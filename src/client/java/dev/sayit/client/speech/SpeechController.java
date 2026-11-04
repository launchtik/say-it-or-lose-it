package dev.sayit.client.speech;

import dev.sayit.SayItMod;
import dev.sayit.config.SayItConfig;

import javax.sound.sampled.TargetDataLine;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the recogniser, the background model loading and one live listening session at a time.
 * Callbacks are delivered on the audio thread; the caller re-posts them to the client thread.
 */
public final class SpeechController {
    public enum State { LOADING, READY, UNAVAILABLE }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SayIt-Speech-Loader");
        t.setDaemon(true);
        return t;
    });
    private final SpeechRecognizer recognizer;
    private volatile State state = State.LOADING;
    private volatile String error = "";
    private volatile String language = "";
    private volatile int downloadPercent = -1;
    private volatile Listening current;

    public SpeechController(SpeechRecognizer recognizer) { this.recognizer = recognizer; }

    public static SpeechRecognizer createRecognizer() {
        String id = SayItConfig.get().recognizer;
        if (!"vosk".equalsIgnoreCase(id)) SayItMod.LOG.warn("Unknown recognizer '{}', using vosk", id);
        return new VoskSpeechRecognizer();
    }

    public State state() { return state; }
    public String error() { return error; }
    public String language() { return language; }
    /** 0-100 while a model is downloading, else -1. */
    public int downloadPercent() { return downloadPercent; }

    /** Loads the model and probes the microphone off-thread; {@code done} runs on the worker thread. */
    public void prepare(String lang, Runnable done) {
        language = lang;
        state = State.LOADING;
        worker.execute(() -> {
            try {
                Microphone.probe();
                if (recognizer instanceof VoskSpeechRecognizer vosk) {
                    try {
                        vosk.ensureModel(lang, p -> downloadPercent = p);
                    } finally {
                        downloadPercent = -1;
                    }
                }
                recognizer.prepare(lang);
                error = "";
                state = State.READY;
            } catch (SpeechUnavailableException e) {
                error = e.getMessage();
                state = State.UNAVAILABLE;
                SayItMod.LOG.warn("Speech unavailable: {}", e.getMessage());
            } catch (Throwable e) {
                error = "Speech error: " + e;
                state = State.UNAVAILABLE;
                SayItMod.LOG.error("Speech init failed", e);
            }
            done.run();
        });
    }

    /** Re-check after a failure (mic plugged in, model downloaded). */
    public void retry(Runnable done) {
        if (state == State.UNAVAILABLE && !language.isEmpty()) prepare(language, done);
    }

    public interface Events {
        void onUtterance(String text, float confidence, String openText);
        /** Mic broke or delivered pure digital silence: the challenge must be cancelled, not failed. */
        void onAbort(String reason);
    }

    /**
     * Opens the mic and starts recognising. Throws (nothing started) if the mic or model is unavailable.
     * @return the form the server should score against ("" = the target itself)
     */
    public String listen(String target, List<String> distractors, boolean openCheck, Events events) throws SpeechUnavailableException {
        stop();
        if (state != State.READY) throw new SpeechUnavailableException(error.isEmpty() ? "Speech is still loading" : error);
        SpeechRecognizer.RecognitionSession session =
                recognizer.startSession(language, target, distractors, openCheck, events::onUtterance);
        TargetDataLine line;
        try {
            line = Microphone.open();
        } catch (SpeechUnavailableException e) {
            session.close();
            throw e;
        }
        Listening l = new Listening(line, session, events);
        current = l;
        l.thread.start();
        return session.scoreAs();
    }

    /** Push-to-talk state, set every client tick. */
    public void setPushToTalk(boolean held) { Listening l = current; if (l != null) l.pttHeld = held; }

    /** Ask the session to finish the current utterance (timer reached zero). */
    public void finishNow() { Listening l = current; if (l != null) l.flushRequested = true; }

    /** Microphone level 0..1 while listening, else 0. */
    public float level() { Listening l = current; return l == null ? 0 : l.level; }

    public boolean isListening() { return current != null; }

    /** True while audio is actually being fed to the recogniser (always in automatic mode). */
    public boolean isGateOpen() { Listening l = current; return l != null && l.gateOpen; }

    public void stop() {
        Listening l = current;
        current = null;
        if (l != null) l.running = false;
    }

    public void shutdown() {
        stop();
        worker.shutdownNow();
        recognizer.close();
    }

    private static final class Listening implements Runnable {
        final TargetDataLine line;
        final SpeechRecognizer.RecognitionSession session;
        final Events events;
        final Thread thread;
        volatile boolean running = true;
        volatile boolean pttHeld;
        volatile boolean flushRequested;
        volatile boolean gateOpen;
        volatile float level;

        Listening(TargetDataLine line, SpeechRecognizer.RecognitionSession session, Events events) {
            this.line = line;
            this.session = session;
            this.events = events;
            this.thread = new Thread(this, "SayIt-Mic");
            this.thread.setDaemon(true);
        }

        @Override
        public void run() {
            byte[] buf = new byte[3200]; // 100 ms
            boolean ptt = SayItConfig.get().listenMode == SayItConfig.ListenMode.PUSH_TO_TALK;
            boolean wasOpen = false;
            boolean sawSignal = false;
            long started = System.currentTimeMillis();
            try {
                while (running) {
                    int n = line.read(buf, 0, buf.length);
                    if (!running) break;
                    if (n <= 0) { abort("Microphone stopped delivering audio"); break; }
                    int peak = 0;
                    double sum = 0;
                    for (int i = 0; i + 1 < n; i += 2) {
                        int s = (short) ((buf[i] & 0xff) | (buf[i + 1] << 8));
                        peak = Math.max(peak, Math.abs(s));
                        sum += (double) s * s;
                    }
                    double rms = Math.sqrt(sum / Math.max(1, n / 2)) / 32768.0;
                    level = (float) Math.min(1, rms * 6);
                    if (peak > 0) sawSignal = true;
                    if (!sawSignal && System.currentTimeMillis() - started > 2500) {
                        // macOS/Windows hand out zeroes when microphone permission is denied.
                        abort("Microphone is silent - check the OS microphone permission");
                        break;
                    }
                    boolean open = !ptt || pttHeld;
                    gateOpen = open;
                    if (open) session.accept(buf, n);
                    if ((wasOpen && !open) || flushRequested) { session.flush(); flushRequested = false; }
                    wasOpen = open;
                }
            } catch (Throwable e) {
                if (running) abort("Microphone error: " + e.getClass().getSimpleName());
            } finally {
                try { line.stop(); line.close(); } catch (Throwable ignored) {}
                try { session.close(); } catch (Throwable ignored) {}
                level = 0;
            }
        }

        private void abort(String reason) {
            running = false;
            events.onAbort(reason);
        }
    }

}

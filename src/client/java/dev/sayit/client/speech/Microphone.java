package dev.sayit.client.speech;

import dev.sayit.config.SayItConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.Locale;

/** 16 kHz mono 16-bit capture via javax.sound. */
public final class Microphone {
    public static final AudioFormat FORMAT = new AudioFormat(VoskSpeechRecognizer.SAMPLE_RATE, 16, 1, true, false);

    private Microphone() {}

    /** Opens (and starts) the configured or default microphone. */
    public static TargetDataLine open() throws SpeechUnavailableException {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            String wanted = SayItConfig.get().microphone.trim().toLowerCase(Locale.ROOT);
            TargetDataLine line = null;
            if (!wanted.isEmpty()) {
                for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                    if (!mi.getName().toLowerCase(Locale.ROOT).contains(wanted)) continue;
                    Mixer m = AudioSystem.getMixer(mi);
                    if (m.isLineSupported(info)) { line = (TargetDataLine) m.getLine(info); break; }
                }
            }
            if (line == null) {
                if (!AudioSystem.isLineSupported(info)) throw new SpeechUnavailableException("No microphone detected");
                line = (TargetDataLine) AudioSystem.getLine(info);
            }
            line.open(FORMAT, 6400);
            line.start();
            return line;
        } catch (SpeechUnavailableException e) {
            throw e;
        } catch (Throwable e) {
            throw new SpeechUnavailableException("No microphone detected (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /** Opens and closes the mic once (also triggers the OS permission prompt at join time). */
    public static void probe() throws SpeechUnavailableException {
        TargetDataLine l = open();
        l.stop();
        l.close();
    }
}

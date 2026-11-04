package dev.sayit.client.speech;

/** A user-facing reason speech can't run ("No microphone detected"). Items are never deleted then. */
public class SpeechUnavailableException extends Exception {
    public SpeechUnavailableException(String message) { super(message); }
    public SpeechUnavailableException(String message, Throwable cause) { super(message, cause); }
}

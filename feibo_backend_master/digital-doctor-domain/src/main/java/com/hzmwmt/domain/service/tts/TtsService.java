package com.hzmwmt.domain.service.tts;

/** Converts a plain-text answer segment into playable audio. */
public interface TtsService {

    byte[] synthesize(String text);

    default byte[] synthesize(String text, TtsCancellation cancellation) {
        cancellation.check();
        byte[] audio = synthesize(text);
        cancellation.check();
        return audio;
    }
}

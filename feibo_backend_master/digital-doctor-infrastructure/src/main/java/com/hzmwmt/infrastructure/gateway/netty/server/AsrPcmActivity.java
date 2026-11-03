package com.hzmwmt.infrastructure.gateway.netty.server;

/**
 * Server-side PCM16/16 kHz activity check. This is an energy gate, not a claim
 * that background noise can be distinguished from speech. Removing DC and
 * requiring 60 ms of activity prevents silence and isolated clicks from
 * opening a paid recognition stream. No transcript phrases are filtered.
 */
final class AsrPcmActivity {
    static final double RMS_THRESHOLD = 0.005;
    private static final int WINDOW_SAMPLES = 320;
    private static final int REQUIRED_ACTIVE_WINDOWS = 3;
    private int samples;
    private double sum;
    private double sumSquares;
    private int activeWindows;

    boolean observe(byte[] pcm) {
        boolean confirmedActivity = false;
        for (int offset = 0; offset + 1 < pcm.length; offset += 2) {
            short value = (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
            double sample = value / 32768.0;
            sum += sample;
            sumSquares += sample * sample;
            if (++samples == WINDOW_SAMPLES) {
                double mean = sum / samples;
                double variance = Math.max(0, sumSquares / samples - mean * mean);
                activeWindows = variance >= RMS_THRESHOLD * RMS_THRESHOLD
                        ? Math.min(REQUIRED_ACTIVE_WINDOWS, activeWindows + 1) : 0;
                confirmedActivity |= activeWindows >= REQUIRED_ACTIVE_WINDOWS;
                samples = 0;
                sum = 0;
                sumSquares = 0;
            }
        }
        return confirmedActivity;
    }
}

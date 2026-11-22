package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;

public final class AudioPreferences {

    private static final String PREFS_NAME = "SpeakSmartAudioPrefs";
    private static final String KEY_SPEECH_RATE = "speech_rate";
    private static final String KEY_VOICE_PITCH = "voice_pitch";

    public static final float RATE_SLOW = 0.65f;
    public static final float RATE_NORMAL = 0.85f;
    public static final float RATE_FAST = 1.05f;

    public static final float PITCH_DEEP = 0.85f;
    public static final float PITCH_NATURAL = 1.0f;
    public static final float PITCH_BRIGHT = 1.10f;

    private AudioPreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );
    }

    public static float getSpeechRate(Context context) {
        float rate = prefs(context).getFloat(KEY_SPEECH_RATE, RATE_NORMAL);
        if (rate != RATE_SLOW && rate != RATE_NORMAL && rate != RATE_FAST) {
            return RATE_NORMAL;
        }
        return rate;
    }

    public static void setSpeechRate(Context context, float rate) {
        prefs(context).edit().putFloat(KEY_SPEECH_RATE, rate).apply();
    }

    public static float getVoicePitch(Context context) {
        float pitch = prefs(context).getFloat(KEY_VOICE_PITCH, PITCH_NATURAL);
        if (pitch == 0.95f) {
            return PITCH_NATURAL;
        }
        if (pitch != PITCH_DEEP && pitch != PITCH_NATURAL && pitch != PITCH_BRIGHT) {
            return PITCH_NATURAL;
        }
        return pitch;
    }

    public static void setVoicePitch(Context context, float pitch) {
        prefs(context).edit().putFloat(KEY_VOICE_PITCH, pitch).apply();
    }

    public static void applyToTextToSpeech(Context context, TextToSpeech tts) {
        if (context == null || tts == null) {
            return;
        }
        tts.setSpeechRate(getSpeechRate(context));
        tts.setPitch(getVoicePitch(context));
    }

    public static String getSummary(Context context) {
        float rate = getSpeechRate(context);
        String rateLabel = rate == RATE_SLOW ? "Slow" : (rate == RATE_FAST ? "Fast" : "Normal");

        float pitch = getVoicePitch(context);
        String pitchLabel = pitch == PITCH_DEEP ? "Deeper" : (pitch == PITCH_BRIGHT ? "Bright" : "Natural");

        return rateLabel + " speed • " + pitchLabel + " pitch";
    }
}
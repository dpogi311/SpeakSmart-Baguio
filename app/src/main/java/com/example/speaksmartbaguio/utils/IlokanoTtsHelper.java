package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configures Android Text-to-Speech for Ilokano pronunciation and applies
 * phonetic normalization before speaking so Filipino/English TTS engines
 * articulate Ilokano syllables clearly instead of reading them as slang or loanwords.
 */
public final class IlokanoTtsHelper {

    private static final Locale[] ILOKANO_CANDIDATE_LOCALES = {
            new Locale("ilo", "PH"),
            new Locale("fil", "PH"),
            new Locale("tl", "PH"),
            new Locale("en", "PH"),
            Locale.getDefault()
    };

    private static final Pattern PARENTHESES_PATTERN = Pattern.compile("\\([^)]*\\)");
    private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\[[^\\]]*\\]");
    private static final Pattern VARIANT_SEPARATORS = Pattern.compile("[/;]");
    private static final Pattern WORD_INTERNAL_HYPHEN = Pattern.compile("(?<=\\p{L})-(?=\\p{L})");

    private IlokanoTtsHelper() {
    }

    /**
     * Selects the best available Ilokano or Filipino voice/locale on the device,
     * preferring high-quality offline voices so pronunciation stays crisp and natural.
     */
    public static boolean configureForIlokano(@Nullable TextToSpeech tts) {
        if (tts == null) {
            return false;
        }

        Voice bestVoice = findBestIlokanoOrFilipinoVoice(tts);
        if (bestVoice != null) {
            try {
                int res = tts.setVoice(bestVoice);
                if (res != TextToSpeech.LANG_MISSING_DATA
                        && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                    return true;
                }
            } catch (Exception ignored) {
                // Fall back to locale selection below.
            }
        }

        for (Locale locale : ILOKANO_CANDIDATE_LOCALES) {
            if (locale == null) {
                continue;
            }
            try {
                int availability = tts.isLanguageAvailable(locale);
                if (availability != TextToSpeech.LANG_MISSING_DATA
                        && availability != TextToSpeech.LANG_NOT_SUPPORTED) {
                    int result = tts.setLanguage(locale);
                    if (result != TextToSpeech.LANG_MISSING_DATA
                            && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    @Nullable
    private static Voice findBestIlokanoOrFilipinoVoice(@NonNull TextToSpeech tts) {
        Set<Voice> voices;
        try {
            voices = tts.getVoices();
        } catch (Exception e) {
            return null;
        }
        if (voices == null || voices.isEmpty()) {
            return null;
        }

        Voice bestVoice = null;
        int bestScore = Integer.MIN_VALUE;

        for (Voice voice : voices) {
            if (voice == null || voice.getLocale() == null) {
                continue;
            }
            String lang = voice.getLocale().getLanguage();
            int langPriority;
            if ("ilo".equalsIgnoreCase(lang)) {
                langPriority = 3000;
            } else if ("fil".equalsIgnoreCase(lang)) {
                langPriority = 2000;
            } else if ("tl".equalsIgnoreCase(lang)) {
                langPriority = 1000;
            } else {
                continue;
            }

            Set<String> features = voice.getFeatures();
            if (features != null && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                continue;
            }

            int score = langPriority + voice.getQuality();
            if (!voice.isNetworkConnectionRequired()) {
                score += 500;
            }
            if (score > bestScore) {
                bestScore = score;
                bestVoice = voice;
            }
        }

        return bestVoice;
    }

    /**
     * Respells raw Ilokano text for clearer synthetic speech without changing displayed UI text.
     */
    @NonNull
    public static String normalizeIlokanoForSpeech(@Nullable String rawText) {
        if (rawText == null) {
            return "";
        }

        String text = rawText.trim();
        if (text.isEmpty()) {
            return "";
        }

        // Remove editorial notes like "(formal)" or "[archaic]".
        text = PARENTHESES_PATTERN.matcher(text).replaceAll(" ");
        text = BRACKETS_PATTERN.matcher(text).replaceAll(" ");

        // Pause cleanly between slash- or semicolon-separated variants.
        text = VARIANT_SEPARATORS.matcher(text).replaceAll(", ");

        // Break word-internal glottal hyphens (ag-adal, saan-a) into crisp pauses.
        text = WORD_INTERNAL_HYPHEN.matcher(text).replaceAll(" ");

        // Separate double-vowel glottal breaks (e.g., saan -> sa an, rabii -> rabi i).
        text = separateDoubleVowels(text);

        // Respelling for Ilokano words commonly misread by fil-PH / en TTS as English loanwords.
        text = replaceWordIgnoreCase(text, "sadiay", "sa dyay");
        text = replaceWordIgnoreCase(text, "idiay", "i dyay");
        text = replaceWordIgnoreCase(text, "diay", "dyay");
        text = replaceWordIgnoreCase(text, "naimbag", "na imbag");
        text = replaceWordIgnoreCase(text, "agyamanak", "ag yamanak");
        text = replaceWordIgnoreCase(text, "biag", "bi yag");
        text = replaceWordIgnoreCase(text, "ania", "an ya");
        text = replaceWordIgnoreCase(text, "pay", "paay");
        text = replaceWordIgnoreCase(text, "man", "maan");
        text = replaceWordIgnoreCase(text, "no", "nu");
        text = replaceWordIgnoreCase(text, "ti", "tii");
        text = replaceWordIgnoreCase(text, "toy", "tooy");

        return text.replaceAll("\\s+", " ").trim();
    }

    private static String separateDoubleVowels(String text) {
        text = text.replaceAll("(?i)aa", "a a");
        text = text.replaceAll("(?i)ii", "i i");
        text = text.replaceAll("(?i)oo", "o o");
        text = text.replaceAll("(?i)uu", "u u");
        return text;
    }

    private static String replaceWordIgnoreCase(String text, String targetWord, String replacement) {
        Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(targetWord) + "\\b");
        return p.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    /**
     * Configures the engine for Ilokano, applies saved rate/pitch preferences,
     * normalizes pronunciation, and speaks immediately.
     */
    public static int speakIlokano(
            @NonNull Context context,
            @Nullable TextToSpeech tts,
            @Nullable String text,
            @NonNull String utteranceId
    ) {
        if (tts == null) {
            return TextToSpeech.ERROR;
        }
        String normalized = normalizeIlokanoForSpeech(text);
        if (normalized.isEmpty()) {
            return TextToSpeech.ERROR;
        }
        try {
            tts.stop();
        } catch (Exception ignored) {
        }
        configureForIlokano(tts);
        AudioPreferences.applyToTextToSpeech(context, tts);
        return tts.speak(normalized, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }
}

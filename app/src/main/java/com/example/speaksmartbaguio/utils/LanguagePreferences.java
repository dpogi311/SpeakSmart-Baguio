package com.example.speaksmartbaguio.utils;

import android.content.Context;

public final class LanguagePreferences {

    private static final String DICTIONARY_PREFS = "DictionaryPrefs";
    private static final String PHRASE_PREFS = "PhrasePrefs";
    private static final String TRANSLATOR_PREFS = "TranslatorPrefs";

    private static final String KEY_LANGUAGE = "selectedLanguage";
    private static final String KEY_TRANSLATION_MODE = "translationMode";

    public static final String LANG_ENGLISH = "English";
    public static final String LANG_TAGALOG = "Tagalog";

    public static final String MODE_ENGLISH_TO_ILOKANO = "English  ⇄  Ilokano";
    public static final String MODE_ILOKANO_TO_ENGLISH = "Ilokano  ⇄  English";
    public static final String MODE_TAGALOG_TO_ILOKANO = "Tagalog  ⇄  Ilokano";
    public static final String MODE_ILOKANO_TO_TAGALOG = "Ilokano  ⇄  Tagalog";

    private LanguagePreferences() {
    }

    public static String getDefaultTargetLanguage(Context context) {
        String lang = context.getApplicationContext()
                .getSharedPreferences(DICTIONARY_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANG_ENGLISH);
        return LANG_TAGALOG.equals(lang) ? LANG_TAGALOG : LANG_ENGLISH;
    }

    public static void setDefaultTargetLanguage(Context context, String language) {
        String safeLang = LANG_TAGALOG.equals(language) ? LANG_TAGALOG : LANG_ENGLISH;
        Context app = context.getApplicationContext();

        app.getSharedPreferences(DICTIONARY_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, safeLang).apply();

        app.getSharedPreferences(PHRASE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, safeLang).apply();
    }

    public static String getDefaultTranslationMode(Context context) {
        String mode = context.getApplicationContext()
                .getSharedPreferences(TRANSLATOR_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TRANSLATION_MODE, MODE_ENGLISH_TO_ILOKANO);

        if (MODE_ILOKANO_TO_ENGLISH.equals(mode)
                || MODE_TAGALOG_TO_ILOKANO.equals(mode)
                || MODE_ILOKANO_TO_TAGALOG.equals(mode)) {
            return mode;
        }
        return MODE_ENGLISH_TO_ILOKANO;
    }

    public static void setDefaultTranslationMode(Context context, String mode) {
        String safeMode = MODE_ENGLISH_TO_ILOKANO;
        if (MODE_ILOKANO_TO_ENGLISH.equals(mode)
                || MODE_TAGALOG_TO_ILOKANO.equals(mode)
                || MODE_ILOKANO_TO_TAGALOG.equals(mode)) {
            safeMode = mode;
        }

        context.getApplicationContext()
                .getSharedPreferences(TRANSLATOR_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TRANSLATION_MODE, safeMode).apply();
    }

    public static String getSummary(Context context) {
        return getDefaultTargetLanguage(context) + " • " + getDefaultTranslationMode(context);
    }
}
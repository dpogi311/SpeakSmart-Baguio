
package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {

    private static final String PREFS_NAME =
            "speak_smart_preferences";

    private static final String KEY_THEME_MODE =
            "theme_mode";

    public static final String LIGHT = "light";
    public static final String DARK = "dark";
    public static final String SYSTEM = "system";

    private ThemeManager() {
        // Prevent creating instances.
    }

    // ==========================================
    // GET SAVED THEME
    // ==========================================

    public static String getSavedTheme(Context context) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        String savedTheme = preferences.getString(
                KEY_THEME_MODE,
                SYSTEM
        );

        if (LIGHT.equals(savedTheme)
                || DARK.equals(savedTheme)
                || SYSTEM.equals(savedTheme)) {

            return savedTheme;
        }

        return SYSTEM;
    }

    // ==========================================
    // APPLY SAVED THEME
    // ==========================================

    public static void applySavedTheme(Context context) {

        applyThemeMode(getSavedTheme(context));
    }

    // ==========================================
    // SAVE AND APPLY SELECTED THEME
    // ==========================================

    public static void setTheme(
            Context context,
            String mode
    ) {

        if (mode == null
                || (!LIGHT.equals(mode)
                && !DARK.equals(mode)
                && !SYSTEM.equals(mode))) {

            mode = SYSTEM;
        }

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        preferences.edit()
                .putString(KEY_THEME_MODE, mode)
                .apply();

        applyThemeMode(mode);
    }

    // ==========================================
    // APPLY THEME THROUGH APPCOMPAT
    // ==========================================

    private static void applyThemeMode(String mode) {

        switch (mode) {

            case LIGHT:

                AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_NO
                );

                break;

            case DARK:

                AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_YES
                );

                break;

            case SYSTEM:
            default:

                AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                );

                break;
        }
    }
}

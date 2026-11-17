
package com.example.speaksmartbaguio;

import android.app.Application;

import com.example.speaksmartbaguio.utils.ThemeManager;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class SpeakSmartApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply the saved Light, Dark, or System theme.
        ThemeManager.applySavedTheme(this);

        // Initialize Firebase App Check.
        FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();

        if (BuildConfig.DEBUG) {
            try {
                // Load the debug provider only in debug builds.
                Class<?> factoryClass = Class.forName(
                        "com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory"
                );

                Object factory = factoryClass
                        .getMethod("getInstance")
                        .invoke(null);

                appCheck.installAppCheckProviderFactory(
                        (AppCheckProviderFactory) factory
                );

            } catch (Exception e) {
                throw new IllegalStateException(
                        "Unable to initialize Firebase App Check for debugging.",
                        e
                );
            }

        } else {
            // Production builds use Google Play Integrity.
            appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
            );
        }
    }
}

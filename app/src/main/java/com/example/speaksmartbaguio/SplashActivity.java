
package com.example.speaksmartbaguio;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SplashActivity extends AppCompatActivity {

    // ==========================================
    // CONFIGURATION
    // ==========================================

    private static final String GROUP_NAME = "BIFROST";
    private static final long SPLASH_DELAY = 7500L;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean navigationStarted = false;


    private final Runnable openHome = () -> {

        if (isFinishing() || isDestroyed() || navigationStarted) {
            return;
        }

        navigationStarted = true;

        // Check whether the user has completed the guide.
        SharedPreferences preferences = getSharedPreferences(
                "FirstTimeGuidePrefs",
                MODE_PRIVATE
        );

        boolean guideCompleted = preferences.getBoolean(
                "guide_completed",
                false
        );

        // First-time users see the guide.
        // Returning users go directly to HomeActivity.
        Intent intent;

        if (guideCompleted) {
            intent = new Intent(
                    SplashActivity.this,
                    HomeActivity.class
            );
        } else {
            intent = new Intent(
                    SplashActivity.this,
                    FirstTimeGuideActivity.class
            );
        }

        startActivity(intent);

        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        finish();
    };


    // ==========================================
    // ACTIVITY CREATION
    // ==========================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        // ==========================================
        // SYSTEM BAR COLORS — LIGHT / DARK SUPPORT
        // ==========================================

        int statusBarColor = ContextCompat.getColor(
                this,
                R.color.status_bar_color
        );

        int navigationBarColor = ContextCompat.getColor(
                this,
                R.color.navigation_bar_color
        );

        getWindow().setStatusBarColor(statusBarColor);
        getWindow().setNavigationBarColor(navigationBarColor);

        int nightModeFlags = getResources()
                .getConfiguration()
                .uiMode & Configuration.UI_MODE_NIGHT_MASK;

        boolean isDarkMode = nightModeFlags
                == Configuration.UI_MODE_NIGHT_YES;

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(
                        getWindow(),
                        getWindow().getDecorView()
                );

        controller.setAppearanceLightStatusBars(!isDarkMode);
        controller.setAppearanceLightNavigationBars(!isDarkMode);

        // ==========================================
        // CONNECT VIEWS
        // ==========================================

        ImageView splashLogo =
                findViewById(R.id.splashLogo);

        TextView textAppTitle =
                findViewById(R.id.textAppTitle);

        TextView textDeveloperCredit =
                findViewById(R.id.textDeveloperCredit);

        LinearLayout letterContainer =
                findViewById(R.id.letterContainer);

        TextView textGroupSub =
                findViewById(R.id.textGroupSub);

        TextView textBifrostSub =
                findViewById(R.id.textBifrostSub);

        BifrostLoaderView bifrostLoader =
                findViewById(R.id.bifrostLoader);

        // ==========================================
        // THEME COLORS
        // ==========================================

        int tealStart = ContextCompat.getColor(
                this,
                R.color.primary_teal
        );

        int tealEnd = ContextCompat.getColor(
                this,
                R.color.accent_teal
        );

        // ==========================================
        // SCENE 1: PREMIUM APP TITLE
        // ==========================================

        setupAppTitle(textAppTitle, tealEnd);

        // ==========================================
        // SCENE 2: LOGO REVEAL
        // ==========================================

        splashLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(900)
                .start();

        // ==========================================
        // SCENE 3: APP TITLE REVEAL
        // ==========================================

        textAppTitle.setTranslationY(16f);

        textAppTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(450)
                .setDuration(850)
                .start();

        // ==========================================
        // SCENE 4: DEVELOPER CREDIT
        // ==========================================

        textDeveloperCredit.setTranslationY(12f);

        textDeveloperCredit.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(850)
                .setDuration(800)
                .start();

        // ==========================================
        // SCENE 5: CREATE BIFROST TITLE
        // ==========================================

        createBifrostLetters(
                letterContainer,
                tealStart,
                tealEnd
        );

        // ==========================================
        // SCENE 6: START LETTER ANIMATION
        // ==========================================

        handler.postDelayed(() -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            animateLetters(
                    letterContainer,
                    0,
                    () -> revealTagline(
                            textGroupSub,
                            textBifrostSub,
                            bifrostLoader
                    )
            );

        }, 1600);

        // ==========================================
        // SCENE 7: OPEN HOME
        // ==========================================

        handler.postDelayed(
                openHome,
                SPLASH_DELAY
        );
    }

    // ==========================================
    // PREMIUM APP TITLE
    // ==========================================

    private void setupAppTitle(
            TextView title,
            int tealColor
    ) {

        SpannableString appTitle =
                new SpannableString("SPEAK SMART\nBAGUIO");

        int baguioStart =
                appTitle.toString().indexOf("BAGUIO");

        appTitle.setSpan(
                new RelativeSizeSpan(0.68f),
                baguioStart,
                appTitle.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        appTitle.setSpan(
                new ForegroundColorSpan(tealColor),
                baguioStart,
                appTitle.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        title.setText(appTitle);

        title.setTypeface(
                Typeface.create(
                        "sans-serif-black",
                        Typeface.BOLD
                )
        );

        title.setShadowLayer(
                16f,
                0f,
                3f,
                Color.argb(65, 0, 210, 200)
        );
    }

    // ==========================================
    // CREATE GRADIENT BIFROST LETTERS
    // ==========================================

    private void createBifrostLetters(
            LinearLayout container,
            int tealStart,
            int tealEnd
    ) {

        container.removeAllViews();

        for (char c : GROUP_NAME.toCharArray()) {

            TextView letter = new TextView(this);

            letter.setText(String.valueOf(c));

            letter.setTextSize(39f);

            letter.setTypeface(
                    Typeface.create(
                            "sans-serif-black",
                            Typeface.BOLD
                    )
            );

            letter.setAlpha(0f);

            letter.setTextColor(Color.WHITE);

            Shader shader = new LinearGradient(
                    0f,
                    0f,
                    0f,
                    55f,
                    new int[]{
                            tealStart,
                            Color.WHITE,
                            tealEnd
                    },
                    null,
                    Shader.TileMode.CLAMP
            );

            letter.getPaint().setShader(shader);

            letter.setShadowLayer(
                    15f,
                    0f,
                    2f,
                    Color.argb(
                            100,
                            0,
                            210,
                            200
                    )
            );

            letter.setPadding(
                    5,
                    0,
                    5,
                    0
            );

            container.addView(letter);
        }
    }

    // ==========================================
    // LETTER-BY-LETTER ANIMATION
    // ==========================================

    private void animateLetters(
            LinearLayout container,
            int index,
            Runnable onComplete
    ) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (index >= container.getChildCount()) {
            onComplete.run();
            return;
        }

        TextView letter =
                (TextView) container.getChildAt(index);

        letter.setTranslationY(12f);

        letter.setScaleX(0.85f);
        letter.setScaleY(0.85f);

        letter.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .withEndAction(() ->
                        animateLetters(
                                container,
                                index + 1,
                                onComplete
                        )
                )
                .start();
    }

    // ==========================================
    // MAIN TAGLINE REVEAL
    // ==========================================

    private void revealTagline(
            TextView tagline,
            TextView secondaryTagline,
            BifrostLoaderView loader
    ) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        tagline.setTranslationY(8f);

        tagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(750)
                .withEndAction(() ->
                        revealSecondaryTagline(
                                secondaryTagline,
                                loader
                        )
                )
                .start();
    }

    // ==========================================
    // SECONDARY TAGLINE REVEAL
    // ==========================================

    private void revealSecondaryTagline(
            TextView tagline,
            BifrostLoaderView loader
    ) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        tagline.setTranslationY(6f);

        tagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(650)
                .withEndAction(() ->
                        revealSpinner(loader)
                )
                .start();
    }

    // ==========================================
    // BIFROST LOADING ANIMATION
    // ==========================================

    private void revealSpinner(
            BifrostLoaderView loader
    ) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        loader.animate()
                .alpha(1f)
                .setDuration(600)
                .start();
    }

    // ==========================================
    // CLEANUP
    // ==========================================

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }
}

package com.example.speaksmartbaguio;

import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
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

        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.animate()
                    .alpha(0f)
                    .scaleX(1.03f)
                    .scaleY(1.03f)
                    .setDuration(280L)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> launchDestination(intent))
                    .start();
        } else {
            launchDestination(intent);
        }
    };


    private void launchDestination(Intent intent) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        startActivity(intent);

        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        finish();
    }


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
        // SCENE 2: LOGO REVEAL + GENTLE PULSE
        // ==========================================

        splashLogo.setAlpha(0f);
        splashLogo.setScaleX(0.82f);
        splashLogo.setScaleY(0.82f);
        splashLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(850L)
                .setInterpolator(new OvershootInterpolator(1.25f))
                .withEndAction(() -> startLogoPulse(splashLogo))
                .start();

        // ==========================================
        // SCENE 3: APP TITLE REVEAL
        // ==========================================

        textAppTitle.setTranslationY(20f);

        textAppTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(420L)
                .setDuration(800L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // ==========================================
        // SCENE 4: DEVELOPER CREDIT
        // ==========================================

        textDeveloperCredit.setTranslationY(14f);

        textDeveloperCredit.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(800L)
                .setDuration(750L)
                .setInterpolator(new DecelerateInterpolator())
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
        // SCENE 6: START CASCADING LETTER ANIMATION
        // ==========================================

        handler.postDelayed(() -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            animateLettersWave(
                    letterContainer,
                    () -> revealTagline(
                            textGroupSub,
                            textBifrostSub,
                            bifrostLoader
                    )
            );

        }, 1450L);

        // ==========================================
        // SCENE 7: OPEN HOME
        // ==========================================

        handler.postDelayed(
                openHome,
                SPLASH_DELAY
        );
    }

    // ==========================================
    // SUBTLE LOGO BREATHING PULSE
    // ==========================================

    private void startLogoPulse(ImageView splashLogo) {

        if (isFinishing() || isDestroyed() || navigationStarted) {
            return;
        }

        splashLogo.animate()
                .scaleX(1.035f)
                .scaleY(1.035f)
                .setDuration(1400L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (isFinishing() || isDestroyed() || navigationStarted) {
                        return;
                    }
                    splashLogo.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(1400L)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> startLogoPulse(splashLogo))
                            .start();
                })
                .start();
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

        Typeface displayFont = ResourcesCompat.getFont(
                this,
                R.font.font_display
        );

        if (displayFont != null) {
            title.setTypeface(displayFont, Typeface.BOLD);
        }

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

        Typeface displayFont = ResourcesCompat.getFont(
                this,
                R.font.font_display
        );

        for (char c : GROUP_NAME.toCharArray()) {

            TextView letter = new TextView(this);

            letter.setText(String.valueOf(c));

            letter.setTextSize(39f);

            if (displayFont != null) {
                letter.setTypeface(displayFont, Typeface.BOLD);
            }

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
    // CASCADING WAVE LETTER ANIMATION
    // ==========================================

    private void animateLettersWave(
            LinearLayout container,
            Runnable onComplete
    ) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        int count = container.getChildCount();
        if (count == 0) {
            onComplete.run();
            return;
        }

        for (int i = 0; i < count; i++) {
            final boolean isLast = (i == count - 1);
            TextView letter = (TextView) container.getChildAt(i);

            letter.setTranslationY(16f);
            letter.setScaleX(0.8f);
            letter.setScaleY(0.8f);

            letter.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(i * 75L)
                    .setDuration(280L)
                    .setInterpolator(new OvershootInterpolator(1.5f))
                    .withEndAction(isLast ? onComplete : null)
                    .start();
        }
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

        tagline.setTranslationY(10f);

        tagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(650L)
                .setInterpolator(new DecelerateInterpolator())
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

        tagline.setTranslationY(8f);

        tagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(550L)
                .setInterpolator(new DecelerateInterpolator())
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

        loader.setScaleX(0.92f);
        loader.setScaleY(0.92f);
        loader.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(550L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }
}
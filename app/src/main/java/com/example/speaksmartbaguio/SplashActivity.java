package com.example.speaksmartbaguio;

import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SplashActivity extends AppCompatActivity {

    // ==========================================
    // TEAM IDENTITY
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

        Intent intent = new Intent(
                SplashActivity.this,
                HomeActivity.class
        );

        startActivity(intent);

        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        // ==========================================
        // SYSTEM BAR COLORS
        // ==========================================

        getWindow().setStatusBarColor(
                ContextCompat.getColor(
                        this,
                        R.color.app_background
                )
        );

        getWindow().setNavigationBarColor(
                ContextCompat.getColor(
                        this,
                        R.color.app_background
                )
        );

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
        // TEAL GRADIENT COLORS
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
        // SCENE 1: LOGO REVEAL
        // ==========================================

        splashLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(900)
                .start();

        // ==========================================
        // SCENE 2: APP TITLE REVEAL
        // SPEAK SMART BAGUIO
        // ==========================================

        textAppTitle.setTranslationY(12f);

        textAppTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(450)
                .setDuration(850)
                .start();

        // ==========================================
        // SCENE 3: DEVELOPER CREDIT
        // ==========================================

        textDeveloperCredit.setTranslationY(12f);

        textDeveloperCredit.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(850)
                .setDuration(800)
                .start();

        // ==========================================
        // SCENE 4: ANIMATED BIFROST TITLE
        // ==========================================

        for (char c : GROUP_NAME.toCharArray()) {

            TextView letter = new TextView(this);

            letter.setText(String.valueOf(c));
            letter.setTextSize(36f);
            letter.setTypeface(null, Typeface.BOLD);
            letter.setAlpha(0f);

            letter.setTextColor(
                    ContextCompat.getColor(
                            this,
                            R.color.text_light
                    )
            );

            Shader shader = new LinearGradient(
                    0f,
                    0f,
                    0f,
                    48f,
                    tealStart,
                    tealEnd,
                    Shader.TileMode.CLAMP
            );

            letter.getPaint().setShader(shader);

            letter.setPadding(2, 0, 2, 0);

            letterContainer.addView(letter);
        }

        // ==========================================
        // START TITLE ANIMATION
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
        // SCENE 8: OPEN HOME
        // ==========================================

        handler.postDelayed(openHome, SPLASH_DELAY);
    }

    // ==============================================
    // LETTER-BY-LETTER TITLE ANIMATION
    // ==============================================

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

        letter.setTranslationY(10f);

        letter.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(150)
                .withEndAction(() ->
                        animateLetters(
                                container,
                                index + 1,
                                onComplete
                        )
                )
                .start();
    }

    // ==============================================
    // SCENE 5: MAIN TAGLINE REVEAL
    // LEARN • UNDERSTAND • CONNECT
    // ==============================================

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

    // ==============================================
    // SCENE 6: SECONDARY TAGLINE REVEAL
    // BRIDGING LANGUAGES & CONNECTING CULTURES
    // ==============================================

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
                .withEndAction(() -> revealSpinner(loader))
                .start();
    }

    // ==============================================
    // SCENE 7: BIFROST BRIDGE LOADING ANIMATION
    // ==============================================

    private void revealSpinner(BifrostLoaderView loader) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        loader.animate()
                .alpha(1f)
                .setDuration(600)
                .start();
    }

    // ==============================================
    // CLEANUP
    // ==============================================

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }
}

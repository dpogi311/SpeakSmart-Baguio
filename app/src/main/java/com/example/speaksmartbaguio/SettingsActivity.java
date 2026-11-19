package com.example.speaksmartbaguio;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.SettingsMenuHelper;
import com.example.speaksmartbaguio.utils.ThemeManager;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvAppearanceValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvAppearanceValue = findViewById(R.id.tvAppearanceValue);

        View cardAppearance = findViewById(R.id.cardSettingAppearance);
        View cardAccessibility = findViewById(R.id.cardSettingAccessibility);
        View cardAudio = findViewById(R.id.cardSettingAudio);
        View cardLanguage = findViewById(R.id.cardSettingLanguage);
        View cardGuide = findViewById(R.id.cardSettingGuide);
        View cardAbout = findViewById(R.id.cardSettingAbout);

        cardAppearance.setOnClickListener(v -> {
            animateTap(v, null);
            SettingsMenuHelper.showAppearanceDialog(this);
        });

        cardAccessibility.setOnClickListener(v -> {
            animateTap(v, null);
            SettingsMenuHelper.showAccessibilityDialog(
                    this,
                    getWindow().getDecorView(),
                    null
            );
        });

        cardAudio.setOnClickListener(v -> {
            animateTap(v, null);
            SettingsMenuHelper.showAudioSettingsDialog(this, null);
        });

        cardLanguage.setOnClickListener(v -> {
            animateTap(v, null);
            SettingsMenuHelper.showLanguagePreferencesDialog(this, null);
        });

        cardGuide.setOnClickListener(v ->
                animateTap(v, () -> {
                    Intent intent = new Intent(this, FirstTimeGuideActivity.class);
                    intent.putExtra(FirstTimeGuideActivity.EXTRA_FROM_SETTINGS, true);
                    startActivity(intent);
                    overridePendingTransition(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                    );
                })
        );

        cardAbout.setOnClickListener(v -> {
            animateTap(v, null);
            SettingsMenuHelper.showAboutDialog(this);
        });

        updateAppearanceLabel();
        animateEntrance(
                cardAppearance,
                cardAccessibility,
                cardAudio,
                cardLanguage,
                cardGuide,
                cardAbout
        );

        AccessibilityPreferences.applyToViewTree(
                this,
                getWindow().getDecorView()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAppearanceLabel();
        AccessibilityPreferences.applyToViewTree(
                this,
                getWindow().getDecorView()
        );
    }

    private void updateAppearanceLabel() {
        if (tvAppearanceValue == null) {
            return;
        }
        String saved = ThemeManager.getSavedTheme(this);
        if (ThemeManager.LIGHT.equals(saved)) {
            tvAppearanceValue.setText("Light Mode");
        } else if (ThemeManager.DARK.equals(saved)) {
            tvAppearanceValue.setText("Dark Mode");
        } else {
            tvAppearanceValue.setText("System Default");
        }
    }

    private void animateEntrance(View... cards) {
        float offset = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                18f,
                getResources().getDisplayMetrics()
        );
        for (int i = 0; i < cards.length; i++) {
            View card = cards[i];
            if (card == null) {
                continue;
            }
            card.setAlpha(0f);
            card.setTranslationY(offset);
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(40L + (i * 55L))
                    .setDuration(260L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void animateTap(View view, Runnable action) {
        if (view == null) {
            if (action != null) {
                action.run();
            }
            return;
        }
        view.animate().cancel();
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(70L)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100L)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();
                    if (action != null) {
                        action.run();
                    }
                })
                .start();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
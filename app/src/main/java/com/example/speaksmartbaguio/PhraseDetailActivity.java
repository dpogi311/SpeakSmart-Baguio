package com.example.speaksmartbaguio;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.AudioPreferences;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

public class PhraseDetailActivity extends AppCompatActivity {

    private TextView tvIlokano;
    private TextView tvPartOfSpeech;
    private TextView tvCategory;
    private TextView tvEnglish;
    private TextView tvTagalog;
    private TextView tvSentence;
    private TextView tvEnglishSentence;

    private ImageButton btnSpeakPhrase;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean isFinishingActivity = false;

    private String ilokanoPhrase = "";

    private static final String TAG = "PHRASE_DETAIL_TTS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phrase_detail);

        // Connect layout views.
        tvIlokano = findViewById(R.id.tvIlokano);
        tvPartOfSpeech = findViewById(R.id.tvPartOfSpeech);
        tvCategory = findViewById(R.id.tvCategory);
        tvEnglish = findViewById(R.id.tvEnglish);
        tvTagalog = findViewById(R.id.tvTagalog);
        tvSentence = findViewById(R.id.tvSentence);
        tvEnglishSentence = findViewById(R.id.tvEnglishSentence);

        btnSpeakPhrase = findViewById(R.id.btnSpeakPhrase);

        // Back button.
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            if (isFinishingActivity) {
                return;
            }
            isFinishingActivity = true;
            animateButtonTap(v, this::finish);
        });

        // Retrieve phrase information.
        ilokanoPhrase = getIntent().getStringExtra("ilokano");

        String partOfSpeech = getIntent().getStringExtra("pos");
        String category = getIntent().getStringExtra("category");
        String english = getIntent().getStringExtra("english");
        String tagalog = getIntent().getStringExtra("tagalog");
        String sentence = getIntent().getStringExtra("sentence");
        String englishSentence =
                getIntent().getStringExtra("englishSentence");

        // Display the main Ilokano phrase.
        tvIlokano.setText(
                displayOrFallback(
                        ilokanoPhrase,
                        "Phrase unavailable."
                )
        );

        // Optional part of speech.
        if (isEmpty(partOfSpeech)) {
            tvPartOfSpeech.setVisibility(View.GONE);
        } else {
            tvPartOfSpeech.setVisibility(View.VISIBLE);
            tvPartOfSpeech.setText(partOfSpeech.trim());
        }

        // Optional category.
        if (isEmpty(category)) {
            tvCategory.setVisibility(View.GONE);
        } else {
            tvCategory.setVisibility(View.VISIBLE);
            tvCategory.setText(category.trim());
        }

        // English translation.
        tvEnglish.setText(
                displayOrFallback(
                        english,
                        "No English translation available."
                )
        );

        // Tagalog translation.
        tvTagalog.setText(
                displayOrFallback(
                        tagalog,
                        "Walang available na salin sa Tagalog."
                )
        );

        // Ilokano example sentence.
        tvSentence.setText(
                displayOrFallback(
                        sentence,
                        "No Ilokano example sentence available."
                )
        );

        // English example translation.
        tvEnglishSentence.setText(
                displayOrFallback(
                        englishSentence,
                        "No English example translation available."
                )
        );

        // Initialize Android Text-to-Speech.
        setupTextToSpeech();

        // Speaker button.
        btnSpeakPhrase.setOnClickListener(v ->
                animateButtonTap(v, this::speakIlokanoPhrase)
        );

        // Apply accessibility settings.
        AccessibilityPreferences.applyToViewTree(
                this,
                getWindow().getDecorView()
        );

        animateEntrance();
    }

    private void animateEntrance() {
        View contentView = findViewById(android.R.id.content);
        if (contentView == null) {
            return;
        }
        float offset = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16f,
                getResources().getDisplayMetrics()
        );
        contentView.setAlpha(0f);
        contentView.setTranslationY(offset);
        contentView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateButtonTap(View view, Runnable action) {
        if (view == null) {
            if (action != null) {
                action.run();
            }
            return;
        }

        view.animate().cancel();
        view.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(70L)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100L)
                            .setInterpolator(new OvershootInterpolator(1.3f))
                            .start();

                    if (action != null) {
                        action.run();
                    }
                })
                .start();
    }

    private void showModernMessage(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null || isFinishing() || isDestroyed()) {
            return;
        }

        Snackbar snackbar = Snackbar.make(
                rootView,
                message,
                Snackbar.LENGTH_SHORT
        );
        snackbar.setBackgroundTint(Color.rgb(25, 35, 40));
        snackbar.setTextColor(Color.WHITE);

        TextView snackText = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text
        );
        if (snackText != null) {
            Typeface bodyFont = ResourcesCompat.getFont(
                    this,
                    R.font.font_body
            );
            if (bodyFont != null) {
                snackText.setTypeface(bodyFont);
            }
        }

        snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE);
        snackbar.show();
    }

    // =========================================================
    // TEXT-TO-SPEECH
    // =========================================================

    private void setupTextToSpeech() {

        ttsReady = false;

        tts = new TextToSpeech(
                getApplicationContext(),
                status -> {

                    if (tts == null) {
                        return;
                    }

                    if (status != TextToSpeech.SUCCESS) {
                        ttsReady = false;

                        Log.e(TAG, "Text-to-Speech initialization failed.");
                        return;
                    }

                    AudioPreferences.applyToTextToSpeech(this, tts);

                    ttsReady = setBestAvailableLanguage();

                    if (!ttsReady) {
                        Log.e(TAG, "No supported TTS language found.");
                    }
                }
        );
    }

    private boolean setBestAvailableLanguage() {

        if (tts == null) {
            return false;
        }

        Locale[] preferredLocales = {
                new Locale("fil", "PH"),
                new Locale("tl", "PH"),
                new Locale("en", "PH"),
                Locale.US
        };

        for (Locale locale : preferredLocales) {

            try {
                int availability = tts.isLanguageAvailable(locale);

                if (availability != TextToSpeech.LANG_MISSING_DATA
                        && availability != TextToSpeech.LANG_NOT_SUPPORTED) {

                    int result = tts.setLanguage(locale);

                    if (result != TextToSpeech.LANG_MISSING_DATA
                            && result != TextToSpeech.LANG_NOT_SUPPORTED) {

                        Log.d(TAG, "Selected TTS language: " + locale);
                        return true;
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, "Unable to select TTS language: " + locale, e);
            }
        }

        return false;
    }

    private void speakIlokanoPhrase() {

        // Speak only the main Ilokano phrase.
        // Do not use the English translation or example sentence.
        if (isEmpty(ilokanoPhrase)) {
            showModernMessage("Ilokano phrase is unavailable.");
            return;
        }

        if (tts == null || !ttsReady) {
            showModernMessage("Text-to-Speech is still starting. Please try again.");
            return;
        }

        tts.stop();

        AudioPreferences.applyToTextToSpeech(this, tts);

        int result = tts.speak(
                ilokanoPhrase.trim(),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "phrase_detail_" + System.currentTimeMillis()
        );

        if (result == TextToSpeech.ERROR) {
            showModernMessage("Unable to pronounce this phrase.");
        }
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String displayOrFallback(String value, String fallback) {
        return isEmpty(value) ? fallback : value.trim();
    }

    // =========================================================
    // EXIT TRANSITION & CLEANUP
    // =========================================================

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {

        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.w(TAG, "Error shutting down Text-to-Speech.", e);
            }

            tts = null;
        }

        ttsReady = false;

        super.onDestroy();
    }
}
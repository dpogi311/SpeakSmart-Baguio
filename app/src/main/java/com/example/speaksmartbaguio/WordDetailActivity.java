package com.example.speaksmartbaguio;

import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
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

import java.io.IOException;
import java.util.Locale;

public class WordDetailActivity extends AppCompatActivity {

    private TextView tvIlokano;
    private TextView tvEnglish;
    private TextView tvTagalog;
    private TextView tvPartOfSpeech;
    private TextView tvCategory;
    private TextView tvDefinition;

    private ImageButton btnBack;
    private ImageButton btnSpeakWord;

    private TextToSpeech tts;
    private MediaPlayer mediaPlayer;

    private String ilokanoWord = "";
    private String ttsUrl = "";

    private boolean ttsReady = false;
    private boolean activityDestroyed = false;
    private boolean isFinishingActivity = false;

    private String pendingSpeech = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_detail);

        // Connect the existing layout views.
        tvIlokano = findViewById(R.id.tvIlokano);
        tvEnglish = findViewById(R.id.tvEnglish);
        tvTagalog = findViewById(R.id.tvTagalog);
        tvPartOfSpeech = findViewById(R.id.tvPartOfSpeech);
        tvCategory = findViewById(R.id.tvCategory);
        tvDefinition = findViewById(R.id.tvDefinition);

        btnBack = findViewById(R.id.btnBack);
        btnSpeakWord = findViewById(R.id.btnSpeakWord);

        btnBack.setOnClickListener(v -> {
            if (isFinishingActivity) {
                return;
            }
            isFinishingActivity = true;
            animateButtonTap(v, this::finish);
        });

        // Receive the selected word from DictionaryAdapter.
        ilokanoWord = getIntent().getStringExtra("ilokano");
        String english = getIntent().getStringExtra("english");
        String tagalog = getIntent().getStringExtra("tagalog");
        String pos = getIntent().getStringExtra("pos");
        String category = getIntent().getStringExtra("category");
        String definition = getIntent().getStringExtra("definition");
        ttsUrl = getIntent().getStringExtra("ttsUrl");

        // Prevent null values from appearing in the interface.
        if (ilokanoWord == null) {
            ilokanoWord = "";
        }

        if (english == null || english.trim().isEmpty()) {
            english = "No English translation available.";
        }

        if (tagalog == null || tagalog.trim().isEmpty()) {
            tagalog = "No Tagalog translation available.";
        }

        if (pos == null || pos.trim().isEmpty()) {
            pos = "Part of speech not specified";
        }

        if (category == null || category.trim().isEmpty()) {
            category = "Category not specified";
        }

        if (definition == null || definition.trim().isEmpty()) {
            definition = "No definition available.";
        }

        if (ttsUrl == null) {
            ttsUrl = "";
        }

        tvIlokano.setText(ilokanoWord);
        tvEnglish.setText(english);
        tvTagalog.setText(tagalog);
        tvPartOfSpeech.setText(pos);
        tvCategory.setText(category);
        tvDefinition.setText(definition);

        // Apply saved font-size, high-contrast, and simplified-layout settings.
        AccessibilityPreferences.applyToViewTree(
                this,
                getWindow().getDecorView()
        );

        animateEntrance();

        // Initialize Android Text-to-Speech.
        initializeTextToSpeech();

        // Play the word when the speaker button is tapped.
        btnSpeakWord.setOnClickListener(v ->
                animateButtonTap(v, this::playPronunciation)
        );
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
        if (rootView == null || activityDestroyed || isFinishing()) {
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

    private void initializeTextToSpeech() {
        tts = new TextToSpeech(
                getApplicationContext(),
                status -> {
                    if (activityDestroyed || isFinishing()) {
                        if (tts != null) {
                            tts.shutdown();
                            tts = null;
                        }
                        return;
                    }

                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        ttsReady = true;

                        // Prefer Ilokano if the installed engine supports it.
                        Locale ilokanoLocale = new Locale("ilo", "PH");
                        int result = tts.setLanguage(ilokanoLocale);

                        // Filipino is the next-best available fallback.
                        if (result == TextToSpeech.LANG_MISSING_DATA
                                || result == TextToSpeech.LANG_NOT_SUPPORTED) {

                            Locale filipinoLocale = new Locale("fil", "PH");
                            result = tts.setLanguage(filipinoLocale);
                        }

                        // Keep the engine's default language if neither is supported.
                        if (result == TextToSpeech.LANG_MISSING_DATA
                                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts.setLanguage(Locale.getDefault());
                        }

                        AudioPreferences.applyToTextToSpeech(this, tts);

                        // Speak if the user tapped before TTS finished initializing.
                        if (pendingSpeech != null) {
                            String wordToSpeak = pendingSpeech;
                            pendingSpeech = null;
                            speakWord(wordToSpeak);
                        }
                    }
                }
        );
    }

    private void playPronunciation() {
        if (ilokanoWord == null || ilokanoWord.trim().isEmpty()) {
            showModernMessage("No word is available to pronounce.");
            return;
        }

        // Prefer the recorded audio when a URL exists.
        if (!ttsUrl.trim().isEmpty()) {
            playRemoteAudio(ttsUrl);
        } else {
            speakWord(ilokanoWord);
        }
    }

    private void playRemoteAudio(String audioUrl) {
        releaseMediaPlayer();

        try {
            MediaPlayer player = new MediaPlayer();
            mediaPlayer = player;

            player.setOnPreparedListener(preparedPlayer -> {
                if (activityDestroyed || mediaPlayer != preparedPlayer) {
                    preparedPlayer.release();
                    return;
                }

                preparedPlayer.start();
            });

            player.setOnCompletionListener(completedPlayer -> {
                if (mediaPlayer == completedPlayer) {
                    releaseMediaPlayer();
                }
            });

            player.setOnErrorListener((failedPlayer, what, extra) -> {
                if (mediaPlayer == failedPlayer) {
                    releaseMediaPlayer();

                    if (!activityDestroyed) {
                        // Try Text-to-Speech if the recording cannot play.
                        speakWord(ilokanoWord);
                    }
                }

                return true;
            });

            player.setDataSource(audioUrl);
            player.prepareAsync();

        } catch (IOException | IllegalArgumentException
                 | IllegalStateException e) {

            releaseMediaPlayer();

            // Audio URL failed; try the device's speech engine.
            speakWord(ilokanoWord);
        }
    }

    private void speakWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }

        if (tts == null || !ttsReady) {
            pendingSpeech = word;

            if (tts == null) {
                showModernMessage("Pronunciation is initializing. Please try again.");
            }

            return;
        }

        AudioPreferences.applyToTextToSpeech(this, tts);

        tts.speak(
                word,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "ILOKANO_WORD_PRONUNCIATION"
        );
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
                // The player may already have stopped or failed.
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Stop pronunciation when this screen is no longer visible.
        releaseMediaPlayer();

        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        pendingSpeech = null;

        releaseMediaPlayer();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        ttsReady = false;

        super.onDestroy();
    }
}
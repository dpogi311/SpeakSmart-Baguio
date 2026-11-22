package com.example.speaksmartbaguio;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.LineBackgroundSpan;
import android.text.style.LineHeightSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.speaksmartbaguio.databinding.ActivityHomeBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.repository.PhraseRepository;
import com.example.speaksmartbaguio.utils.NetworkUtil;
import com.example.speaksmartbaguio.utils.SyncManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;

    private PhraseRepository phraseRepository;
    private DictionaryRepository dictionaryRepository;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean hasResumedOnce = false;
    private boolean lastKnownOnline = false;
    private boolean networkCallbackRegistered = false;
    private boolean isNavigating = false;

    // Exact Ilokano search query to pass to the target module.
    private String dailyPhraseSearchTerm = "";

    // Original source of the selected daily content.
    // Possible values: PHRASEBOOK or DICTIONARY.
    private String dailyPhraseDestination = "PHRASEBOOK";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        phraseRepository = new PhraseRepository(getApplicationContext());
        dictionaryRepository = new DictionaryRepository(getApplicationContext());

        setupNavigation();
        setupDailyPhraseCard();
        setupSyncIndicator();
        setupAiAssistantFab();
        animateHomeEntrance();

        loadDailyPhrase();

        lastKnownOnline = NetworkUtil.isOnline(this);
        refreshConnectionStatus();

        if (lastKnownOnline) {
            startSync();
        }
    }


    // =========================================================
    // HOME ANIMATIONS & TRANSITIONS
    // =========================================================

    private void animateHomeEntrance() {

        if (binding == null) {
            return;
        }

        View[] cards = {
                binding.homeDailyPhraseCard,
                binding.cardTranslator,
                binding.cardDictionary,
                binding.cardPhrasebook,
                binding.cardSettings
        };

        float offset = dpToPx(22f);

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
                    .setStartDelay(50L + (i * 65L))
                    .setDuration(320L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }


    private void animateCardTap(View view, Runnable action) {

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
                .setDuration(80L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(110L)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();

                    if (action != null) {
                        action.run();
                    }
                })
                .start();
    }


    // =========================================================
    // HOME NAVIGATION
    // =========================================================

    private void setupNavigation() {

        binding.cardTranslator.setOnClickListener(v -> {
            if (isNavigating) {
                return;
            }
            isNavigating = true;
            animateCardTap(v, () -> openLearningTool("TRANSLATOR"));
        });

        binding.cardDictionary.setOnClickListener(v -> {
            if (isNavigating) {
                return;
            }
            isNavigating = true;
            animateCardTap(v, () -> openLearningTool("DICTIONARY"));
        });

        binding.cardPhrasebook.setOnClickListener(v -> {
            if (isNavigating) {
                return;
            }
            isNavigating = true;
            animateCardTap(v, () -> openLearningTool("PHRASEBOOK"));
        });

        binding.cardSettings.setOnClickListener(v -> {
            if (isNavigating) {
                return;
            }
            isNavigating = true;
            animateCardTap(v, () -> {
                startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
        });
    }


    private void openLearningTool(String fragmentName) {

        Intent intent = new Intent(
                HomeActivity.this,
                MainActivity.class
        );

        intent.putExtra(
                MainActivity.EXTRA_START_FRAGMENT,
                fragmentName
        );

        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }


    private void openDailyPhrase() {

        if (dailyPhraseSearchTerm == null
                || dailyPhraseSearchTerm.trim().isEmpty()) {

            openLearningTool("PHRASEBOOK");
            return;
        }

        Intent intent = new Intent(
                HomeActivity.this,
                MainActivity.class
        );

        // Open the original module that supplied the content.
        intent.putExtra(
                MainActivity.EXTRA_START_FRAGMENT,
                dailyPhraseDestination
        );

        // Pass the exact Ilokano search term.
        if ("DICTIONARY".equals(dailyPhraseDestination)) {

            intent.putExtra(
                    MainActivity.EXTRA_DICTIONARY_SEARCH,
                    dailyPhraseSearchTerm.trim()
            );

        } else {

            intent.putExtra(
                    MainActivity.EXTRA_PHRASEBOOK_SEARCH,
                    dailyPhraseSearchTerm.trim()
            );
        }

        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }


    // =========================================================
    // AI ASSISTANT FLOATING BUTTON
    // =========================================================

    private void setupAiAssistantFab() {
        if (binding == null || binding.fabAiAssistant == null) {
            return;
        }
        binding.fabAiAssistant.setOnClickListener(v -> {
            if (isNavigating) {
                return;
            }
            isNavigating = true;
            animateCardTap(v, () -> {
                Intent intent = new Intent(this, AiAssistantActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
        });
    }


    // =========================================================
    // DAILY PHRASE CARD
    // =========================================================

    private void setupDailyPhraseCard() {

        binding.homeDailyPhraseCard.setOnClickListener(v -> {
            if (isNavigating) {
                return;
            }
            isNavigating = true;
            animateCardTap(v, this::openDailyPhrase);
        });

        binding.homeDailyPhraseCard.setClickable(true);
        binding.homeDailyPhraseCard.setFocusable(true);
    }


    /**
     * Loads saved content from BOTH Phrasebook and Dictionary.
     *
     * Each record keeps its original source.
     * The daily card never guesses its destination.
     */
    private void loadDailyPhrase() {

        if (binding == null
                || phraseRepository == null
                || dictionaryRepository == null) {
            return;
        }

        final List<Phrase> phraseResults = new ArrayList<>();
        final List<Word> wordResults = new ArrayList<>();

        final boolean[] phraseLoaded = {false};
        final boolean[] dictionaryLoaded = {false};

        final Object lock = new Object();


        // Load Phrasebook records.
        phraseRepository.getAllPhrases(result -> {

            List<Phrase> phrases = EntityMapper.toPhraseList(result);

            synchronized (lock) {

                phraseResults.clear();

                if (phrases != null) {
                    phraseResults.addAll(phrases);
                }

                phraseLoaded[0] = true;
            }

            displayWhenBothSourcesAreLoaded(
                    lock,
                    phraseResults,
                    wordResults,
                    phraseLoaded,
                    dictionaryLoaded
            );
        });


        // Load Dictionary records.
        dictionaryRepository.getAllWords(result -> {

            List<Word> words = EntityMapper.toWordList(result);

            synchronized (lock) {

                wordResults.clear();

                if (words != null) {
                    wordResults.addAll(words);
                }

                dictionaryLoaded[0] = true;
            }

            displayWhenBothSourcesAreLoaded(
                    lock,
                    phraseResults,
                    wordResults,
                    phraseLoaded,
                    dictionaryLoaded
            );
        });
    }


    /**
     * Waits until both repositories finish loading.
     * Then safely sends copies of their results to the UI.
     */
    private void displayWhenBothSourcesAreLoaded(
            Object lock,
            List<Phrase> phrases,
            List<Word> words,
            boolean[] phraseLoaded,
            boolean[] dictionaryLoaded
    ) {

        List<Phrase> phraseSnapshot;
        List<Word> wordSnapshot;

        synchronized (lock) {

            if (!phraseLoaded[0] || !dictionaryLoaded[0]) {
                return;
            }

            phraseSnapshot = new ArrayList<>(phrases);
            wordSnapshot = new ArrayList<>(words);
        }

        runOnUiThread(() -> {

            if (binding == null || isFinishing() || isDestroyed()) {
                return;
            }

            displayDailyContent(
                    phraseSnapshot,
                    wordSnapshot
            );
        });
    }


    /**
     * Combines Phrasebook and Dictionary content.
     * Displays styled English and Tagalog pill badges with dividers.
     */
    private void displayDailyContent(
            List<Phrase> phrases,
            List<Word> words
    ) {

        if (binding == null) {
            return;
        }

        List<DailyCardItem> dailyItems = new ArrayList<>();


        // -----------------------------------------------------
        // ADD PHRASEBOOK RECORDS
        // -----------------------------------------------------

        if (phrases != null) {

            for (Phrase phrase : phrases) {

                if (phrase == null) {
                    continue;
                }

                String ilokanoText =
                        getIlokanoDisplayText(phrase);

                String searchTerm =
                        getIlokanoSearchTerm(phrase);

                String englishText =
                        getEnglishDisplayText(phrase);

                String tagalogText =
                        phrase.getTagalogTranslation();

                boolean hasAnyTranslation =
                        (englishText != null && !englishText.trim().isEmpty())
                                || (tagalogText != null && !tagalogText.trim().isEmpty());

                if (!ilokanoText.trim().isEmpty()
                        && !searchTerm.trim().isEmpty()
                        && hasAnyTranslation) {

                    dailyItems.add(
                            new DailyCardItem(
                                    "PHRASEBOOK",
                                    ilokanoText.trim(),
                                    searchTerm.trim(),
                                    englishText == null ? "" : englishText.trim(),
                                    tagalogText == null ? "" : tagalogText.trim()
                            )
                    );
                }
            }
        }


        // -----------------------------------------------------
        // ADD DICTIONARY RECORDS
        // -----------------------------------------------------

        if (words != null) {

            for (Word word : words) {

                if (word == null) {
                    continue;
                }

                String ilokanoText = word.getIlokanoWord();

                String englishText = word.getEnglishTranslation();
                String tagalogText = word.getTagalogTranslation();

                boolean hasAnyTranslation =
                        (englishText != null && !englishText.trim().isEmpty())
                                || (tagalogText != null && !tagalogText.trim().isEmpty());

                if (ilokanoText != null
                        && !ilokanoText.trim().isEmpty()
                        && hasAnyTranslation) {

                    String cleanWord = ilokanoText.trim();

                    dailyItems.add(
                            new DailyCardItem(
                                    "DICTIONARY",
                                    cleanWord,
                                    cleanWord,
                                    englishText == null ? "" : englishText.trim(),
                                    tagalogText == null ? "" : tagalogText.trim()
                            )
                    );
                }
            }
        }


        // -----------------------------------------------------
        // CHECK AVAILABLE CONTENT
        // -----------------------------------------------------

        if (dailyItems.isEmpty()) {
            showEmptyDailyPhrase();
            return;
        }


        // -----------------------------------------------------
        // SELECT DAILY CONTENT
        // -----------------------------------------------------

        Calendar calendar = Calendar.getInstance();

        int year = calendar.get(Calendar.YEAR);
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);

        int dailySeed = year * 1000 + dayOfYear;

        int dailyIndex = Math.floorMod(
                dailySeed,
                dailyItems.size()
        );

        DailyCardItem selectedItem =
                dailyItems.get(dailyIndex);


        // -----------------------------------------------------
        // SAVE THE SELECTED ITEM AND ITS SOURCE
        // -----------------------------------------------------

        dailyPhraseSearchTerm =
                selectedItem.searchTerm;

        dailyPhraseDestination =
                selectedItem.source;


        // -----------------------------------------------------
        // DISPLAY SOURCE LABEL (KEEP ORIGINAL XML PILL PADDING)
        // -----------------------------------------------------

        if ("DICTIONARY".equals(selectedItem.source)) {

            binding.tvDailyPhraseSource.setText(
                    "SOURCE: DICTIONARY"
            );

        } else {

            binding.tvDailyPhraseSource.setText(
                    "SOURCE: PHRASEBOOK"
            );
        }

        binding.tvDailyPhraseSource.setVisibility(View.VISIBLE);


        // -----------------------------------------------------
        // DISPLAY DAILY CONTENT (WITH DIVIDERS & SPACING)
        // -----------------------------------------------------

        binding.tvDailyPhrase.setText(
                selectedItem.ilokanoText
        );

        binding.tvDailyPhraseTranslation.setSingleLine(false);
        binding.tvDailyPhraseTranslation.setMaxLines(10);
        binding.tvDailyPhraseTranslation.setLineSpacing(0f, 1.0f);

        binding.tvDailyPhraseTranslation.setText(
                buildStyledTranslations(
                        selectedItem.englishTranslation,
                        selectedItem.tagalogTranslation
                )
        );

        binding.tvDailyPhrase.setVisibility(View.VISIBLE);
        binding.tvDailyPhraseTranslation.setVisibility(View.VISIBLE);

        // Smoothly fade in refreshed daily content
        fadeInViews(
                binding.tvDailyPhraseSource,
                binding.tvDailyPhrase,
                binding.tvDailyPhraseTranslation
        );
    }


    private void fadeInViews(View... views) {
        for (View view : views) {
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            view.setAlpha(0f);
            view.animate()
                    .alpha(1f)
                    .setDuration(240L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }


    // =========================================================
    // DAILY CONTENT MODEL
    // =========================================================

    private static class DailyCardItem {

        final String source;
        final String ilokanoText;
        final String searchTerm;
        final String englishTranslation;
        final String tagalogTranslation;

        DailyCardItem(
                String source,
                String ilokanoText,
                String searchTerm,
                String englishTranslation,
                String tagalogTranslation
        ) {

            this.source = source;
            this.ilokanoText = ilokanoText;
            this.searchTerm = searchTerm;
            this.englishTranslation = englishTranslation;
            this.tagalogTranslation = tagalogTranslation;
        }
    }


    // =========================================================
    // STYLED TRANSLATION BUILDER (PILL BADGES + DIVIDERS)
    // =========================================================

    private CharSequence buildStyledTranslations(
            String english,
            String tagalog
    ) {

        String cleanEnglish =
                english == null ? "" : english.trim();

        String cleanTagalog =
                tagalog == null ? "" : tagalog.trim();

        Typeface labelFont = ResourcesCompat.getFont(
                this,
                R.font.font_label
        );

        SpannableStringBuilder builder =
                new SpannableStringBuilder();

        boolean isNightMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        int dividerColor = ContextCompat.getColor(this, R.color.divider_color);

        int enBgColor = Color.parseColor(isNightMode ? "#3338BDF8" : "#E0F2FE");
        int enBorderColor = Color.parseColor(isNightMode ? "#8038BDF8" : "#0284C7");
        int enTextColor = Color.parseColor(isNightMode ? "#E0F2FE" : "#0369A1");

        int tlBgColor = Color.parseColor(isNightMode ? "#33FBBF24" : "#FEF3C7");
        int tlBorderColor = Color.parseColor(isNightMode ? "#80FBBF24" : "#D97706");
        int tlTextColor = Color.parseColor(isNightMode ? "#FEF3C7" : "#B45309");

        // Top divider separating the Ilokano title from the translations.
        appendDividerLine(
                builder,
                Math.round(dpToPx(14f)),
                dividerColor
        );

        if (!cleanEnglish.isEmpty()) {
            appendTranslationRow(
                    builder,
                    "EN",
                    labelFont,
                    enBgColor,
                    enBorderColor,
                    enTextColor,
                    cleanEnglish
            );
        }

        if (!cleanTagalog.isEmpty()) {
            if (!cleanEnglish.isEmpty()) {
                // Middle divider separating English and Tagalog rows.
                appendDividerLine(
                        builder,
                        Math.round(dpToPx(18f)),
                        dividerColor
                );
            }

            appendTranslationRow(
                    builder,
                    "TL",
                    labelFont,
                    tlBgColor,
                    tlBorderColor,
                    tlTextColor,
                    cleanTagalog
            );
        }

        return builder;
    }


    /**
     * Appends a dedicated spacer line that draws a horizontal divider
     * centered vertically inside the spacing gap.
     */
    private void appendDividerLine(
            SpannableStringBuilder builder,
            int verticalGapPx,
            int dividerColor
    ) {

        if (builder.length() > 0) {
            builder.append("\n");
        }

        int start = builder.length();
        builder.append("\u00A0");
        int end = builder.length();

        builder.setSpan(
                new HorizontalDividerSpan(
                        verticalGapPx,
                        dpToPx(1f),
                        dividerColor
                ),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        builder.append("\n");
    }


    /**
     * Appends a badge pill ("EN" or "TL") followed by the translation text
     * using the exact same bold font style and color for both languages.
     */
    private void appendTranslationRow(
            SpannableStringBuilder builder,
            String badgeLabel,
            @Nullable Typeface badgeTypeface,
            int badgeBackgroundColor,
            int badgeBorderColor,
            int badgeTextColor,
            String translationText
    ) {

        int badgeStart = builder.length();
        builder.append(badgeLabel);
        int badgeEnd = builder.length();

        RoundedBadgeSpan badgeSpan = new RoundedBadgeSpan(
                badgeTypeface,
                badgeBackgroundColor,
                badgeBorderColor,
                badgeTextColor,
                dpToPx(6f),
                dpToPx(7f),
                dpToPx(2.5f),
                spToPx(10.5f)
        );

        builder.setSpan(
                badgeSpan,
                badgeStart,
                badgeEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        builder.append("   ");

        int textStart = builder.length();
        builder.append(translationText);
        int textEnd = builder.length();

        // Apply the exact same bold style to both English and Tagalog text.
        builder.setSpan(
                new StyleSpan(Typeface.BOLD),
                textStart,
                textEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }


    /**
     * Custom span that creates vertical spacing and draws a full-width
     * subtle horizontal divider line across the card.
     */
    private static class HorizontalDividerSpan
            implements LineBackgroundSpan, LineHeightSpan {

        private final int heightPx;
        private final float strokeWidthPx;
        private final int lineColor;

        HorizontalDividerSpan(
                int heightPx,
                float strokeWidthPx,
                int lineColor
        ) {
            this.heightPx = Math.max(4, heightPx);
            this.strokeWidthPx = strokeWidthPx;
            this.lineColor = lineColor;
        }

        @Override
        public void chooseHeight(
                CharSequence text,
                int start,
                int end,
                int spanstartv,
                int lineHeight,
                Paint.FontMetricsInt fm
        ) {
            if (!(text instanceof Spanned)) {
                return;
            }

            Spanned spanned = (Spanned) text;
            int spanStart = spanned.getSpanStart(this);
            int spanEnd = spanned.getSpanEnd(this);

            if (spanStart >= start && spanEnd <= end) {
                int half = heightPx / 2;
                fm.ascent = -half;
                fm.top = -half;
                fm.descent = heightPx - half;
                fm.bottom = heightPx - half;
            }
        }

        @Override
        public void drawBackground(
                @NonNull Canvas canvas,
                @NonNull Paint paint,
                int left,
                int right,
                int top,
                int baseline,
                int bottom,
                @NonNull CharSequence text,
                int start,
                int end,
                int lineNumber
        ) {
            if (!(text instanceof Spanned)) {
                return;
            }

            Spanned spanned = (Spanned) text;
            int spanStart = spanned.getSpanStart(this);
            int spanEnd = spanned.getSpanEnd(this);

            if (spanStart >= start && spanEnd <= end) {
                float centerY = (top + bottom) / 2f;

                Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                linePaint.setColor(lineColor);
                linePaint.setStyle(Paint.Style.STROKE);
                linePaint.setStrokeWidth(strokeWidthPx);

                canvas.drawLine(left, centerY, right, centerY, linePaint);
            }
        }
    }


    /**
     * Custom ReplacementSpan that draws a modern rounded pill badge
     * (with fill + subtle border) for "EN" and "TL" tags.
     */
    private static class RoundedBadgeSpan extends ReplacementSpan {

        @Nullable
        private final Typeface badgeTypeface;
        private final int backgroundColor;
        private final int borderColor;
        private final int textColor;
        private final float cornerRadius;
        private final float horizontalPadding;
        private final float verticalPadding;
        private final float badgeTextSize;

        RoundedBadgeSpan(
                @Nullable Typeface badgeTypeface,
                int backgroundColor,
                int borderColor,
                int textColor,
                float cornerRadius,
                float horizontalPadding,
                float verticalPadding,
                float badgeTextSize
        ) {
            this.badgeTypeface = badgeTypeface;
            this.backgroundColor = backgroundColor;
            this.borderColor = borderColor;
            this.textColor = textColor;
            this.cornerRadius = cornerRadius;
            this.horizontalPadding = horizontalPadding;
            this.verticalPadding = verticalPadding;
            this.badgeTextSize = badgeTextSize;
        }

        private Paint createBadgePaint(Paint src) {
            Paint badgePaint = new Paint(src);
            badgePaint.setTextSize(badgeTextSize);
            badgePaint.setTypeface(
                    badgeTypeface != null
                            ? Typeface.create(badgeTypeface, Typeface.BOLD)
                            : Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            );
            badgePaint.setAntiAlias(true);
            badgePaint.setLetterSpacing(0.06f);
            return badgePaint;
        }

        @Override
        public int getSize(
                @NonNull Paint paint,
                CharSequence text,
                int start,
                int end,
                @Nullable Paint.FontMetricsInt fm
        ) {
            Paint badgePaint = createBadgePaint(paint);
            float textWidth = badgePaint.measureText(text, start, end);
            return Math.round(textWidth + horizontalPadding * 2f);
        }

        @Override
        public void draw(
                @NonNull Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int y,
                int bottom,
                @NonNull Paint paint
        ) {
            Paint badgePaint = createBadgePaint(paint);
            Paint.FontMetrics badgeFm = badgePaint.getFontMetrics();
            Paint.FontMetrics hostFm = paint.getFontMetrics();

            float textWidth = badgePaint.measureText(text, start, end);
            float badgeWidth = textWidth + horizontalPadding * 2f;
            float badgeTextHeight = badgeFm.descent - badgeFm.ascent;
            float badgeHeight = badgeTextHeight + verticalPadding * 2f;

            // Vertically center the pill relative to the main translation text line.
            float lineCenterY = y + (hostFm.ascent + hostFm.descent) / 2f;
            float rectTop = lineCenterY - badgeHeight / 2f;
            float rectBottom = lineCenterY + badgeHeight / 2f;

            RectF rect = new RectF(
                    x,
                    rectTop,
                    x + badgeWidth,
                    rectBottom
            );

            // Draw pill background fill.
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(backgroundColor);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);

            // Draw subtle pill border.
            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2f);
            strokePaint.setColor(borderColor);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint);

            // Draw centered badge label ("EN" or "TL").
            badgePaint.setColor(textColor);
            float textX = x + horizontalPadding;
            float textBaselineY =
                    lineCenterY - (badgeFm.ascent + badgeFm.descent) / 2f;

            canvas.drawText(
                    text,
                    start,
                    end,
                    textX,
                    textBaselineY,
                    badgePaint
            );
        }
    }


    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }


    private float spToPx(float sp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                getResources().getDisplayMetrics()
        );
    }


    // =========================================================
    // PHRASEBOOK & TRANSLATION HELPERS
    // =========================================================

    private String getIlokanoDisplayText(Phrase phrase) {

        if (phrase == null) {
            return "";
        }

        String word = phrase.getIlokanoWord();

        if (word != null && !word.trim().isEmpty()) {
            return word.trim();
        }

        String sentence = phrase.getIlokanoSentence();

        return sentence == null ? "" : sentence.trim();
    }


    /**
     * Prefers ilokanoWord as the search query so Phrasebook search
     * (both online API and local DB) always finds the exact entry.
     */
    private String getIlokanoSearchTerm(Phrase phrase) {

        if (phrase == null) {
            return "";
        }

        String word = phrase.getIlokanoWord();

        if (word != null && !word.trim().isEmpty()) {
            return word.trim();
        }

        String sentence = phrase.getIlokanoSentence();

        return sentence == null ? "" : sentence.trim();
    }


    private String getEnglishDisplayText(Phrase phrase) {

        if (phrase == null) {
            return "";
        }

        String translation = phrase.getEnglishTranslation();

        if (translation != null && !translation.trim().isEmpty()) {
            return translation.trim();
        }

        String sentence = phrase.getEnglishSentence();

        return sentence == null ? "" : sentence.trim();
    }


    // =========================================================
    // EMPTY DAILY CARD
    // =========================================================

    private void showEmptyDailyPhrase() {

        if (binding == null) {
            return;
        }

        dailyPhraseSearchTerm = "";
        dailyPhraseDestination = "PHRASEBOOK";

        // Hide source when no daily item is available.
        binding.tvDailyPhraseSource.setVisibility(View.GONE);

        binding.tvDailyPhrase.setText(
                "Daily phrase is not available yet."
        );

        binding.tvDailyPhraseTranslation.setLineSpacing(0f, 1.0f);
        binding.tvDailyPhraseTranslation.setText(
                "Open Phrasebook or Dictionary to load learning content."
        );

        binding.tvDailyPhrase.setVisibility(View.VISIBLE);
        binding.tvDailyPhraseTranslation.setVisibility(View.VISIBLE);

        fadeInViews(binding.tvDailyPhrase, binding.tvDailyPhraseTranslation);
    }


    // =========================================================
    // OFFLINE / ONLINE / SYNC INDICATOR
    // =========================================================

    private void setupSyncIndicator() {

        binding.btnRetrySync.setOnClickListener(v -> animateCardTap(v, () -> {

            if (!NetworkUtil.isOnline(this)) {

                refreshConnectionStatus();

                binding.tvSyncStatus.setText(
                        "No internet connection. Please reconnect and retry."
                );

                return;
            }

            startSync();
        }));

        updateLastSyncText();

        binding.progressSync.setVisibility(View.GONE);
    }


    private void startSync() {

        if (binding == null) {
            return;
        }

        if (!NetworkUtil.isOnline(this)) {

            refreshConnectionStatus();

            binding.tvSyncStatus.setText(
                    "Offline mode. Your saved data remains available."
            );

            return;
        }

        binding.btnRetrySync.setEnabled(false);
        binding.progressSync.setVisibility(View.VISIBLE);

        binding.tvSyncStatus.setText(
                "Checking for updates..."
        );

        SyncManager.sync(this, new SyncManager.SyncListener() {

            @Override
            public void onSyncStarted() {

                if (binding == null || isFinishing() || isDestroyed()) {
                    return;
                }

                binding.progressSync.setVisibility(View.VISIBLE);
                binding.btnRetrySync.setEnabled(false);

                binding.tvSyncStatus.setText("Syncing...");
            }


            @Override
            public void onSyncFinished(
                    boolean success,
                    String message
            ) {

                if (binding == null || isFinishing() || isDestroyed()) {
                    return;
                }

                binding.progressSync.setVisibility(View.GONE);
                binding.btnRetrySync.setEnabled(true);

                binding.tvSyncStatus.setText(message);

                updateLastSyncText();
                refreshConnectionStatus();

                // Refresh the daily card after synchronization.
                loadDailyPhrase();
            }
        });
    }


    private void refreshConnectionStatus() {

        if (binding == null) {
            return;
        }

        boolean online = NetworkUtil.isOnline(this);

        binding.tvConnectionStatus.setText(
                online ? "Online" : "Offline mode"
        );

        if (!online && !SyncManager.isSyncing()) {

            binding.tvSyncStatus.setText(
                    "Your saved data is available offline."
            );
        }
    }


    private void updateLastSyncText() {

        if (binding == null) {
            return;
        }

        long lastSync =
                SyncManager.getLastSuccessfulSync(this);

        if (lastSync <= 0) {

            binding.tvLastSync.setText(
                    "Last successful sync: Not yet synced"
            );

            return;
        }

        SimpleDateFormat formatter = new SimpleDateFormat(
                "MMM d, yyyy h:mm a",
                Locale.getDefault()
        );

        binding.tvLastSync.setText(
                "Last successful sync: "
                        + formatter.format(new Date(lastSync))
        );
    }


    // =========================================================
    // MONITOR CONNECTIVITY CHANGES
    // =========================================================

    @Override
    protected void onStart() {

        super.onStart();

        connectivityManager =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (connectivityManager == null
                || networkCallbackRegistered) {
            return;
        }

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET
                        )
                        .build();

        networkCallback =
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(Network network) {
                        runOnUiThread(
                                () -> handleConnectivityChange()
                        );
                    }


                    @Override
                    public void onLost(Network network) {
                        runOnUiThread(
                                () -> handleConnectivityChange()
                        );
                    }


                    @Override
                    public void onCapabilitiesChanged(
                            Network network,
                            NetworkCapabilities capabilities
                    ) {

                        runOnUiThread(
                                () -> handleConnectivityChange()
                        );
                    }
                };

        try {

            connectivityManager.registerNetworkCallback(
                    request,
                    networkCallback
            );

            networkCallbackRegistered = true;

        } catch (Exception ignored) {
            // Initial status checks still work if registration fails.
        }
    }


    private void handleConnectivityChange() {

        if (binding == null) {
            return;
        }

        boolean online = NetworkUtil.isOnline(this);
        boolean wasOnline = lastKnownOnline;

        lastKnownOnline = online;

        refreshConnectionStatus();

        if (online && !wasOnline) {
            startSync();
        }
    }


    @Override
    protected void onStop() {

        if (connectivityManager != null
                && networkCallback != null
                && networkCallbackRegistered) {

            try {

                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );

            } catch (Exception ignored) {
                // Callback may already have been unregistered.
            }
        }

        networkCallbackRegistered = false;
        networkCallback = null;

        super.onStop();
    }


    // =========================================================
    // ACTIVITY LIFECYCLE
    // =========================================================

    @Override
    protected void onResume() {

        super.onResume();
        isNavigating = false;

        if (hasResumedOnce) {

            loadDailyPhrase();

            refreshConnectionStatus();
            updateLastSyncText();

        } else {

            hasResumedOnce = true;
        }
    }


    @Override
    protected void onDestroy() {

        if (phraseRepository != null) {

            phraseRepository.close();
            phraseRepository = null;
        }

        // DictionaryRepository is not explicitly closed here
        // because its close() method has not been confirmed.

        dictionaryRepository = null;

        binding = null;

        super.onDestroy();
    }
}
package com.example.speaksmartbaguio;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.ThemeManager;

public class FirstTimeGuideActivity extends AppCompatActivity {
    public static final String EXTRA_FROM_SETTINGS = "from_settings";
    private static final String PREFS_NAME = "FirstTimeGuidePrefs";
    private static final String KEY_COMPLETED = "guide_completed";

    // Keep true while designing and testing the guide.
    // Change to false when the app is ready for release.
    private static final boolean ALWAYS_SHOW_GUIDE_FOR_TESTING = false;

    // Five guide pages
    private final int[] PAGE_LAYOUTS = {
            R.layout.item_guide_page_1,  // Page 1 - Welcome
            R.layout.item_guide_page_2,  // Page 2 - Translator
            R.layout.item_guide_page_3,  // Page 3 - Dictionary
            R.layout.item_guide_page_4,  // Page 4 - Phrasebook
            R.layout.item_guide_page_5   // Page 5 - Pronunciation
    };

    private int colorBrightTeal;
    private int colorMuted;

    private HorizontalScrollView pageScroller;
    private LinearLayout pagesContainer;
    private LinearLayout dotsContainer;

    private Button btnBack;
    private Button btnNext;
    private TextView btnSkip;

    private int currentPage = 0;
    private int pageWidth = 0;
    private boolean isFinishingGuide = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_time_guide);

        colorBrightTeal = ContextCompat.getColor(this, R.color.cyan_primary);
        colorMuted = ContextCompat.getColor(this, R.color.text_disabled);

        initializeViews();
        setupPages();
        setupNavigation();
        setupBackNavigation();
    }

    private void initializeViews() {
        pageScroller = findViewById(R.id.guidePageScroller);
        pagesContainer = findViewById(R.id.guidePagesContainer);
        dotsContainer = findViewById(R.id.guideDotsContainer);

        btnBack = findViewById(R.id.btnGuideBack);
        btnNext = findViewById(R.id.btnGuideNext);
        btnSkip = findViewById(R.id.btnSkipGuide);

        // Disable manual touch/swipe scrolling.
        pageScroller.setOnTouchListener((view, event) -> true);
    }

    private void setupPages() {

        pageScroller.post(() -> {

            pageWidth = pageScroller.getWidth();

            if (pageWidth <= 0) {
                return;
            }

            pagesContainer.removeAllViews();

            for (int layout : PAGE_LAYOUTS) {

                View page = getLayoutInflater().inflate(
                        layout,
                        pagesContainer,
                        false
                );
                if (layout == R.layout.item_guide_page_1) {
                    TextView title = page.findViewById(R.id.tvGuideTitle);
                    String accentHex = String.format("#%06X", (0xFFFFFF & colorBrightTeal));

                    title.setText(Html.fromHtml(
                            "Welcome to <font color='" + accentHex + "'>Speak Smart Baguio</font>",
                            Html.FROM_HTML_MODE_LEGACY
                    ));
                }
                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                pageWidth,
                                LinearLayout.LayoutParams.MATCH_PARENT
                        );

                pagesContainer.addView(page, params);
            }

            setupPageIndicators();

            pageScroller.scrollTo(0, 0);

            updateNavigation();
            AccessibilityPreferences.applyToViewTree(this, getWindow().getDecorView());
            animatePageReveal(0);
        });
    }

    private void setupPageIndicators() {

        dotsContainer.removeAllViews();

        for (int i = 0; i < PAGE_LAYOUTS.length; i++) {

            TextView dot = new TextView(this);

            dot.setText("●");
            dot.setTextSize(13);
            dot.setPadding(7, 5, 7, 5);
            dot.setTextColor(colorMuted);

            dotsContainer.addView(dot);
        }

        updatePageIndicators();
    }

    private void updatePageIndicators() {

        for (int i = 0; i < dotsContainer.getChildCount(); i++) {

            TextView dot = (TextView) dotsContainer.getChildAt(i);
            boolean isActive = (i == currentPage);

            dot.setTextColor(isActive ? colorBrightTeal : colorMuted);
            dot.setTextSize(isActive ? 15 : 12);

            dot.animate().cancel();
            dot.animate()
                    .scaleX(isActive ? 1.25f : 0.9f)
                    .scaleY(isActive ? 1.25f : 0.9f)
                    .alpha(isActive ? 1f : 0.55f)
                    .setDuration(200L)
                    .setInterpolator(
                            isActive
                                    ? new OvershootInterpolator(1.6f)
                                    : new DecelerateInterpolator()
                    )
                    .start();
        }
    }

    private void setupNavigation() {

        btnBack.setOnClickListener(v -> animateButtonTap(v, () -> {
            if (currentPage > 0) {
                goToPage(currentPage - 1);
            }
        }));

        btnNext.setOnClickListener(v -> animateButtonTap(v, () -> {
            if (currentPage < PAGE_LAYOUTS.length - 1) {
                goToPage(currentPage + 1);
            } else {
                finishGuide();
            }
        }));

        btnSkip.setOnClickListener(v -> animateButtonTap(v, this::finishGuide));

        updateNavigation();
    }

    private void updateNavigation() {

        boolean isFirstPage = currentPage == 0;
        boolean isLastPage = currentPage == PAGE_LAYOUTS.length - 1;

        if (isFirstPage) {
            if (btnBack.getVisibility() == View.VISIBLE) {
                btnBack.animate()
                        .alpha(0f)
                        .setDuration(150L)
                        .withEndAction(() -> btnBack.setVisibility(View.INVISIBLE))
                        .start();
            } else {
                btnBack.setVisibility(View.INVISIBLE);
                btnBack.setAlpha(0f);
            }
        } else {
            if (btnBack.getVisibility() != View.VISIBLE) {
                btnBack.setAlpha(0f);
                btnBack.setVisibility(View.VISIBLE);
            }
            btnBack.animate()
                    .alpha(1f)
                    .setDuration(200L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (isLastPage) {
            btnNext.setText("Get Started");
        } else {
            btnNext.setText("Next ›");
        }

        updatePageIndicators();
    }

    private void goToPage(int page) {

        if (page < 0 || page >= PAGE_LAYOUTS.length) {
            return;
        }

        currentPage = page;

        if (pageWidth > 0) {
            int targetX = page * pageWidth;
            ObjectAnimator scrollAnimator = ObjectAnimator.ofInt(
                    pageScroller,
                    "scrollX",
                    targetX
            );
            scrollAnimator.setDuration(320L);
            scrollAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
            scrollAnimator.start();
        }

        animatePageReveal(page);
        updateNavigation();
    }

    private void animatePageReveal(int pageIndex) {

        if (pagesContainer == null
                || pageIndex < 0
                || pageIndex >= pagesContainer.getChildCount()) {
            return;
        }

        View pageView = pagesContainer.getChildAt(pageIndex);
        if (pageView == null) {
            return;
        }

        pageView.animate().cancel();
        pageView.setAlpha(0.55f);
        pageView.setScaleX(0.96f);
        pageView.setScaleY(0.96f);
        pageView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280L)
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
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(75L)
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

    private void setupBackNavigation() {

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (currentPage > 0) {
                            goToPage(currentPage - 1);
                        } else {
                            finishGuide();
                        }
                    }
                }
        );
    }

    private void finishGuide() {

        if (isFinishingGuide) {
            return;
        }
        isFinishingGuide = true;

        boolean openedFromSettings = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_FROM_SETTINGS, false);

        if (!openedFromSettings) {
            SharedPreferences preferences =
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            preferences.edit()
                    .putBoolean(
                            KEY_COMPLETED,
                            !ALWAYS_SHOW_GUIDE_FOR_TESTING
                    )
                    .apply();
        }

        View rootView = findViewById(android.R.id.content);

        if (openedFromSettings) {
            if (rootView != null) {
                rootView.animate()
                        .alpha(0f)
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(200L)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            finish();
                            overridePendingTransition(
                                    R.anim.slide_in_left,
                                    R.anim.slide_out_right
                            );
                        })
                        .start();
            } else {
                finish();
                overridePendingTransition(
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                );
            }
            return;
        }

        Intent intent = new Intent(
                FirstTimeGuideActivity.this,
                HomeActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        if (rootView != null) {
            rootView.animate()
                    .alpha(0f)
                    .scaleX(1.02f)
                    .scaleY(1.02f)
                    .setDuration(220L)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        startActivity(intent);
                        overridePendingTransition(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        );
                        finish();
                    })
                    .start();
        } else {
            startActivity(intent);
            overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
            finish();
        }
    }
}

package com.example.speaksmartbaguio;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.Html;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class FirstTimeGuideActivity extends AppCompatActivity {

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

    private final int COLOR_BRIGHT_TEAL = Color.rgb(8, 212, 194);
    private final int COLOR_MUTED = Color.rgb(105, 130, 134);

    private HorizontalScrollView pageScroller;
    private LinearLayout pagesContainer;
    private LinearLayout dotsContainer;

    private Button btnBack;
    private Button btnNext;
    private TextView btnSkip;

    private int currentPage = 0;
    private int pageWidth = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_time_guide);

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

                    title.setText(Html.fromHtml(
                            "Welcome to <font color='#08D4C2'>Speak Smart Baguio</font>",
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
        });

    }

    private void setupPageIndicators() {

        dotsContainer.removeAllViews();

        for (int i = 0; i < PAGE_LAYOUTS.length; i++) {


            TextView dot = new TextView(this);

            dot.setText("●");
            dot.setTextSize(13);
            dot.setPadding(7, 5, 7, 5);
            dot.setTextColor(COLOR_MUTED);


            dotsContainer.addView(dot);
        }

        updatePageIndicators();
    }

    private void updatePageIndicators() {

        for (int i = 0; i < dotsContainer.getChildCount(); i++) {

            TextView dot = (TextView) dotsContainer.getChildAt(i);

            if (i == currentPage) {
                dot.setTextColor(COLOR_BRIGHT_TEAL);
                dot.setTextSize(16);
            } else {
                dot.setTextColor(COLOR_MUTED);
                dot.setTextSize(12);
            }
        }
    }

    private void setupNavigation() {

        btnBack.setOnClickListener(v -> {

            if (currentPage > 0) {
                goToPage(currentPage - 1);
            }
        });

        btnNext.setOnClickListener(v -> {

            if (currentPage < PAGE_LAYOUTS.length - 1) {
                goToPage(currentPage + 1);
            } else {
                finishGuide();
            }
        });

        btnSkip.setOnClickListener(v -> finishGuide());

        updateNavigation();
    }

    private void updateNavigation() {

        boolean isFirstPage = currentPage == 0;
        boolean isLastPage = currentPage == PAGE_LAYOUTS.length - 1;

        btnBack.setVisibility(
                isFirstPage ? View.INVISIBLE : View.VISIBLE
        );

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

            pageScroller.smoothScrollTo(
                    page * pageWidth,
                    0
            );
        }

        updateNavigation();
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

        SharedPreferences preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        preferences.edit()
                .putBoolean(
                        KEY_COMPLETED,
                        !ALWAYS_SHOW_GUIDE_FOR_TESTING
                )
                .apply();

        Intent intent = new Intent(
                FirstTimeGuideActivity.this,
                HomeActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}

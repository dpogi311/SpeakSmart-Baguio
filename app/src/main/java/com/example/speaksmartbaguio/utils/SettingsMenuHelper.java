package com.example.speaksmartbaguio.utils;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.speaksmartbaguio.FirstTimeGuideActivity;
import com.example.speaksmartbaguio.MainActivity;
import com.example.speaksmartbaguio.R;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

public final class SettingsMenuHelper {

    private SettingsMenuHelper() {
        // Prevent creating instances of this helper.
    }

    // =========================================================
    // MENU ITEM IDS
    // =========================================================

    private static final int MENU_APPEARANCE = 1005;
    private static final int MENU_ACCESSIBILITY = 1001;
    private static final int MENU_AUDIO = 1002;
    private static final int MENU_LANGUAGE = 1003;
    private static final int MENU_ABOUT = 1004;
    private static final int MENU_GUIDE = 1006;

    // =========================================================
    // SHOW SETTINGS POPUP
    // =========================================================

    public static void show(View anchor) {

        if (anchor == null || anchor.getContext() == null) {
            return;
        }

        Context context = anchor.getContext();

        // Theme-aware colors.
        int backgroundColor = getColor(
                context,
                R.color.app_surface_elevated
        );

        int borderColor = getColor(
                context,
                R.color.divider_color
        );

        // Scrollable wrapper so the full list is always reachable.
        ScrollView scrollContainer = new ScrollView(context);
        scrollContainer.setVerticalScrollBarEnabled(false);
        scrollContainer.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollContainer.setBackground(createRoundedBackground(
                backgroundColor,
                dp(context, 18),
                borderColor,
                dp(context, 1)
        ));
        scrollContainer.setClipToOutline(true);

        // Main popup container.
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        container.setPadding(
                dp(context, 10),
                dp(context, 10),
                dp(context, 10),
                dp(context, 10)
        );

        scrollContainer.addView(
                container,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        // Calculate popup width.
        int screenWidth = context.getResources()
                .getDisplayMetrics().widthPixels;

        int popupWidth = Math.min(
                dp(context, 320),
                screenWidth - dp(context, 24)
        );

        // Create popup.
        PopupWindow popupWindow = new PopupWindow(
                scrollContainer,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT)
        );

        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setElevation(dp(context, 12));

        // Appearance.
        addMenuItem(
                context,
                container,
                anchor,
                popupWindow,
                MENU_APPEARANCE,
                "◐",
                "Appearance",
                "Light, Dark, or System default",
                true
        );

        // Accessibility & Readability.
        addMenuItem(
                context,
                container,
                anchor,
                popupWindow,
                MENU_ACCESSIBILITY,
                "Aa",
                "Accessibility & Readability",
                "Text size and accessibility",
                true
        );

        // Audio & Pronunciation.
        addMenuItem(
                context,
                container,
                anchor,
                popupWindow,
                MENU_AUDIO,
                "♫",
                "Audio & Pronunciation",
                AudioPreferences.getSummary(context),
                true
        );

        // Language Preferences.
        addMenuItem(
                context,
                container,
                anchor,
                popupWindow,
                MENU_LANGUAGE,
                "文",
                "Language Preferences",
                LanguagePreferences.getSummary(context),
                true
        );

        // Quick Guide.
        addMenuItem(
                context,
                container,
                anchor,
                popupWindow,
                MENU_GUIDE,
                "?",
                "Quick Guide",
                "Replay the app walkthrough",
                true
        );

        // About Speak Smart.
        addMenuItem(
                context,
                container,
                anchor,
                popupWindow,
                MENU_ABOUT,
                "i",
                "About Speak Smart",
                "App information and details",
                false
        );

        // Display popup: center when launched from a large card tile,
        // or drop down aligned with small gear buttons.
        if (anchor.isAttachedToWindow()) {
            if (anchor.getHeight() > dp(context, 60)) {
                popupWindow.showAtLocation(
                        anchor,
                        Gravity.CENTER,
                        0,
                        0
                );
            } else {
                popupWindow.showAsDropDown(
                        anchor,
                        0,
                        dp(context, 8),
                        Gravity.END
                );
            }

            scrollContainer.setAlpha(0f);
            scrollContainer.setScaleX(0.94f);
            scrollContainer.setScaleY(0.94f);
            scrollContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    // =========================================================
    // CREATE EACH MENU ROW
    // =========================================================

    private static void addMenuItem(
            Context context,
            LinearLayout container,
            View originalAnchor,
            PopupWindow popupWindow,
            int menuId,
            String iconText,
            String title,
            String subtitle,
            boolean showDivider
    ) {

        Typeface bodyFont = ResourcesCompat.getFont(
                context,
                R.font.font_body
        );

        // Theme-aware colors.
        int tealColor = getColor(
                context,
                R.color.cyan_primary
        );

        int iconBackgroundColor = getColor(
                context,
                R.color.app_surface_pressed
        );

        int titleColor = getColor(
                context,
                R.color.text_primary
        );

        int subtitleColor = getColor(
                context,
                R.color.text_secondary
        );

        int dividerColor = getColor(
                context,
                R.color.divider_color
        );

        // Entire row is clickable.
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.setPadding(
                dp(context, 8),
                dp(context, 11),
                dp(context, 8),
                dp(context, 11)
        );

        row.setBackground(createRoundedBackground(
                Color.TRANSPARENT,
                dp(context, 12),
                Color.TRANSPARENT,
                0
        ));

        row.setClickable(true);
        row.setFocusable(true);

        // Icon tile.
        TextView icon = new TextView(context);
        icon.setText(iconText);
        icon.setTextColor(tealColor);
        icon.setTextSize(iconText.length() > 1 ? 16 : 21);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);

        icon.setBackground(createRoundedBackground(
                iconBackgroundColor,
                dp(context, 11),
                Color.TRANSPARENT,
                0
        ));

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(context, 42),
                        dp(context, 42)
                );

        iconParams.setMarginEnd(dp(context, 12));
        row.addView(icon, iconParams);

        // Title and subtitle.
        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(titleColor);
        titleView.setTextSize(14);
        titleView.setTypeface(
                bodyFont != null ? bodyFont : Typeface.DEFAULT,
                Typeface.BOLD
        );
        titleView.setMaxLines(2);

        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(subtitleColor);
        subtitleView.setTextSize(11);
        if (bodyFont != null) {
            subtitleView.setTypeface(bodyFont);
        }
        subtitleView.setMaxLines(2);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin = dp(context, 3);

        textContainer.addView(titleView);
        textContainer.addView(subtitleView, subtitleParams);

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        row.addView(textContainer, textParams);

        // Right-facing chevron.
        TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextColor(tealColor);
        chevron.setTextSize(25);
        chevron.setGravity(Gravity.CENTER);

        row.addView(
                chevron,
                new LinearLayout.LayoutParams(
                        dp(context, 22),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        // Handle menu selection.
        row.setOnClickListener(v -> {

            if (popupWindow.isShowing()) {
                popupWindow.dismiss();
            }

            handleMenuSelection(originalAnchor, menuId);
        });

        container.addView(
                row,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        // Separator between menu options.
        if (showDivider) {

            View divider = new View(context);
            divider.setBackgroundColor(dividerColor);

            LinearLayout.LayoutParams dividerParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(context, 1)
                    );

            dividerParams.setMargins(
                    dp(context, 8),
                    dp(context, 2),
                    dp(context, 8),
                    dp(context, 2)
            );

            container.addView(divider, dividerParams);
        }
    }

    // =========================================================
    // HANDLE MENU SELECTION
    // =========================================================

    private static void handleMenuSelection(
            View anchor,
            int menuId
    ) {

        if (anchor == null || anchor.getContext() == null) {
            return;
        }

        Context context = anchor.getContext();

        if (menuId == MENU_APPEARANCE) {

            showAppearanceDialog(context);

        } else if (menuId == MENU_ACCESSIBILITY) {

            showAccessibilityDialog(context, anchor.getRootView(), null);

        } else if (menuId == MENU_AUDIO) {

            showAudioSettingsDialog(context, null);

        } else if (menuId == MENU_LANGUAGE) {

            showLanguagePreferencesDialog(context, null);

        } else if (menuId == MENU_GUIDE) {

            Intent intent = new Intent(context, FirstTimeGuideActivity.class);
            intent.putExtra(FirstTimeGuideActivity.EXTRA_FROM_SETTINGS, true);
            context.startActivity(intent);
            if (context instanceof Activity) {
                ((Activity) context).overridePendingTransition(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                );
            }

        } else if (menuId == MENU_ABOUT) {

            showAboutDialog(context);
        }
    }

    // =========================================================
    // MODERN APPEARANCE SELECTION MODAL
    // =========================================================

    public static void showAppearanceDialog(Context context) {

        Typeface displayFont = ResourcesCompat.getFont(
                context,
                R.font.font_display
        );

        Typeface bodyFont = ResourcesCompat.getFont(
                context,
                R.font.font_body
        );

        Typeface labelFont = ResourcesCompat.getFont(
                context,
                R.font.font_label
        );

        int cyanPrimary = getColor(context, R.color.cyan_primary);
        int textOnCyan = getColor(context, R.color.text_on_cyan);
        int textPrimary = getColor(context, R.color.text_primary);
        int textSecondary = getColor(context, R.color.text_secondary);
        int surfaceElevated = getColor(context, R.color.app_surface_elevated);
        int surfaceCard = getColor(context, R.color.app_surface);
        int surfacePressed = getColor(context, R.color.app_surface_pressed);
        int dividerColor = getColor(context, R.color.divider_color);

        String[] themeOptions = {
                "Light Mode",
                "Dark Mode",
                "System Default"
        };

        String[] themeDescriptions = {
                "Crisp, bright surface for daytime reading",
                "Easy on the eyes in low-light spaces",
                "Matches your device display settings"
        };

        String[] themeValues = {
                ThemeManager.LIGHT,
                ThemeManager.DARK,
                ThemeManager.SYSTEM
        };

        String savedTheme = ThemeManager.getSavedTheme(context);

        int selectedIndex = 2;
        for (int i = 0; i < themeValues.length; i++) {
            if (themeValues[i].equals(savedTheme)) {
                selectedIndex = i;
                break;
            }
        }

        // Main dialog container
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dp(context, 22),
                dp(context, 22),
                dp(context, 22),
                dp(context, 20)
        );

        GradientDrawable dialogBackground = new GradientDrawable();
        dialogBackground.setColor(surfaceElevated);
        dialogBackground.setCornerRadius(dp(context, 26));
        dialogBackground.setStroke(dp(context, 1), dividerColor);

        // Eyebrow label ("APPEARANCE")
        TextView eyebrow = new TextView(context);
        eyebrow.setText("APPEARANCE");
        eyebrow.setTextColor(cyanPrimary);
        eyebrow.setTextSize(11);
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTypeface(
                labelFont != null ? labelFont : Typeface.DEFAULT,
                Typeface.BOLD
        );
        content.addView(eyebrow);

        // Heading
        TextView heading = new TextView(context);
        heading.setText("Choose Theme");
        heading.setTextColor(textPrimary);
        heading.setTextSize(22);
        heading.setTypeface(
                displayFont != null ? displayFont : Typeface.DEFAULT,
                Typeface.BOLD
        );
        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        headingParams.topMargin = dp(context, 4);
        content.addView(heading, headingParams);

        // Subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText("Personalize how Speak Smart Baguio looks.");
        subtitle.setTextColor(textSecondary);
        subtitle.setTextSize(13);
        if (bodyFont != null) {
            subtitle.setTypeface(bodyFont);
        }
        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.topMargin = dp(context, 4);
        subtitleParams.bottomMargin = dp(context, 18);
        content.addView(subtitle, subtitleParams);

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(content)
                .create();

        final boolean[] isClosing = {false};

        // Theme option cards
        for (int i = 0; i < themeOptions.length; i++) {

            final int index = i;
            boolean isSelected = (i == selectedIndex);

            LinearLayout option = new LinearLayout(context);
            option.setOrientation(LinearLayout.HORIZONTAL);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(
                    dp(context, 14),
                    dp(context, 14),
                    dp(context, 14),
                    dp(context, 14)
            );

            GradientDrawable cardBackground = new GradientDrawable();
            cardBackground.setColor(isSelected ? surfacePressed : surfaceCard);
            cardBackground.setCornerRadius(dp(context, 18));
            cardBackground.setStroke(
                    dp(context, isSelected ? 2 : 1),
                    isSelected ? cyanPrimary : dividerColor
            );

            option.setBackground(cardBackground);
            option.setClickable(true);
            option.setFocusable(true);

            // Mini theme preview swatch on the left
            View swatch = createThemePreviewSwatch(context, themeValues[i], cyanPrimary);
            LinearLayout.LayoutParams swatchParams =
                    new LinearLayout.LayoutParams(
                            dp(context, 46),
                            dp(context, 46)
                    );
            swatchParams.setMarginEnd(dp(context, 14));
            option.addView(swatch, swatchParams);

            // Title & description
            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(context);
            title.setText(themeOptions[i]);
            title.setTextColor(textPrimary);
            title.setTextSize(15);
            title.setTypeface(
                    bodyFont != null ? bodyFont : Typeface.DEFAULT,
                    Typeface.BOLD
            );

            TextView description = new TextView(context);
            description.setText(themeDescriptions[i]);
            description.setTextColor(textSecondary);
            description.setTextSize(12);
            if (bodyFont != null) {
                description.setTypeface(bodyFont);
            }
            description.setMaxLines(2);

            LinearLayout.LayoutParams descriptionParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            descriptionParams.topMargin = dp(context, 3);

            textContainer.addView(title);
            textContainer.addView(description, descriptionParams);

            LinearLayout.LayoutParams textParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );
            option.addView(textContainer, textParams);

            // Modern selection indicator circle
            TextView radioCircle = new TextView(context);
            radioCircle.setGravity(Gravity.CENTER);
            radioCircle.setTextSize(13);
            radioCircle.setTypeface(Typeface.DEFAULT_BOLD);

            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            if (isSelected) {
                circleBg.setColor(cyanPrimary);
                radioCircle.setText("✓");
                radioCircle.setTextColor(textOnCyan);
            } else {
                circleBg.setColor(Color.TRANSPARENT);
                circleBg.setStroke(dp(context, 2), dividerColor);
                radioCircle.setText("");
            }
            radioCircle.setBackground(circleBg);

            LinearLayout.LayoutParams radioParams =
                    new LinearLayout.LayoutParams(
                            dp(context, 24),
                            dp(context, 24)
                    );
            radioParams.setMarginStart(dp(context, 10));
            option.addView(radioCircle, radioParams);

            // Apply selected theme with smooth exit animation
            option.setOnClickListener(v -> {
                if (isClosing[0]) {
                    return;
                }
                isClosing[0] = true;

                option.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(70L)
                        .withEndAction(() ->
                                dismissWithAnimation(
                                        dialog,
                                        content,
                                        () -> ThemeManager.setTheme(
                                                context,
                                                themeValues[index]
                                        )
                                )
                        )
                        .start();
            });

            LinearLayout.LayoutParams optionParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            optionParams.bottomMargin = dp(context, 10);
            content.addView(option, optionParams);
        }

        // Full-width modern pill Close button
        TextView closeButton = new TextView(context);
        closeButton.setText("Close");
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setTextSize(14);
        closeButton.setTextColor(textPrimary);
        closeButton.setTypeface(
                bodyFont != null ? bodyFont : Typeface.DEFAULT,
                Typeface.BOLD
        );
        closeButton.setClickable(true);
        closeButton.setFocusable(true);
        closeButton.setPadding(0, dp(context, 13), 0, dp(context, 13));

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(surfaceCard);
        closeBg.setCornerRadius(dp(context, 999));
        closeBg.setStroke(dp(context, 1), dividerColor);
        closeButton.setBackground(closeBg);

        // Color transition + press bounce + smooth modal dismiss animation on Close button
        closeButton.setOnClickListener(v -> {
            if (isClosing[0]) {
                return;
            }
            isClosing[0] = true;

            ArgbEvaluator colorEvaluator = new ArgbEvaluator();
            ValueAnimator colorAnim = ValueAnimator.ofFloat(0f, 1f);
            colorAnim.setDuration(120L);
            colorAnim.addUpdateListener(anim -> {
                float fraction = (float) anim.getAnimatedValue();
                int bg = (int) colorEvaluator.evaluate(fraction, surfaceCard, cyanPrimary);
                int stroke = (int) colorEvaluator.evaluate(fraction, dividerColor, cyanPrimary);
                int txt = (int) colorEvaluator.evaluate(fraction, textPrimary, textOnCyan);

                closeBg.setColor(bg);
                closeBg.setStroke(dp(context, 1), stroke);
                closeButton.setTextColor(txt);
            });
            colorAnim.start();

            closeButton.animate()
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .setDuration(80L)
                    .withEndAction(() -> {
                        closeButton.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(80L)
                                .start();

                        dismissWithAnimation(dialog, content, null);
                    })
                    .start();
        });

        LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        closeParams.topMargin = dp(context, 6);
        content.addView(closeButton, closeParams);

        dialog.show();
        applyDialogEntrance(dialog, content, dialogBackground, context);
    }

    // =========================================================
    // ACCESSIBILITY & READABILITY MODAL
    // =========================================================

    public static void showAccessibilityDialog(
            Context context,
            @Nullable View targetRootView,
            @Nullable Runnable onUpdated
    ) {
        Typeface displayFont = ResourcesCompat.getFont(context, R.font.font_display);
        Typeface bodyFont = ResourcesCompat.getFont(context, R.font.font_body);
        Typeface labelFont = ResourcesCompat.getFont(context, R.font.font_label);

        int cyanPrimary = getColor(context, R.color.cyan_primary);
        int textOnCyan = getColor(context, R.color.text_on_cyan);
        int textPrimary = getColor(context, R.color.text_primary);
        int textSecondary = getColor(context, R.color.text_secondary);
        int surfaceElevated = getColor(context, R.color.app_surface_elevated);
        int surfaceCard = getColor(context, R.color.app_surface);
        int surfacePressed = getColor(context, R.color.app_surface_pressed);
        int dividerColor = getColor(context, R.color.divider_color);

        View liveRoot = targetRootView;
        if (liveRoot == null && context instanceof Activity) {
            liveRoot = ((Activity) context).getWindow().getDecorView();
        }
        final View applyTarget = liveRoot;

        Runnable notifyAndApply = () -> {
            if (applyTarget != null) {
                AccessibilityPreferences.applyToViewTree(context, applyTarget);
            }
            if (onUpdated != null) {
                onUpdated.run();
            }
        };

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 22), dp(context, 22), dp(context, 22), dp(context, 20));
        scroll.addView(content);

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(surfaceElevated);
        dialogBg.setCornerRadius(dp(context, 26));
        dialogBg.setStroke(dp(context, 1), dividerColor);

        TextView eyebrow = new TextView(context);
        eyebrow.setText("ACCESSIBILITY & READABILITY");
        eyebrow.setTextColor(cyanPrimary);
        eyebrow.setTextSize(11);
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);

        TextView heading = new TextView(context);
        heading.setText("Display & Text");
        heading.setTextColor(textPrimary);
        heading.setTextSize(22);
        heading.setTypeface(displayFont != null ? displayFont : Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(context, 4);
        content.addView(heading, headingParams);

        TextView subtitle = new TextView(context);
        subtitle.setText("Adjust text size, contrast, and layout simplicity.");
        subtitle.setTextColor(textSecondary);
        subtitle.setTextSize(13);
        if (bodyFont != null) subtitle.setTypeface(bodyFont);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(context, 4);
        subParams.bottomMargin = dp(context, 16);
        content.addView(subtitle, subParams);

        TextView sizeLabel = new TextView(context);
        sizeLabel.setText("TEXT SIZE");
        sizeLabel.setTextColor(textSecondary);
        sizeLabel.setTextSize(11);
        sizeLabel.setLetterSpacing(0.10f);
        sizeLabel.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(sizeLabel);

        final float[] scaleValues = {
                AccessibilityPreferences.FONT_SCALE_STANDARD,
                AccessibilityPreferences.FONT_SCALE_LARGE,
                AccessibilityPreferences.FONT_SCALE_EXTRA_LARGE
        };
        final String[] scaleLabels = {"Standard", "Large", "Extra Large"};

        LinearLayout scaleContainer = new LinearLayout(context);
        scaleContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(context, 8);
        rowParams.bottomMargin = dp(context, 14);
        content.addView(scaleContainer, rowParams);

        LinearLayout togglesContainer = new LinearLayout(context);
        togglesContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(togglesContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable[] renderControls = new Runnable[1];
        renderControls[0] = () -> {
            scaleContainer.removeAllViews();
            scaleContainer.addView(createChipRow(
                    context, scaleLabels, scaleValues, AccessibilityPreferences.getFontScale(context),
                    cyanPrimary, textOnCyan, textPrimary, surfaceCard, dividerColor, bodyFont,
                    val -> {
                        AccessibilityPreferences.setFontScale(context, val);
                        notifyAndApply.run();
                    }
            ));

            togglesContainer.removeAllViews();
            togglesContainer.addView(createToggleCard(
                    context,
                    "High Contrast",
                    "Sharper text and stronger borders",
                    AccessibilityPreferences.isHighContrastEnabled(context),
                    cyanPrimary, textOnCyan, textPrimary, textSecondary,
                    surfaceCard, surfacePressed, dividerColor, bodyFont,
                    enabled -> {
                        AccessibilityPreferences.setHighContrastEnabled(context, enabled);
                        notifyAndApply.run();
                        renderControls[0].run();
                    }
            ));

            LinearLayout simplifiedCard = createToggleCard(
                    context,
                    "Simplified Layout",
                    "Reduce extra visual clutter",
                    AccessibilityPreferences.isSimplifiedLayoutEnabled(context),
                    cyanPrimary, textOnCyan, textPrimary, textSecondary,
                    surfaceCard, surfacePressed, dividerColor, bodyFont,
                    enabled -> {
                        AccessibilityPreferences.setSimplifiedLayoutEnabled(context, enabled);
                        notifyAndApply.run();
                        renderControls[0].run();
                    }
            );
            togglesContainer.addView(simplifiedCard);
        };
        renderControls[0].run();

        TextView resetBtn = new TextView(context);
        resetBtn.setText("Reset to Default");
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setTextSize(13);
        resetBtn.setTextColor(cyanPrimary);
        resetBtn.setTypeface(bodyFont != null ? bodyFont : Typeface.DEFAULT, Typeface.BOLD);
        resetBtn.setPadding(0, dp(context, 11), 0, dp(context, 11));
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setColor(surfacePressed);
        resetBg.setCornerRadius(dp(context, 14));
        resetBg.setStroke(dp(context, 1), cyanPrimary);
        resetBtn.setBackground(resetBg);
        resetBtn.setOnClickListener(v -> {
            AccessibilityPreferences.reset(context);
            notifyAndApply.run();
            renderControls[0].run();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.topMargin = dp(context, 6);
        resetParams.bottomMargin = dp(context, 10);
        content.addView(resetBtn, resetParams);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();

        TextView closeBtn = createAnimatedCloseButton(
                context, dialog, scroll, surfaceCard, dividerColor, cyanPrimary, textPrimary, textOnCyan, bodyFont);
        content.addView(closeBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.show();
        applyDialogEntrance(dialog, scroll, dialogBg, context);
    }

    // =========================================================
    // ABOUT SPEAK SMART MODAL
    // =========================================================

    public static void showAboutDialog(Context context) {
        Typeface displayFont = ResourcesCompat.getFont(context, R.font.font_display);
        Typeface bodyFont = ResourcesCompat.getFont(context, R.font.font_body);
        Typeface labelFont = ResourcesCompat.getFont(context, R.font.font_label);

        int cyanPrimary = getColor(context, R.color.cyan_primary);
        int cyanSecondary = getColor(context, R.color.cyan_secondary);
        int textOnCyan = getColor(context, R.color.text_on_cyan);
        int textPrimary = getColor(context, R.color.text_primary);
        int textSecondary = getColor(context, R.color.text_secondary);
        int surfaceElevated = getColor(context, R.color.app_surface_elevated);
        int surfaceCard = getColor(context, R.color.app_surface);
        int surfacePressed = getColor(context, R.color.app_surface_pressed);
        int dividerColor = getColor(context, R.color.divider_color);

        String versionName = "1.0";
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (info != null && info.versionName != null) {
                versionName = info.versionName;
            }
        } catch (Exception ignored) {
        }

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 22), dp(context, 22), dp(context, 22), dp(context, 20));
        scroll.addView(content);

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(surfaceElevated);
        dialogBg.setCornerRadius(dp(context, 26));
        dialogBg.setStroke(dp(context, 1), dividerColor);

        // Eyebrow
        TextView eyebrow = new TextView(context);
        eyebrow.setText("APP INFORMATION");
        eyebrow.setTextColor(cyanPrimary);
        eyebrow.setTextSize(11);
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);

        // Hero Branding Card
        LinearLayout heroCard = new LinearLayout(context);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setGravity(Gravity.CENTER_HORIZONTAL);
        heroCard.setPadding(dp(context, 16), dp(context, 18), dp(context, 16), dp(context, 18));
        GradientDrawable heroBg = new GradientDrawable();
        heroBg.setColor(surfaceCard);
        heroBg.setCornerRadius(dp(context, 20));
        heroBg.setStroke(dp(context, 1), dividerColor);
        heroCard.setBackground(heroBg);

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Speak Smart Baguio Logo");
        heroCard.addView(logo, new LinearLayout.LayoutParams(dp(context, 78), dp(context, 78)));

        TextView heading = new TextView(context);
        heading.setText("Speak Smart Baguio");
        heading.setGravity(Gravity.CENTER);
        heading.setTextColor(cyanPrimary);
        heading.setTextSize(22);
        heading.setTypeface(displayFont != null ? displayFont : Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(context, 8);
        heroCard.addView(heading, headingParams);

        TextView pillTag = new TextView(context);
        pillTag.setText("TRANSLATOR  •  DICTIONARY  •  PHRASEBOOK");
        pillTag.setGravity(Gravity.CENTER);
        pillTag.setTextColor(cyanSecondary);
        pillTag.setTextSize(9.5f);
        pillTag.setLetterSpacing(0.06f);
        pillTag.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        pillTag.setPadding(dp(context, 12), dp(context, 5), dp(context, 12), dp(context, 5));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(surfacePressed);
        pillBg.setCornerRadius(dp(context, 999));
        pillTag.setBackground(pillBg);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.topMargin = dp(context, 8);
        heroCard.addView(pillTag, pillParams);

        TextView versionText = new TextView(context);
        versionText.setText("Version " + versionName);
        versionText.setTextColor(textSecondary);
        versionText.setTextSize(11);
        if (bodyFont != null) versionText.setTypeface(bodyFont);
        LinearLayout.LayoutParams verParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        verParams.topMargin = dp(context, 6);
        heroCard.addView(versionText, verParams);

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroParams.topMargin = dp(context, 10);
        heroParams.bottomMargin = dp(context, 14);
        content.addView(heroCard, heroParams);

        // About the Application
        addInfoCard(
                context, content,
                "About the Application",
                "Speak Smart Baguio is an offline-ready language translation and learning application designed to help tourists, visitors, students, and locals explore English, Tagalog, and Benguet Ilokano in one accessible mobile app.",
                textPrimary, textSecondary, surfaceCard, dividerColor, displayFont, bodyFont
        );

        // What's Inside Header
        TextView insideEyebrow = new TextView(context);
        insideEyebrow.setText("WHAT'S INSIDE");
        insideEyebrow.setTextColor(cyanSecondary);
        insideEyebrow.setTextSize(10.5f);
        insideEyebrow.setLetterSpacing(0.14f);
        insideEyebrow.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams ieParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ieParams.topMargin = dp(context, 6);
        content.addView(insideEyebrow, ieParams);

        TextView featuresTitle = new TextView(context);
        featuresTitle.setText("Main Features");
        featuresTitle.setTextColor(textPrimary);
        featuresTitle.setTextSize(18);
        featuresTitle.setTypeface(displayFont != null ? displayFont : Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams ftParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ftParams.topMargin = dp(context, 2);
        ftParams.bottomMargin = dp(context, 10);
        content.addView(featuresTitle, ftParams);

        addFeatureCard(context, content, R.drawable.ic_translator, null,
                "Translator",
                "Translate between English, Tagalog, and Ilokano with automatic \"Did you mean?\" suggestions from your database when a word is misspelled.",
                cyanPrimary, textPrimary, textSecondary, surfaceCard, dividerColor, displayFont, bodyFont);

        addFeatureCard(context, content, R.drawable.ic_dictionary, null,
                "Dictionary",
                "Look up words, parts of speech, and meanings with offline support and instant sorting.",
                cyanPrimary, textPrimary, textSecondary, surfaceCard, dividerColor, displayFont, bodyFont);

        addFeatureCard(context, content, R.drawable.ic_phrasebook, null,
                "Phrasebook",
                "Browse everyday Ilokano phrases for greetings, directions, and daily conversations.",
                cyanPrimary, textPrimary, textSecondary, surfaceCard, dividerColor, displayFont, bodyFont);

        addFeatureCard(context, content, 0, "♥",
                "Favorites",
                "Save and revisit your favorite words and phrases for quick offline reference.",
                cyanPrimary, textPrimary, textSecondary, surfaceCard, dividerColor, displayFont, bodyFont);

        addFeatureCard(context, content, 0, "♫",
                "Audio & Pronunciation",
                "Listen to words and phrases spoken aloud with customizable speech speed and voice pitch.",
                cyanPrimary, textPrimary, textSecondary, surfaceCard, dividerColor, displayFont, bodyFont);

        addFeatureCard(context, content, 0, "◐",
                "Personalization",
                "Switch between Light, Dark, or System themes, set default Language Preferences, or replay the Quick Guide anytime.",
                cyanPrimary, textPrimary, textSecondary, surfaceCard, dividerColor, displayFont, bodyFont);

        addFeatureCard(context, content, 0, "Aa",
                "Accessibility",
                "Customize text size, high contrast, and simplified layout to make the app easier and more comfortable to read.",
                cyanPrimary, textPrimary, textSecondary, surfaceCard, dividerColor, displayFont, bodyFont);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();

        TextView closeBtn = createAnimatedCloseButton(
                context, dialog, scroll, surfaceCard, dividerColor, cyanPrimary, textPrimary, textOnCyan, bodyFont);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = dp(context, 6);
        content.addView(closeBtn, closeParams);

        dialog.show();
        applyDialogEntrance(dialog, scroll, dialogBg, context);
    }

    private static void addInfoCard(
            Context context, LinearLayout parent, String title, String body,
            int textPrimary, int textSecondary, int surfaceCard, int dividerColor,
            Typeface displayFont, Typeface bodyFont
    ) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surfaceCard);
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), dividerColor);
        card.setBackground(bg);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(textPrimary);
        titleView.setTextSize(16);
        titleView.setTypeface(displayFont != null ? displayFont : Typeface.DEFAULT, Typeface.BOLD);

        TextView bodyView = new TextView(context);
        bodyView.setText(body);
        bodyView.setTextColor(textSecondary);
        bodyView.setTextSize(13);
        bodyView.setLineSpacing(dp(context, 3), 1f);
        if (bodyFont != null) bodyView.setTypeface(bodyFont);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(context, 6);

        card.addView(titleView);
        card.addView(bodyView, bp);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(context, 10);
        parent.addView(card, p);
    }

    private static void addFeatureCard(
            Context context, LinearLayout parent, int iconRes, @Nullable String symbolText,
            String title, String description,
            int cyanPrimary, int textPrimary, int textSecondary, int surfaceCard, int dividerColor,
            Typeface displayFont, Typeface bodyFont
    ) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surfaceCard);
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), dividerColor);
        card.setBackground(bg);

        if (iconRes != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            icon.setColorFilter(cyanPrimary);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32));
            ip.setMarginEnd(dp(context, 12));
            ip.topMargin = dp(context, 2);
            card.addView(icon, ip);
        } else {
            TextView icon = new TextView(context);
            icon.setText(symbolText != null ? symbolText : "•");
            icon.setTextColor(cyanPrimary);
            icon.setTextSize(22);
            icon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32));
            ip.setMarginEnd(dp(context, 12));
            card.addView(icon, ip);
        }

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(textPrimary);
        titleView.setTextSize(15);
        titleView.setTypeface(displayFont != null ? displayFont : Typeface.DEFAULT, Typeface.BOLD);

        TextView descView = new TextView(context);
        descView.setText(description);
        descView.setTextColor(textSecondary);
        descView.setTextSize(12.5f);
        descView.setLineSpacing(dp(context, 2), 1f);
        if (bodyFont != null) descView.setTypeface(bodyFont);
        LinearLayout.LayoutParams dpParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dpParams.topMargin = dp(context, 3);

        textCol.addView(titleView);
        textCol.addView(descView, dpParams);
        card.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(context, 8);
        parent.addView(card, p);
    }

    // =========================================================
    // AUDIO & PRONUNCIATION MODAL
    // =========================================================

    public static void showAudioSettingsDialog(
            Context context,
            @Nullable Runnable onUpdated
    ) {
        Typeface displayFont = ResourcesCompat.getFont(context, R.font.font_display);
        Typeface bodyFont = ResourcesCompat.getFont(context, R.font.font_body);
        Typeface labelFont = ResourcesCompat.getFont(context, R.font.font_label);

        int cyanPrimary = getColor(context, R.color.cyan_primary);
        int textOnCyan = getColor(context, R.color.text_on_cyan);
        int textPrimary = getColor(context, R.color.text_primary);
        int textSecondary = getColor(context, R.color.text_secondary);
        int surfaceElevated = getColor(context, R.color.app_surface_elevated);
        int surfaceCard = getColor(context, R.color.app_surface);
        int surfacePressed = getColor(context, R.color.app_surface_pressed);
        int dividerColor = getColor(context, R.color.divider_color);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 22), dp(context, 22), dp(context, 22), dp(context, 20));
        scroll.addView(content);

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(surfaceElevated);
        dialogBg.setCornerRadius(dp(context, 26));
        dialogBg.setStroke(dp(context, 1), dividerColor);

        TextView eyebrow = new TextView(context);
        eyebrow.setText("AUDIO & PRONUNCIATION");
        eyebrow.setTextColor(cyanPrimary);
        eyebrow.setTextSize(11);
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);

        TextView heading = new TextView(context);
        heading.setText("Voice Settings");
        heading.setTextColor(textPrimary);
        heading.setTextSize(22);
        heading.setTypeface(displayFont != null ? displayFont : Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(context, 4);
        content.addView(heading, headingParams);

        TextView subtitle = new TextView(context);
        subtitle.setText("Adjust pronunciation speed and pitch.");
        subtitle.setTextColor(textSecondary);
        subtitle.setTextSize(13);
        if (bodyFont != null) subtitle.setTypeface(bodyFont);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(context, 4);
        subParams.bottomMargin = dp(context, 16);
        content.addView(subtitle, subParams);

        TextView rateLabel = new TextView(context);
        rateLabel.setText("SPEECH SPEED");
        rateLabel.setTextColor(textSecondary);
        rateLabel.setTextSize(11);
        rateLabel.setLetterSpacing(0.10f);
        rateLabel.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(rateLabel);

        final float[] rateValues = {
                AudioPreferences.RATE_SLOW,
                AudioPreferences.RATE_NORMAL,
                AudioPreferences.RATE_FAST
        };
        final String[] rateLabels = {"Slow (0.65x)", "Normal (0.85x)", "Fast (1.05x)"};

        LinearLayout rateRow = createChipRow(
                context, rateLabels, rateValues, AudioPreferences.getSpeechRate(context),
                cyanPrimary, textOnCyan, textPrimary, surfaceCard, dividerColor, bodyFont,
                val -> {
                    AudioPreferences.setSpeechRate(context, val);
                    if (onUpdated != null) onUpdated.run();
                }
        );
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(context, 8);
        rowParams.bottomMargin = dp(context, 16);
        content.addView(rateRow, rowParams);

        TextView pitchLabel = new TextView(context);
        pitchLabel.setText("VOICE PITCH");
        pitchLabel.setTextColor(textSecondary);
        pitchLabel.setTextSize(11);
        pitchLabel.setLetterSpacing(0.10f);
        pitchLabel.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(pitchLabel);

        final float[] pitchValues = {
                AudioPreferences.PITCH_DEEP,
                AudioPreferences.PITCH_NATURAL,
                AudioPreferences.PITCH_BRIGHT
        };
        final String[] pitchLabels = {"Deeper", "Natural", "Bright"};

        LinearLayout pitchRow = createChipRow(
                context, pitchLabels, pitchValues, AudioPreferences.getVoicePitch(context),
                cyanPrimary, textOnCyan, textPrimary, surfaceCard, dividerColor, bodyFont,
                val -> {
                    AudioPreferences.setVoicePitch(context, val);
                    if (onUpdated != null) onUpdated.run();
                }
        );
        content.addView(pitchRow, rowParams);

        final TextToSpeech[] previewTts = {null};
        final boolean[] ttsReady = {false};
        previewTts[0] = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS && previewTts[0] != null) {
                Locale fil = new Locale("fil", "PH");
                int res = previewTts[0].setLanguage(fil);
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    previewTts[0].setLanguage(Locale.getDefault());
                }
                ttsReady[0] = true;
            }
        });

        TextView testBtn = new TextView(context);
        testBtn.setText("♫  Test Voice (Kumusta ka?)");
        testBtn.setGravity(Gravity.CENTER);
        testBtn.setTextSize(13);
        testBtn.setTextColor(cyanPrimary);
        testBtn.setTypeface(bodyFont != null ? bodyFont : Typeface.DEFAULT, Typeface.BOLD);
        testBtn.setPadding(0, dp(context, 12), 0, dp(context, 12));
        GradientDrawable testBg = new GradientDrawable();
        testBg.setColor(surfacePressed);
        testBg.setCornerRadius(dp(context, 14));
        testBg.setStroke(dp(context, 1), cyanPrimary);
        testBtn.setBackground(testBg);
        testBtn.setOnClickListener(v -> {
            if (previewTts[0] != null && ttsReady[0]) {
                AudioPreferences.applyToTextToSpeech(context, previewTts[0]);
                previewTts[0].speak("Kumusta ka?", TextToSpeech.QUEUE_FLUSH, null, "test_voice");
            }
        });
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        testParams.bottomMargin = dp(context, 10);
        content.addView(testBtn, testParams);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();
        dialog.setOnDismissListener(d -> {
            if (previewTts[0] != null) {
                previewTts[0].stop();
                previewTts[0].shutdown();
                previewTts[0] = null;
            }
        });

        TextView closeBtn = createAnimatedCloseButton(
                context, dialog, scroll, surfaceCard, dividerColor, cyanPrimary, textPrimary, textOnCyan, bodyFont);
        content.addView(closeBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.show();
        applyDialogEntrance(dialog, scroll, dialogBg, context);
    }

    // =========================================================
    // LANGUAGE PREFERENCES MODAL
    // =========================================================

    public static void showLanguagePreferencesDialog(
            Context context,
            @Nullable Runnable onUpdated
    ) {
        Typeface displayFont = ResourcesCompat.getFont(context, R.font.font_display);
        Typeface bodyFont = ResourcesCompat.getFont(context, R.font.font_body);
        Typeface labelFont = ResourcesCompat.getFont(context, R.font.font_label);

        int cyanPrimary = getColor(context, R.color.cyan_primary);
        int textOnCyan = getColor(context, R.color.text_on_cyan);
        int textPrimary = getColor(context, R.color.text_primary);
        int textSecondary = getColor(context, R.color.text_secondary);
        int surfaceElevated = getColor(context, R.color.app_surface_elevated);
        int surfaceCard = getColor(context, R.color.app_surface);
        int dividerColor = getColor(context, R.color.divider_color);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 22), dp(context, 22), dp(context, 22), dp(context, 20));
        scroll.addView(content);

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(surfaceElevated);
        dialogBg.setCornerRadius(dp(context, 26));
        dialogBg.setStroke(dp(context, 1), dividerColor);

        TextView eyebrow = new TextView(context);
        eyebrow.setText("LANGUAGE PREFERENCES");
        eyebrow.setTextColor(cyanPrimary);
        eyebrow.setTextSize(11);
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);

        TextView heading = new TextView(context);
        heading.setText("Default Languages");
        heading.setTextColor(textPrimary);
        heading.setTextSize(22);
        heading.setTypeface(displayFont != null ? displayFont : Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(context, 4);
        content.addView(heading, headingParams);

        TextView subtitle = new TextView(context);
        subtitle.setText("Set your default translation language and mode.");
        subtitle.setTextColor(textSecondary);
        subtitle.setTextSize(13);
        if (bodyFont != null) subtitle.setTypeface(bodyFont);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(context, 4);
        subParams.bottomMargin = dp(context, 16);
        content.addView(subtitle, subParams);

        TextView targetLabel = new TextView(context);
        targetLabel.setText("DICTIONARY & PHRASEBOOK");
        targetLabel.setTextColor(textSecondary);
        targetLabel.setTextSize(11);
        targetLabel.setLetterSpacing(0.10f);
        targetLabel.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(targetLabel);

        final String[] langOptions = {
                LanguagePreferences.LANG_ENGLISH,
                LanguagePreferences.LANG_TAGALOG
        };

        LinearLayout langRow = createStringChipRow(
                context, langOptions, LanguagePreferences.getDefaultTargetLanguage(context),
                cyanPrimary, textOnCyan, textPrimary, surfaceCard, dividerColor, bodyFont,
                val -> {
                    LanguagePreferences.setDefaultTargetLanguage(context, val);
                    if (onUpdated != null) onUpdated.run();
                }
        );
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(context, 8);
        rowParams.bottomMargin = dp(context, 16);
        content.addView(langRow, rowParams);

        TextView modeLabel = new TextView(context);
        modeLabel.setText("DEFAULT TRANSLATOR MODE");
        modeLabel.setTextColor(textSecondary);
        modeLabel.setTextSize(11);
        modeLabel.setLetterSpacing(0.10f);
        modeLabel.setTypeface(labelFont != null ? labelFont : Typeface.DEFAULT, Typeface.BOLD);
        content.addView(modeLabel);

        final String[] modes = {
                LanguagePreferences.MODE_ENGLISH_TO_ILOKANO,
                LanguagePreferences.MODE_ILOKANO_TO_ENGLISH,
                LanguagePreferences.MODE_TAGALOG_TO_ILOKANO,
                LanguagePreferences.MODE_ILOKANO_TO_TAGALOG
        };
        final String[] currentMode = {LanguagePreferences.getDefaultTranslationMode(context)};

        LinearLayout modeList = new LinearLayout(context);
        modeList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams modeListParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeListParams.topMargin = dp(context, 8);
        modeListParams.bottomMargin = dp(context, 10);
        content.addView(modeList, modeListParams);

        final Runnable[] refreshModes = new Runnable[1];
        refreshModes[0] = () -> {
            modeList.removeAllViews();
            for (String mode : modes) {
                boolean selected = mode.equals(currentMode[0]);
                TextView item = new TextView(context);
                item.setText(selected ? "✓   " + mode : mode);
                item.setTextSize(13);
                item.setTextColor(selected ? textOnCyan : textPrimary);
                item.setTypeface(bodyFont != null ? bodyFont : Typeface.DEFAULT, Typeface.BOLD);
                item.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(selected ? cyanPrimary : surfaceCard);
                bg.setCornerRadius(dp(context, 14));
                bg.setStroke(dp(context, 1), selected ? cyanPrimary : dividerColor);
                item.setBackground(bg);

                item.setOnClickListener(v -> {
                    currentMode[0] = mode;
                    LanguagePreferences.setDefaultTranslationMode(context, mode);
                    if (onUpdated != null) onUpdated.run();
                    refreshModes[0].run();
                });

                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                p.bottomMargin = dp(context, 8);
                modeList.addView(item, p);
            }
        };
        refreshModes[0].run();

        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();

        TextView closeBtn = createAnimatedCloseButton(
                context, dialog, scroll, surfaceCard, dividerColor, cyanPrimary, textPrimary, textOnCyan, bodyFont);
        content.addView(closeBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.show();
        applyDialogEntrance(dialog, scroll, dialogBg, context);
    }

    private interface FloatConsumer {
        void accept(float value);
    }

    private interface StringConsumer {
        void accept(String value);
    }

    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private static LinearLayout createToggleCard(
            Context context, String title, String subtitle, boolean enabled,
            int cyanPrimary, int textOnCyan, int textPrimary, int textSecondary,
            int surfaceCard, int surfacePressed, int dividerColor,
            Typeface bodyFont, BooleanConsumer onToggle
    ) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(enabled ? surfacePressed : surfaceCard);
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), enabled ? cyanPrimary : dividerColor);
        card.setBackground(bg);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(textPrimary);
        titleView.setTextSize(14);
        titleView.setTypeface(bodyFont != null ? bodyFont : Typeface.DEFAULT, Typeface.BOLD);

        TextView subView = new TextView(context);
        subView.setText(subtitle);
        subView.setTextColor(textSecondary);
        subView.setTextSize(11);
        if (bodyFont != null) subView.setTypeface(bodyFont);

        textCol.addView(titleView);
        textCol.addView(subView);
        card.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView pill = new TextView(context);
        pill.setText(enabled ? "ON" : "OFF");
        pill.setTextSize(11);
        pill.setGravity(Gravity.CENTER);
        pill.setTypeface(bodyFont != null ? bodyFont : Typeface.DEFAULT, Typeface.BOLD);
        pill.setTextColor(enabled ? textOnCyan : textSecondary);
        pill.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(enabled ? cyanPrimary : surfaceCard);
        pillBg.setCornerRadius(dp(context, 999));
        pillBg.setStroke(dp(context, 1), enabled ? cyanPrimary : dividerColor);
        pill.setBackground(pillBg);
        card.addView(pill);

        card.setOnClickListener(v -> onToggle.accept(!enabled));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(context, 10);
        card.setLayoutParams(p);
        return card;
    }

    private static LinearLayout createChipRow(
            Context context, String[] labels, float[] values, float initial,
            int cyanPrimary, int textOnCyan, int textPrimary, int surfaceCard, int dividerColor,
            Typeface bodyFont, FloatConsumer onSelect
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final float[] current = {initial};
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            row.removeAllViews();
            for (int i = 0; i < labels.length; i++) {
                final float val = values[i];
                boolean selected = Math.abs(val - current[0]) < 0.01f;
                TextView chip = new TextView(context);
                chip.setText(labels[i]);
                chip.setGravity(Gravity.CENTER);
                chip.setTextSize(12);
                chip.setTextColor(selected ? textOnCyan : textPrimary);
                chip.setTypeface(bodyFont != null ? bodyFont : Typeface.DEFAULT, Typeface.BOLD);
                chip.setPadding(dp(context, 6), dp(context, 10), dp(context, 6), dp(context, 10));

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(selected ? cyanPrimary : surfaceCard);
                bg.setCornerRadius(dp(context, 12));
                bg.setStroke(dp(context, 1), selected ? cyanPrimary : dividerColor);
                chip.setBackground(bg);

                chip.setOnClickListener(v -> {
                    current[0] = val;
                    onSelect.accept(val);
                    refresh[0].run();
                });

                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i < labels.length - 1) p.setMarginEnd(dp(context, 8));
                row.addView(chip, p);
            }
        };
        refresh[0].run();
        return row;
    }

    private static LinearLayout createStringChipRow(
            Context context, String[] options, String initial,
            int cyanPrimary, int textOnCyan, int textPrimary, int surfaceCard, int dividerColor,
            Typeface bodyFont, StringConsumer onSelect
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final String[] current = {initial};
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            row.removeAllViews();
            for (int i = 0; i < options.length; i++) {
                final String val = options[i];
                boolean selected = val.equals(current[0]);
                TextView chip = new TextView(context);
                chip.setText(val);
                chip.setGravity(Gravity.CENTER);
                chip.setTextSize(13);
                chip.setTextColor(selected ? textOnCyan : textPrimary);
                chip.setTypeface(bodyFont != null ? bodyFont : Typeface.DEFAULT, Typeface.BOLD);
                chip.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10));

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(selected ? cyanPrimary : surfaceCard);
                bg.setCornerRadius(dp(context, 12));
                bg.setStroke(dp(context, 1), selected ? cyanPrimary : dividerColor);
                chip.setBackground(bg);

                chip.setOnClickListener(v -> {
                    current[0] = val;
                    onSelect.accept(val);
                    refresh[0].run();
                });

                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i < options.length - 1) p.setMarginEnd(dp(context, 8));
                row.addView(chip, p);
            }
        };
        refresh[0].run();
        return row;
    }

    private static TextView createAnimatedCloseButton(
            Context context, AlertDialog dialog, View content,
            int surfaceCard, int dividerColor, int cyanPrimary,
            int textPrimary, int textOnCyan, Typeface bodyFont
    ) {
        TextView closeButton = new TextView(context);
        closeButton.setText("Done");
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setTextSize(14);
        closeButton.setTextColor(textPrimary);
        closeButton.setTypeface(bodyFont != null ? bodyFont : Typeface.DEFAULT, Typeface.BOLD);
        closeButton.setPadding(0, dp(context, 13), 0, dp(context, 13));

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(surfaceCard);
        closeBg.setCornerRadius(dp(context, 999));
        closeBg.setStroke(dp(context, 1), dividerColor);
        closeButton.setBackground(closeBg);

        final boolean[] isClosing = {false};
        closeButton.setOnClickListener(v -> {
            if (isClosing[0]) return;
            isClosing[0] = true;

            ArgbEvaluator colorEvaluator = new ArgbEvaluator();
            ValueAnimator colorAnim = ValueAnimator.ofFloat(0f, 1f);
            colorAnim.setDuration(120L);
            colorAnim.addUpdateListener(anim -> {
                float fraction = (float) anim.getAnimatedValue();
                closeBg.setColor((int) colorEvaluator.evaluate(fraction, surfaceCard, cyanPrimary));
                closeBg.setStroke(dp(context, 1), (int) colorEvaluator.evaluate(fraction, dividerColor, cyanPrimary));
                closeButton.setTextColor((int) colorEvaluator.evaluate(fraction, textPrimary, textOnCyan));
            });
            colorAnim.start();

            closeButton.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80L)
                    .withEndAction(() -> dismissWithAnimation(dialog, content, null))
                    .start();
        });
        return closeButton;
    }

    private static void applyDialogEntrance(
            AlertDialog dialog, View content, GradientDrawable background, Context context
    ) {
        content.setAlpha(0f);
        content.setScaleX(0.92f);
        content.setScaleY(0.92f);
        content.setTranslationY(dp(context, 12));
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(220L).setInterpolator(new DecelerateInterpolator()).start();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(background);
            dialog.getWindow().setLayout(
                    Math.min(dp(context, 380),
                            context.getResources().getDisplayMetrics().widthPixels - dp(context, 40)),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    /**
     * Smoothly scales down and fades out the modal before calling dialog.dismiss().
     */
    private static void dismissWithAnimation(
            AlertDialog dialog,
            View content,
            Runnable afterDismiss
    ) {
        if (content == null || !dialog.isShowing()) {
            if (afterDismiss != null) {
                afterDismiss.run();
            }
            return;
        }

        content.animate().cancel();
        content.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .translationY(dp(content.getContext(), 10))
                .setDuration(160L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    if (afterDismiss != null) {
                        afterDismiss.run();
                    }
                })
                .start();
    }

    /**
     * Builds a mini visual swatch representing Light, Dark, or System (split).
     */
    private static View createThemePreviewSwatch(
            Context context,
            String themeValue,
            int accentColor
    ) {
        LinearLayout swatch = new LinearLayout(context);
        swatch.setOrientation(LinearLayout.HORIZONTAL);
        swatch.setGravity(Gravity.CENTER);
        swatch.setClipToOutline(true);

        if (ThemeManager.LIGHT.equals(themeValue)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#F8FAFC"));
            bg.setCornerRadius(dp(context, 13));
            bg.setStroke(dp(context, 1), Color.parseColor("#CBD5E1"));
            swatch.setBackground(bg);

            TextView sun = new TextView(context);
            sun.setText("☀");
            sun.setTextSize(20);
            sun.setTextColor(accentColor);
            sun.setGravity(Gravity.CENTER);
            swatch.addView(sun);

        } else if (ThemeManager.DARK.equals(themeValue)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#0F172A"));
            bg.setCornerRadius(dp(context, 13));
            bg.setStroke(dp(context, 1), Color.parseColor("#334155"));
            swatch.setBackground(bg);

            TextView moon = new TextView(context);
            moon.setText("☾");
            moon.setTextSize(20);
            moon.setTextColor(accentColor);
            moon.setGravity(Gravity.CENTER);
            swatch.addView(moon);

        } else {
            // Split Light/Dark preview for System Default
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{
                            Color.parseColor("#F8FAFC"),
                            Color.parseColor("#0F172A")
                    }
            );
            bg.setCornerRadius(dp(context, 13));
            bg.setStroke(dp(context, 1), Color.parseColor("#475569"));
            swatch.setBackground(bg);

            TextView autoIcon = new TextView(context);
            autoIcon.setText("◐");
            autoIcon.setTextSize(20);
            autoIcon.setTextColor(accentColor);
            autoIcon.setGravity(Gravity.CENTER);
            swatch.addView(autoIcon);
        }

        return swatch;
    }

    // =========================================================
    // MODERN SNACKBAR NOTIFICATION
    // =========================================================

    private static void showModernNotification(
            View anchor,
            String message
    ) {

        if (anchor == null || anchor.getContext() == null) {
            return;
        }

        try {
            View rootView = anchor.getRootView();
            Snackbar snackbar = Snackbar.make(
                    rootView != null ? rootView : anchor,
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
                        anchor.getContext(),
                        R.font.font_body
                );
                if (bodyFont != null) {
                    snackText.setTypeface(bodyFont);
                }
            }

            snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE);
            snackbar.show();

        } catch (Exception e) {
            Toast.makeText(
                    anchor.getContext(),
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // =========================================================
    // NAVIGATION HELPER
    // =========================================================

    private static void navigateToDestination(
            View anchor,
            int destinationId,
            String errorMessage
    ) {

        try {

            NavController navController =
                    Navigation.findNavController(anchor);

            // Avoid navigating to the current destination.
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId()
                    == destinationId) {

                return;
            }

            navController.navigate(destinationId);

        } catch (IllegalArgumentException
                 | IllegalStateException exception) {

            Context context = anchor != null ? anchor.getContext() : null;
            if (context != null) {
                String startTarget = null;
                if (destinationId == R.id.navigation_accessibility_settings) {
                    startTarget = "ACCESSIBILITY";
                } else if (destinationId == R.id.navigation_about) {
                    startTarget = "ABOUT";
                }

                if (startTarget != null) {
                    Intent intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainActivity.EXTRA_START_FRAGMENT, startTarget);
                    context.startActivity(intent);
                    if (context instanceof Activity) {
                        ((Activity) context).overridePendingTransition(
                                R.anim.slide_in_right,
                                R.anim.slide_out_left
                        );
                    }
                    return;
                }
            }

            showModernNotification(anchor, errorMessage);
        }
    }

    // =========================================================
    // THEME-AWARE COLOR HELPER
    // =========================================================

    private static int getColor(
            Context context,
            int colorResource
    ) {

        return ContextCompat.getColor(
                context,
                colorResource
        );
    }

    // =========================================================
    // ROUNDED BACKGROUND HELPER
    // =========================================================

    private static GradientDrawable createRoundedBackground(
            int backgroundColor,
            int cornerRadius,
            int strokeColor,
            int strokeWidth
    ) {

        GradientDrawable drawable = new GradientDrawable();

        drawable.setColor(backgroundColor);
        drawable.setCornerRadius(cornerRadius);

        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }

        return drawable;
    }

    // =========================================================
    // DP TO PIXELS
    // =========================================================

    private static int dp(Context context, float value) {

        return (int) (
                value * context.getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }
}
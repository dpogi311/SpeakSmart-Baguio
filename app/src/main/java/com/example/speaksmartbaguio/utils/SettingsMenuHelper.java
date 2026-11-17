
package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.speaksmartbaguio.R;

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

        // Main popup container.
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        container.setPadding(
                dp(context, 10),
                dp(context, 10),
                dp(context, 10),
                dp(context, 10)
        );

        container.setBackground(createRoundedBackground(
                backgroundColor,
                dp(context, 18),
                borderColor,
                dp(context, 1)
        ));

        container.setClipToOutline(true);

        // Calculate popup width.
        int screenWidth = context.getResources()
                .getDisplayMetrics().widthPixels;

        int popupWidth = Math.min(
                dp(context, 320),
                screenWidth - dp(context, 24)
        );

        // Create popup.
        PopupWindow popupWindow = new PopupWindow(
                container,
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
                "♿",
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
                "Listen and practice speech",
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
                "Choose your preferred languages",
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

        // Display popup aligned with gear button.
        if (anchor.isAttachedToWindow()) {
            popupWindow.showAsDropDown(
                    anchor,
                    0,
                    dp(context, 8),
                    Gravity.END
            );
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
        icon.setTextSize(21);
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
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        titleView.setMaxLines(2);

        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(subtitleColor);
        subtitleView.setTextSize(11);
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

            navigateToDestination(
                    anchor,
                    R.id.navigation_accessibility_settings,
                    "Unable to open accessibility settings."
            );

        } else if (menuId == MENU_AUDIO) {

            Toast.makeText(
                    context,
                    "Audio & Pronunciation — Coming Soon",
                    Toast.LENGTH_SHORT
            ).show();

        } else if (menuId == MENU_LANGUAGE) {

            Toast.makeText(
                    context,
                    "Language Preferences — Coming Soon",
                    Toast.LENGTH_SHORT
            ).show();

        } else if (menuId == MENU_ABOUT) {

            navigateToDestination(
                    anchor,
                    R.id.navigation_about,
                    "Unable to open About Speak Smart."
            );
        }
    }

    // =========================================================
    // APPEARANCE SELECTION DIALOG
    // =========================================================



    private static void showAppearanceDialog(Context context) {

        String[] themeOptions = {
                "Light",
                "Dark",
                "System Default"
        };

        String[] themeDescriptions = {
                "A bright and clean appearance.",
                "A comfortable appearance for low-light environments.",
                "Automatically follow your device settings."
        };

        String[] themeIcons = {
                "☀",
                "☾",
                "▣"
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

        // Main dialog content
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dp(context, 24),
                dp(context, 22),
                dp(context, 24),
                dp(context, 12)
        );

        // Dialog background with rounded corners
        GradientDrawable dialogBackground = new GradientDrawable();
        dialogBackground.setColor(
                getColor(context, R.color.app_surface_elevated)
        );
        dialogBackground.setCornerRadius(dp(context, 28));

        // Heading
        TextView heading = new TextView(context);
        heading.setText("Choose your appearance");
        heading.setTextColor(
                getColor(context, R.color.text_primary)
        );
        heading.setTextSize(21);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        content.addView(heading);

        // Subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText("Personalize how Speak Smart Baguio looks.");
        subtitle.setTextColor(
                getColor(context, R.color.text_secondary)
        );
        subtitle.setTextSize(13);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin = dp(context, 7);
        subtitleParams.bottomMargin = dp(context, 22);

        content.addView(subtitle, subtitleParams);

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(content)
                .setNegativeButton("Cancel", (dialogInterface, which) ->
                        dialogInterface.dismiss()
                )
                .create();

        // Theme selection cards
        for (int i = 0; i < themeOptions.length; i++) {

            final int index = i;

            boolean isSelected = (i == selectedIndex);

            LinearLayout option = new LinearLayout(context);
            option.setOrientation(LinearLayout.HORIZONTAL);
            option.setGravity(Gravity.CENTER_VERTICAL);

            option.setPadding(
                    dp(context, 14),
                    dp(context, 15),
                    dp(context, 14),
                    dp(context, 15)
            );

            int cardColor = isSelected
                    ? getColor(context, R.color.app_surface_pressed)
                    : getColor(context, R.color.app_surface);

            int cardBorder = isSelected
                    ? getColor(context, R.color.cyan_primary)
                    : getColor(context, R.color.divider_color);

            // Softer, more rounded card
            GradientDrawable cardBackground =
                    new GradientDrawable();

            cardBackground.setColor(cardColor);
            cardBackground.setCornerRadius(dp(context, 20));
            cardBackground.setStroke(
                    dp(context, isSelected ? 2 : 1),
                    cardBorder
            );

            option.setBackground(cardBackground);
            option.setClickable(true);
            option.setFocusable(true);

            // Icon tile
            TextView icon = new TextView(context);
            icon.setText(themeIcons[i]);
            icon.setTextSize(25);
            icon.setTextColor(
                    getColor(context, R.color.cyan_primary)
            );
            icon.setGravity(Gravity.CENTER);
            icon.setTypeface(Typeface.DEFAULT_BOLD);

            GradientDrawable iconBackground =
                    new GradientDrawable();

            iconBackground.setColor(
                    getColor(context, R.color.app_surface_pressed)
            );
            iconBackground.setCornerRadius(dp(context, 15));

            icon.setBackground(iconBackground);

            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(
                            dp(context, 50),
                            dp(context, 50)
                    );

            iconParams.setMarginEnd(dp(context, 14));

            option.addView(icon, iconParams);

            // Text container
            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(context);
            title.setText(themeOptions[i]);
            title.setTextColor(
                    getColor(context, R.color.text_primary)
            );
            title.setTextSize(15);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

            TextView description = new TextView(context);
            description.setText(themeDescriptions[i]);
            description.setTextColor(
                    getColor(context, R.color.text_secondary)
            );
            description.setTextSize(12);
            description.setMaxLines(2);

            LinearLayout.LayoutParams descriptionParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            descriptionParams.topMargin = dp(context, 5);

            textContainer.addView(title);
            textContainer.addView(description, descriptionParams);

            LinearLayout.LayoutParams textParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );

            option.addView(textContainer, textParams);

            // Selection indicator
            TextView checkmark = new TextView(context);
            checkmark.setText(isSelected ? "✓" : "");
            checkmark.setTextColor(
                    getColor(context, R.color.cyan_primary)
            );
            checkmark.setTextSize(22);
            checkmark.setTypeface(Typeface.DEFAULT_BOLD);
            checkmark.setGravity(Gravity.CENTER);

            option.addView(
                    checkmark,
                    new LinearLayout.LayoutParams(
                            dp(context, 28),
                            dp(context, 40)
                    )
            );

            // Select theme
            option.setOnClickListener(v -> {

                dialog.dismiss();

                ThemeManager.setTheme(
                        context,
                        themeValues[index]
                );
            });

            LinearLayout.LayoutParams optionParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            optionParams.bottomMargin = dp(context, 12);

            content.addView(option, optionParams);
        }

        dialog.show();

        // Apply rounded background to the actual dialog window
        if (dialog.getWindow() != null) {

            dialog.getWindow().setBackgroundDrawable(
                    dialogBackground
            );

            dialog.getWindow().setLayout(
                    Math.min(
                            dp(context, 390),
                            context.getResources()
                                    .getDisplayMetrics().widthPixels
                                    - dp(context, 40)
                    ),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // Style Cancel button
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(
                        getColor(context, R.color.cyan_primary)
                );
    }



    // =========================================================
    // NAVIGATION HELPER
    // =========================================================

    private static void navigateToDestination(
            View anchor,
            int destinationId,
            String errorMessage
    ) {

        Context context = anchor.getContext();

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

            Toast.makeText(
                    context,
                    errorMessage,
                    Toast.LENGTH_SHORT
            ).show();
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

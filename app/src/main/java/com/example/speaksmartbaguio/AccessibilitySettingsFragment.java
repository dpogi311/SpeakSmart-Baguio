package com.example.speaksmartbaguio;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.speaksmartbaguio.databinding.FragmentAccessibilitySettingsBinding;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class AccessibilitySettingsFragment extends Fragment {

    private FragmentAccessibilitySettingsBinding binding;

    /*
     * Prevents programmatic control updates, such as Reset,
     * from triggering the normal setting-change listeners.
     */
    private boolean updatingControls = false;
    private boolean isNavigatingBack = false;

    public AccessibilitySettingsFragment() {
        // Required empty public constructor.
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentAccessibilitySettingsBinding.inflate(
                inflater,
                container,
                false
        );

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        // Animate the page and its sections when it opens.
        animatePageEntrance(view);

        // Set up the Back button.
        setupBackButton();

        // Load saved values before registering listeners.
        loadSavedSettings();

        setupFontSizeOptions();
        setupHighContrastSwitch();
        setupSimplifiedLayoutSwitch();
        setupResetButton();

        applySettingsToCurrentScreen();
    }

    // ---------------------------------------------------------
    // STAGGERED PAGE ENTRANCE ANIMATION
    // ---------------------------------------------------------

    private void animatePageEntrance(@NonNull View view) {

        float offset = 20f * getResources().getDisplayMetrics().density;

        view.setAlpha(0f);
        view.animate()
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        ViewGroup contentContainer = resolveContentContainer(view);
        if (contentContainer == null) {
            return;
        }

        int childCount = contentContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = contentContainer.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }

            child.setAlpha(0f);
            child.setTranslationY(offset);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(40L + (i * 45L))
                    .setDuration(320L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    @Nullable
    private ViewGroup resolveContentContainer(@NonNull View root) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup rootGroup = (ViewGroup) root;
        if (rootGroup.getChildCount() == 1 && rootGroup.getChildAt(0) instanceof ViewGroup) {
            return (ViewGroup) rootGroup.getChildAt(0);
        }
        return rootGroup;
    }

    private void animateControlPulse(@Nullable View control) {
        if (control == null) {
            return;
        }
        control.animate().cancel();
        control.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(90L)
                .withEndAction(() -> control.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120L)
                        .setInterpolator(new OvershootInterpolator(1.3f))
                        .start())
                .start();
    }

    @Override
    public void onResume() {
        super.onResume();
        isNavigatingBack = false;

        /*
         * Reapply saved settings when returning to this screen.
         * This also updates controls if settings changed elsewhere.
         */
        if (binding != null) {
            loadSavedSettings();
            applySettingsToCurrentScreen();
        }
    }

    // ---------------------------------------------------------
    // BACK BUTTON
    // ---------------------------------------------------------

    private void setupBackButton() {

        binding.buttonAccessibilityBack.setOnClickListener(v -> {

            if (!isAdded() || isNavigatingBack) {
                return;
            }
            isNavigatingBack = true;

            v.animate().cancel();
            v.animate()
                    .scaleX(0.90f)
                    .scaleY(0.90f)
                    .setDuration(75L)
                    .withEndAction(() -> {
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100L)
                                .setInterpolator(new OvershootInterpolator(1.3f))
                                .start();

                        if (!isAdded()) {
                            return;
                        }

                        NavController navController =
                                Navigation.findNavController(v);

                        /*
                         * Return to the previous navigation destination.
                         * If navigation cannot go back, use normal Android Back.
                         */
                        if (!navController.navigateUp()) {
                            requireActivity()
                                    .getOnBackPressedDispatcher()
                                    .onBackPressed();
                        }
                    })
                    .start();
        });
    }

    // ---------------------------------------------------------
    // LOAD SAVED SETTINGS
    // ---------------------------------------------------------

    private void loadSavedSettings() {

        if (binding == null) {
            return;
        }

        float fontScale =
                AccessibilityPreferences.getFontScale(
                        requireContext()
                );

        updatingControls = true;

        try {

            if (fontScale ==
                    AccessibilityPreferences.FONT_SCALE_LARGE) {

                binding.fontSizeLarge.setChecked(true);

            } else if (
                    fontScale ==
                            AccessibilityPreferences.FONT_SCALE_EXTRA_LARGE
            ) {

                binding.fontSizeExtraLarge.setChecked(true);

            } else {

                binding.fontSizeStandard.setChecked(true);
            }

            boolean highContrast =
                    AccessibilityPreferences.isHighContrastEnabled(
                            requireContext()
                    );
            binding.switchHighContrast.setChecked(highContrast);
            updateSwitchAppearance(binding.switchHighContrast);

            boolean simplified =
                    AccessibilityPreferences.isSimplifiedLayoutEnabled(
                            requireContext()
                    );
            binding.switchSimplifiedLayout.setChecked(simplified);
            updateSwitchAppearance(binding.switchSimplifiedLayout);

        } finally {
            updatingControls = false;
        }
    }

    // ---------------------------------------------------------
    // SWITCH VISUAL STATE (CLEAR ON vs OFF COLORS)
    // ---------------------------------------------------------

    private void updateSwitchAppearance(@Nullable SwitchMaterial switchView) {

        if (switchView == null || !isAdded()) {
            return;
        }

        int cyanPrimary = ContextCompat.getColor(
                requireContext(),
                R.color.cyan_primary
        );

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };

        int[] thumbColors = new int[]{
                cyanPrimary,
                Color.parseColor("#CBD5E1")
        };

        int[] trackColors = new int[]{
                Color.argb(120, Color.red(cyanPrimary), Color.green(cyanPrimary), Color.blue(cyanPrimary)),
                Color.parseColor("#475569")
        };

        switchView.setThumbTintList(new ColorStateList(states, thumbColors));
        switchView.setTrackTintList(new ColorStateList(states, trackColors));
    }

    // ---------------------------------------------------------
    // FONT SIZE OPTIONS
    // ---------------------------------------------------------

    private void setupFontSizeOptions() {

        binding.fontSizeRadioGroup.setOnCheckedChangeListener(
                (RadioGroup group, int checkedId) -> {

                    if (updatingControls || checkedId == -1) {
                        return;
                    }

                    float selectedScale;
                    View selectedRadio;

                    if (checkedId == R.id.fontSizeLarge) {

                        selectedScale =
                                AccessibilityPreferences.FONT_SCALE_LARGE;
                        selectedRadio = binding.fontSizeLarge;

                    } else if (
                            checkedId == R.id.fontSizeExtraLarge
                    ) {

                        selectedScale =
                                AccessibilityPreferences.FONT_SCALE_EXTRA_LARGE;
                        selectedRadio = binding.fontSizeExtraLarge;

                    } else {

                        selectedScale =
                                AccessibilityPreferences.FONT_SCALE_STANDARD;
                        selectedRadio = binding.fontSizeStandard;
                    }

                    animateControlPulse(selectedRadio);

                    AccessibilityPreferences.setFontScale(
                            requireContext(),
                            selectedScale
                    );

                    applySettingsToCurrentScreen();

                    showMessage("Text size updated.");
                }
        );
    }

    // ---------------------------------------------------------
    // HIGH CONTRAST
    // ---------------------------------------------------------

    private void setupHighContrastSwitch() {

        binding.switchHighContrast.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    if (updatingControls) {
                        return;
                    }

                    animateControlPulse(buttonView);

                    AccessibilityPreferences.setHighContrastEnabled(
                            requireContext(),
                            isChecked
                    );

                    applySettingsToCurrentScreen();

                    showMessage(
                            isChecked
                                    ? "High contrast enabled."
                                    : "High contrast disabled."
                    );
                }
        );
    }

    // ---------------------------------------------------------
    // SIMPLIFIED LAYOUT
    // ---------------------------------------------------------

    private void setupSimplifiedLayoutSwitch() {

        binding.switchSimplifiedLayout.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    if (updatingControls) {
                        return;
                    }

                    animateControlPulse(buttonView);

                    AccessibilityPreferences.setSimplifiedLayoutEnabled(
                            requireContext(),
                            isChecked
                    );

                    applySettingsToCurrentScreen();

                    showMessage(
                            isChecked
                                    ? "Simplified layout enabled."
                                    : "Simplified layout disabled."
                    );
                }
        );
    }

    // ---------------------------------------------------------
    // RESET SETTINGS
    // ---------------------------------------------------------

    private void setupResetButton() {

        binding.buttonResetAccessibility.setOnClickListener(v -> {

            if (binding == null) {
                return;
            }

            v.animate().cancel();
            v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(75L)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(110L)
                            .setInterpolator(new OvershootInterpolator(1.3f))
                            .start())
                    .start();

            // Restore all saved accessibility preferences.
            AccessibilityPreferences.reset(requireContext());

            /*
             * Update the controls without triggering their
             * normal listeners or showing additional messages.
             */
            updatingControls = true;

            try {

                binding.fontSizeStandard.setChecked(true);
                binding.switchHighContrast.setChecked(false);
                binding.switchSimplifiedLayout.setChecked(false);

            } finally {
                updatingControls = false;
            }

            applySettingsToCurrentScreen();

            showMessage("Accessibility settings reset.");
        });
    }

    // ---------------------------------------------------------
    // APPLY SETTINGS
    // ---------------------------------------------------------

    private void applySettingsToCurrentScreen() {

        if (binding == null || !isAdded()) {
            return;
        }

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                binding.getRoot()
        );

        updateSwitchAppearance(binding.switchHighContrast);
        updateSwitchAppearance(binding.switchSimplifiedLayout);
    }

    // ---------------------------------------------------------
    // USER FEEDBACK (MODERN DARK-GLASS SNACKBAR)
    // ---------------------------------------------------------

    private void showMessage(@NonNull String message) {

        if (binding == null || !isAdded()) {
            return;
        }

        Snackbar snackbar = Snackbar.make(
                binding.getRoot(),
                message,
                Snackbar.LENGTH_SHORT
        );

        snackbar.setBackgroundTint(
                Color.rgb(25, 35, 40)
        );

        snackbar.setTextColor(
                Color.WHITE
        );

        TextView snackText = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text
        );
        if (snackText != null) {
            Typeface bodyFont = ResourcesCompat.getFont(
                    requireContext(),
                    R.font.font_body
            );
            if (bodyFont != null) {
                snackText.setTypeface(bodyFont);
            }
        }

        snackbar.setAnimationMode(
                Snackbar.ANIMATION_MODE_SLIDE
        );

        snackbar.show();
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    @Override
    public void onDestroyView() {

        if (binding != null) {
            // Cancel any running entrance animations.
            View root = binding.getRoot();
            root.animate().cancel();

            ViewGroup contentContainer = resolveContentContainer(root);
            if (contentContainer != null) {
                for (int i = 0; i < contentContainer.getChildCount(); i++) {
                    View child = contentContainer.getChildAt(i);
                    if (child != null) {
                        child.animate().cancel();
                    }
                }
            }
        }

        super.onDestroyView();

        binding = null;
    }
}
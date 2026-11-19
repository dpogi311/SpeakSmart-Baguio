package com.example.speaksmartbaguio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.speaksmartbaguio.databinding.FragmentAboutSpeakSmartBinding;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;

public class AboutSpeakSmartFragment extends Fragment {

    private FragmentAboutSpeakSmartBinding binding;
    private boolean isNavigatingBack = false;

    public AboutSpeakSmartFragment() {
        // Required empty public constructor.
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentAboutSpeakSmartBinding.inflate(
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

        setupBackButton();

        applyAccessibilitySettings();

        // Animate the page and its sections when it opens.
        animatePageEntrance(view);
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

    @Override
    public void onResume() {
        super.onResume();
        isNavigatingBack = false;

        // Respect saved accessibility preferences when returning.
        if (binding != null && isAdded()) {
            applyAccessibilitySettings();
        }
    }

    // ---------------------------------------------------------
    // BACK BUTTON
    // ---------------------------------------------------------

    private void setupBackButton() {

        binding.buttonAboutBack.setOnClickListener(v -> {

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
                         * If there is no previous destination, use the
                         * activity's standard Android Back behavior.
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
    // ACCESSIBILITY SETTINGS
    // ---------------------------------------------------------

    private void applyAccessibilitySettings() {

        if (binding == null || !isAdded()) {
            return;
        }

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                binding.getRoot()
        );
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
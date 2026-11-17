
package com.example.speaksmartbaguio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.speaksmartbaguio.databinding.FragmentAboutSpeakSmartBinding;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;

public class AboutSpeakSmartFragment extends Fragment {

    private FragmentAboutSpeakSmartBinding binding;

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

        // Animate the page when it opens.
        animatePageEntrance(view);
    }

    // ---------------------------------------------------------
    // PAGE ENTRANCE ANIMATION
    // ---------------------------------------------------------

    private void animatePageEntrance(@NonNull View view) {

        // Start slightly lower and transparent.
        view.setAlpha(0f);

        float startTranslation =
                24f * getResources().getDisplayMetrics().density;

        view.setTranslationY(startTranslation);

        // Smooth fade-in and upward movement.
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    public void onResume() {
        super.onResume();

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
            // Cancel any running entrance animation.
            binding.getRoot().animate().cancel();
        }

        super.onDestroyView();

        binding = null;
    }
}

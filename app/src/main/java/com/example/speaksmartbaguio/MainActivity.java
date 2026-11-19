package com.example.speaksmartbaguio;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.speaksmartbaguio.databinding.ActivityMainBinding;
import com.example.speaksmartbaguio.utils.SyncManager;
import com.example.speaksmartbaguio.utils.ThemeManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // =====================================================
    // INTENT EXTRA KEYS
    // =====================================================

    public static final String EXTRA_START_FRAGMENT =
            "START_FRAGMENT";

    public static final String EXTRA_DICTIONARY_SEARCH =
            "DICTIONARY_SEARCH_QUERY";

    public static final String EXTRA_PHRASEBOOK_SEARCH =
            "PHRASEBOOK_SEARCH_QUERY";


    private ActivityMainBinding binding;
    private NavController navController;
    private boolean launchedStandaloneSettings = false;


    // =====================================================
    // ON CREATE
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Apply saved theme before creating the activity.
        ThemeManager.applySavedTheme(this);

        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SyncManager.sync(getApplicationContext());


        // =================================================
        // CONNECT NAVIGATION CONTROLLER
        // =================================================

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(
                                R.id.nav_host_fragment_activity_main
                        );

        if (navHostFragment == null) {
            return;
        }

        navController = navHostFragment.getNavController();

        BottomNavigationView navView = binding.navView;


        // =================================================
        // BOTTOM NAVIGATION
        // =================================================

        navView.setOnItemSelectedListener(item -> {

            int destinationId = item.getItemId();

            animateNavItemPop(navView, destinationId);

            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId()
                    == destinationId) {

                return true;
            }

            int currentDestination =
                    navController.getCurrentDestination() != null
                            ? navController.getCurrentDestination().getId()
                            : R.id.navigation_translator;

            int currentOrder = getModuleOrder(currentDestination);
            int targetOrder = getModuleOrder(destinationId);

            boolean movingForward = targetOrder >= currentOrder;

            NavOptions navOptions = createDirectionalNavOptions(movingForward);

            try {

                navController.navigate(
                        destinationId,
                        null,
                        navOptions
                );

                return true;

            } catch (IllegalArgumentException e) {

                e.printStackTrace();
                return false;
            }
        });


        // =================================================
        // PREVENT UNNECESSARY RESELECTION
        // =================================================

        navView.setOnItemReselectedListener(item -> {
            animateNavItemPop(navView, item.getItemId());
        });


        // =================================================
        // DESTINATION CHANGES
        // =================================================

        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {

                    int destinationId = destination.getId();

                    // If opened from SettingsActivity directly to Accessibility/About
                    // and the user presses the in-screen back arrow back to Translator,
                    // close MainActivity immediately to return to SettingsActivity.
                    if (launchedStandaloneSettings
                            && destinationId == R.id.navigation_translator) {
                        finish();
                        return;
                    }

                    boolean hideBottomNavigation =
                            destinationId == R.id.navigation_accessibility_settings
                                    || destinationId == R.id.navigation_about;

                    animateBottomNavigation(navView, !hideBottomNavigation);

                    // Phrase List belongs to Phrasebook.
                    if (destinationId == R.id.phraseListFragment) {
                        destinationId = R.id.navigation_phrasebook;
                    }

                    // Keep the correct navigation item selected.
                    if (navView.getMenu().findItem(destinationId) != null) {

                        navView.getMenu()
                                .findItem(destinationId)
                                .setChecked(true);
                    }
                }
        );


        // =================================================
        // BACK BUTTON BEHAVIOR
        // Top-level bottom nav tabs return to HomeActivity;
        // standalone settings screens return to SettingsActivity;
        // in-module sub-pages pop back to their parent tab.
        // =================================================

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (launchedStandaloneSettings) {
                            finish();
                            return;
                        }

                        if (navController != null
                                && navController.getCurrentDestination() != null) {

                            int currentId =
                                    navController.getCurrentDestination().getId();

                            boolean isSubDestination =
                                    currentId == R.id.navigation_accessibility_settings
                                            || currentId == R.id.navigation_about
                                            || currentId == R.id.phraseListFragment;

                            if (isSubDestination && navController.navigateUp()) {
                                return;
                            }
                        }

                        finish();
                    }
                }
        );


        // =================================================
        // HANDLE NAVIGATION FROM HOME / SETTINGS ACTIVITY
        // =================================================

        handleNavigationIntent(getIntent(), navView);
    }


    // =====================================================
    // UI ANIMATION HELPERS
    // =====================================================

    private void animateBottomNavigation(
            BottomNavigationView navView,
            boolean show
    ) {

        if (navView == null) {
            return;
        }

        if (show) {
            if (navView.getVisibility() == View.VISIBLE && navView.getAlpha() == 1f) {
                return;
            }

            navView.animate().cancel();
            if (navView.getVisibility() != View.VISIBLE) {
                navView.setAlpha(0f);
                navView.setTranslationY(
                        navView.getHeight() > 0 ? navView.getHeight() : 120f
                );
                navView.setVisibility(View.VISIBLE);
            }

            navView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            if (navView.getVisibility() == View.GONE) {
                return;
            }

            float hideOffset = navView.getHeight() > 0 ? navView.getHeight() : 120f;
            navView.animate().cancel();
            navView.animate()
                    .alpha(0f)
                    .translationY(hideOffset)
                    .setDuration(180L)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> navView.setVisibility(View.GONE))
                    .start();
        }
    }


    private void animateNavItemPop(
            BottomNavigationView navView,
            int itemId
    ) {

        if (navView == null) {
            return;
        }

        View itemView = navView.findViewById(itemId);
        if (itemView == null) {
            return;
        }

        itemView.animate().cancel();
        itemView.setScaleX(0.90f);
        itemView.setScaleY(0.90f);
        itemView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();
    }


    // =====================================================
    // NAVIGATION OPTIONS
    // =====================================================

    private NavOptions createDirectionalNavOptions(boolean movingForward) {

        int enterAnimation = movingForward
                ? R.anim.slide_in_right
                : R.anim.slide_in_left;

        int exitAnimation = movingForward
                ? R.anim.slide_out_left
                : R.anim.slide_out_right;

        int popEnterAnimation = movingForward
                ? R.anim.slide_in_left
                : R.anim.slide_in_right;

        int popExitAnimation = movingForward
                ? R.anim.slide_out_right
                : R.anim.slide_out_left;

        return new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(
                        R.id.navigation_translator,
                        false
                )
                .setEnterAnim(enterAnimation)
                .setExitAnim(exitAnimation)
                .setPopEnterAnim(popEnterAnimation)
                .setPopExitAnim(popExitAnimation)
                .build();
    }


    // =====================================================
    // GET MODULE ORDER
    // =====================================================

    private int getModuleOrder(int destinationId) {

        if (destinationId == R.id.navigation_translator) {
            return 0;
        }

        if (destinationId == R.id.navigation_dictionary) {
            return 1;
        }

        if (destinationId == R.id.navigation_phrasebook
                || destinationId == R.id.phraseListFragment) {
            return 2;
        }

        if (destinationId == R.id.navigation_favorites) {
            return 3;
        }

        return 0;
    }


    // =====================================================
    // HANDLE HOME / SETTINGS ACTIVITY NAVIGATION
    // =====================================================

    private void handleNavigationIntent(
            Intent intent,
            BottomNavigationView navView
    ) {

        if (intent == null
                || navView == null
                || navController == null) {

            return;
        }

        String startFragment = intent.getStringExtra(
                EXTRA_START_FRAGMENT
        );

        if (startFragment == null) {
            return;
        }

        int destinationId;

        Bundle arguments = new Bundle();

        switch (startFragment) {

            // =============================================
            // DICTIONARY
            // =============================================

            case "DICTIONARY":

                destinationId = R.id.navigation_dictionary;

                String dictionaryQuery =
                        intent.getStringExtra(EXTRA_DICTIONARY_SEARCH);

                if (dictionaryQuery != null
                        && !dictionaryQuery.trim().isEmpty()) {

                    arguments.putString(
                            EXTRA_DICTIONARY_SEARCH,
                            dictionaryQuery.trim()
                    );
                }

                break;


            // =============================================
            // PHRASEBOOK
            // =============================================

            case "PHRASEBOOK":

                destinationId = R.id.navigation_phrasebook;

                String phrasebookQuery =
                        intent.getStringExtra(EXTRA_PHRASEBOOK_SEARCH);

                if (phrasebookQuery != null
                        && !phrasebookQuery.trim().isEmpty()) {

                    arguments.putString(
                            EXTRA_PHRASEBOOK_SEARCH,
                            phrasebookQuery.trim()
                    );
                }

                break;


            // =============================================
            // TRANSLATOR
            // =============================================

            case "TRANSLATOR":

                destinationId = R.id.navigation_translator;
                break;


            // =============================================
            // FAVORITES
            // =============================================

            case "FAVORITES":

                destinationId = R.id.navigation_favorites;
                break;


            // =============================================
            // ACCESSIBILITY SETTINGS
            // =============================================

            case "ACCESSIBILITY":

                destinationId = R.id.navigation_accessibility_settings;
                break;


            // =============================================
            // ABOUT SPEAK SMART
            // =============================================

            case "ABOUT":

                destinationId = R.id.navigation_about;
                break;


            default:
                return;
        }


        // =================================================
        // DETERMINE ANIMATION DIRECTION
        // =================================================

        boolean isStandaloneSettingsScreen =
                destinationId == R.id.navigation_accessibility_settings
                        || destinationId == R.id.navigation_about;

        launchedStandaloneSettings = isStandaloneSettingsScreen;

        NavOptions navOptions;
        if (isStandaloneSettingsScreen) {
            navOptions = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(
                            R.id.navigation_translator,
                            true
                    )
                    .setEnterAnim(R.anim.slide_in_right)
                    .setExitAnim(R.anim.slide_out_left)
                    .build();
        } else {
            int currentDestination =
                    navController.getCurrentDestination() != null
                            ? navController.getCurrentDestination().getId()
                            : R.id.navigation_translator;

            boolean movingForward =
                    getModuleOrder(destinationId)
                            >= getModuleOrder(currentDestination);

            navOptions = createDirectionalNavOptions(movingForward);
        }


        // =================================================
        // NAVIGATE AND PASS SEARCH ARGUMENTS
        // =================================================

        try {

            navController.navigate(
                    destinationId,
                    arguments,
                    navOptions
            );

            // Update bottom navigation selection.
            if (navView.getMenu().findItem(destinationId) != null) {

                navView.getMenu()
                        .findItem(destinationId)
                        .setChecked(true);
            }

            // Remove consumed intent extras.
            intent.removeExtra(EXTRA_START_FRAGMENT);
            intent.removeExtra(EXTRA_DICTIONARY_SEARCH);
            intent.removeExtra(EXTRA_PHRASEBOOK_SEARCH);

        } catch (IllegalArgumentException e) {

            e.printStackTrace();
        }
    }


    // =====================================================
    // HANDLE NEW INTENTS
    // =====================================================

    @Override
    protected void onNewIntent(Intent intent) {

        super.onNewIntent(intent);

        setIntent(intent);

        if (binding != null && navController != null) {

            handleNavigationIntent(
                    intent,
                    binding.navView
            );
        }
    }


    // =====================================================
    // ACTIVITY EXIT TRANSITION
    // =====================================================

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }


    // =====================================================
    // CLEANUP
    // =====================================================

    @Override
    protected void onDestroy() {

        binding = null;
        navController = null;

        super.onDestroy();
    }
}
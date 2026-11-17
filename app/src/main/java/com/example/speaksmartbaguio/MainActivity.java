
package com.example.speaksmartbaguio;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

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

            int enterAnimation;
            int exitAnimation;

            if (movingForward) {

                enterAnimation = R.anim.slide_in_right;
                exitAnimation = R.anim.slide_out_left;

            } else {

                enterAnimation = R.anim.slide_in_left;
                exitAnimation = R.anim.slide_out_right;
            }

            NavOptions navOptions = createNavOptions(
                    enterAnimation,
                    exitAnimation
            );

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
            // Intentionally empty.
        });


        // =================================================
        // DESTINATION CHANGES
        // =================================================

        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {

                    int destinationId = destination.getId();

                    boolean hideBottomNavigation =
                            destinationId == R.id.navigation_accessibility_settings
                                    || destinationId == R.id.navigation_about;

                    navView.setVisibility(
                            hideBottomNavigation
                                    ? View.GONE
                                    : View.VISIBLE
                    );

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
        // HANDLE NAVIGATION FROM HOME ACTIVITY
        // =================================================

        handleNavigationIntent(getIntent(), navView);
    }


    // =====================================================
    // NAVIGATION OPTIONS
    // =====================================================

    private NavOptions createNavOptions(
            int enterAnimation,
            int exitAnimation
    ) {

        return new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(
                        R.id.navigation_translator,
                        false
                )
                .setEnterAnim(enterAnimation)
                .setExitAnim(exitAnimation)
                .setPopEnterAnim(enterAnimation)
                .setPopExitAnim(exitAnimation)
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
    // HANDLE HOME ACTIVITY NAVIGATION
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


            default:
                return;
        }


        // =================================================
        // DETERMINE ANIMATION DIRECTION
        // =================================================

        int currentDestination =
                navController.getCurrentDestination() != null
                        ? navController.getCurrentDestination().getId()
                        : R.id.navigation_translator;

        boolean movingForward =
                getModuleOrder(destinationId)
                        >= getModuleOrder(currentDestination);

        int enterAnimation = movingForward
                ? R.anim.slide_in_right
                : R.anim.slide_in_left;

        int exitAnimation = movingForward
                ? R.anim.slide_out_left
                : R.anim.slide_out_right;

        NavOptions navOptions = createNavOptions(
                enterAnimation,
                exitAnimation
        );


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
    // CLEANUP
    // =====================================================

    @Override
    protected void onDestroy() {

        binding = null;
        navController = null;

        super.onDestroy();
    }
}

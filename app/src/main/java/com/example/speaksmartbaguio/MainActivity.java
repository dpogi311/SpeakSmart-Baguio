
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

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_START_FRAGMENT =
            "START_FRAGMENT";

    public static final String EXTRA_DICTIONARY_SEARCH =
            "DICTIONARY_SEARCH_QUERY";

    public static final String EXTRA_PHRASEBOOK_SEARCH =
            "PHRASEBOOK_SEARCH_QUERY";

    private ActivityMainBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SyncManager.sync(getApplicationContext());

        // ==========================================
        // CONNECT NAVIGATION CONTROLLER
        // ==========================================

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

        // ==========================================
        // CUSTOM BOTTOM NAVIGATION
        // WITH SLIDE + FADE ANIMATION
        // ==========================================

        navView.setOnItemSelectedListener(item -> {

            int destinationId = item.getItemId();

            // Do nothing if already on this destination.
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId()
                    == destinationId) {
                return true;
            }

            int currentOrder = getModuleOrder(
                    navController.getCurrentDestination() != null
                            ? navController.getCurrentDestination().getId()
                            : R.id.navigation_translator
            );

            int targetOrder = getModuleOrder(destinationId);

            // Determine animation direction.
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

            // ======================================
            // NAVIGATION OPTIONS
            // ======================================

            NavOptions navOptions = new NavOptions.Builder()
                    .setLaunchSingleTop(true)

                    // Return to the main navigation destination
                    // before opening another module.
                    .setPopUpTo(
                            R.id.navigation_translator,
                            false
                    )

                    .setEnterAnim(enterAnimation)
                    .setExitAnim(exitAnimation)
                    .setPopEnterAnim(enterAnimation)
                    .setPopExitAnim(exitAnimation)

                    .build();

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

        // Prevent unnecessary animation when the
        // currently selected navigation item is tapped.
        navView.setOnItemReselectedListener(item -> {
            // Intentionally empty.
        });

        // ==========================================
        // KEEP NAVIGATION ITEM SELECTED
        // AND CONTROL BOTTOM NAVIGATION VISIBILITY
        // ==========================================

        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {

                    int destinationId = destination.getId();

                    // Hide bottom navigation on these screens.
                    boolean hideBottomNavigation =
                            destinationId == R.id.navigation_accessibility_settings
                                    || destinationId == R.id.navigation_about;

                    if (hideBottomNavigation) {
                        navView.setVisibility(View.GONE);
                    } else {
                        navView.setVisibility(View.VISIBLE);
                    }

                    // Phrase List belongs to Phrasebook.
                    if (destinationId == R.id.phraseListFragment) {
                        destinationId = R.id.navigation_phrasebook;
                    }

                    // Keep the correct main navigation item selected.
                    if (navView.getMenu().findItem(destinationId) != null) {
                        navView.getMenu()
                                .findItem(destinationId)
                                .setChecked(true);
                    }
                }
        );

        // ==========================================
        // HANDLE HOME ACTIVITY NAVIGATION
        // ==========================================

        handleNavigationIntent(getIntent(), navView);
    }

    // ==============================================
    // GET MODULE ORDER
    // ==============================================

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

    // ==============================================
    // HANDLE HOME ACTIVITY NAVIGATION
    // ==============================================

    private void handleNavigationIntent(
            Intent intent,
            BottomNavigationView navView
    ) {

        if (intent == null || navView == null) {
            return;
        }

        String startFragment = intent.getStringExtra(
                EXTRA_START_FRAGMENT
        );

        if (startFragment == null) {
            return;
        }

        int destinationId;

        switch (startFragment) {

            case "DICTIONARY":
                destinationId = R.id.navigation_dictionary;
                break;

            case "PHRASEBOOK":
                destinationId = R.id.navigation_phrasebook;
                break;

            case "TRANSLATOR":
                destinationId = R.id.navigation_translator;
                break;

            case "FAVORITES":
                destinationId = R.id.navigation_favorites;
                break;

            default:
                return;
        }

        navView.setSelectedItemId(destinationId);
    }

    // ==============================================
    // HANDLE NEW INTENTS
    // ==============================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        if (binding != null) {
            handleNavigationIntent(intent, binding.navView);
        }
    }

    // ==============================================
    // CLEANUP
    // ==============================================

    @Override
    protected void onDestroy() {

        binding = null;
        navController = null;

        super.onDestroy();
    }
}

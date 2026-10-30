
package com.example.speaksmartbaguio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.speaksmartbaguio.databinding.ActivityHomeBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.repository.PhraseRepository;
import com.example.speaksmartbaguio.utils.NetworkUtil;
import com.example.speaksmartbaguio.utils.SyncManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;

    private PhraseRepository phraseRepository;
    private DictionaryRepository dictionaryRepository;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean hasResumedOnce = false;
    private boolean lastKnownOnline = false;
    private boolean networkCallbackRegistered = false;

    // Exact Ilokano text displayed in the daily card.
    private String dailyPhraseSearchTerm = "";

    // Source module of the selected daily content.
    // Possible values: PHRASEBOOK or DICTIONARY.
    private String dailyPhraseDestination = "PHRASEBOOK";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        phraseRepository = new PhraseRepository(getApplicationContext());
        dictionaryRepository = new DictionaryRepository(getApplicationContext());

        setupNavigation();
        setupDailyPhraseCard();
        setupSyncIndicator();

        loadDailyPhrase();

        lastKnownOnline = NetworkUtil.isOnline(this);
        refreshConnectionStatus();

        if (lastKnownOnline) {
            startSync();
        }
    }


    // =========================================================
    // HOME NAVIGATION
    // =========================================================

    private void setupNavigation() {

        binding.cardTranslator.setOnClickListener(v ->
                openLearningTool("TRANSLATOR"));

        binding.cardDictionary.setOnClickListener(v ->
                openLearningTool("DICTIONARY"));

        binding.cardPhrasebook.setOnClickListener(v ->
                openLearningTool("PHRASEBOOK"));
    }


    private void openLearningTool(String fragmentName) {

        Intent intent = new Intent(
                HomeActivity.this,
                MainActivity.class
        );

        intent.putExtra(
                MainActivity.EXTRA_START_FRAGMENT,
                fragmentName
        );

        startActivity(intent);
    }


    private void openDailyPhrase() {

        if (dailyPhraseSearchTerm == null
                || dailyPhraseSearchTerm.trim().isEmpty()) {

            // No selected record exists yet.
            openLearningTool("PHRASEBOOK");
            return;
        }

        Intent intent = new Intent(
                HomeActivity.this,
                MainActivity.class
        );

        // Open the module that supplied this exact record.
        intent.putExtra(
                MainActivity.EXTRA_START_FRAGMENT,
                dailyPhraseDestination
        );

        if ("DICTIONARY".equals(dailyPhraseDestination)) {

            intent.putExtra(
                    MainActivity.EXTRA_DICTIONARY_SEARCH,
                    dailyPhraseSearchTerm
            );

        } else {

            intent.putExtra(
                    MainActivity.EXTRA_PHRASEBOOK_SEARCH,
                    dailyPhraseSearchTerm
            );
        }

        startActivity(intent);
    }


    // =========================================================
    // DAILY PHRASE CARD
    // =========================================================

    private void setupDailyPhraseCard() {

        binding.homeDailyPhraseCard.setOnClickListener(v ->
                openDailyPhrase()
        );

        binding.homeDailyPhraseCard.setClickable(true);
        binding.homeDailyPhraseCard.setFocusable(true);
    }


    /**
     * Loads saved content from BOTH Phrasebook and Dictionary.
     *
     * Each record keeps its original source.
     * The daily card will never guess the destination
     * based on whether the content is a word or sentence.
     */
    private void loadDailyPhrase() {

        if (binding == null
                || phraseRepository == null
                || dictionaryRepository == null) {
            return;
        }

        final List<Phrase> phraseResults = new ArrayList<>();
        final List<Word> wordResults = new ArrayList<>();

        final boolean[] phraseLoaded = {false};
        final boolean[] dictionaryLoaded = {false};

        final Object lock = new Object();


        // Load Phrasebook records.
        phraseRepository.getAllPhrases(result -> {

            List<Phrase> phrases = EntityMapper.toPhraseList(result);

            synchronized (lock) {

                phraseResults.clear();

                if (phrases != null) {
                    phraseResults.addAll(phrases);
                }

                phraseLoaded[0] = true;
            }

            displayWhenBothSourcesAreLoaded(
                    lock,
                    phraseResults,
                    wordResults,
                    phraseLoaded,
                    dictionaryLoaded
            );
        });


        // Load Dictionary records.
        dictionaryRepository.getAllWords(result -> {

            List<Word> words = EntityMapper.toWordList(result);

            synchronized (lock) {

                wordResults.clear();

                if (words != null) {
                    wordResults.addAll(words);
                }

                dictionaryLoaded[0] = true;
            }

            displayWhenBothSourcesAreLoaded(
                    lock,
                    phraseResults,
                    wordResults,
                    phraseLoaded,
                    dictionaryLoaded
            );
        });
    }


    /**
     * Waits until both repositories finish loading.
     * Then safely sends copies of their results to the UI.
     */
    private void displayWhenBothSourcesAreLoaded(
            Object lock,
            List<Phrase> phrases,
            List<Word> words,
            boolean[] phraseLoaded,
            boolean[] dictionaryLoaded
    ) {

        List<Phrase> phraseSnapshot;
        List<Word> wordSnapshot;

        synchronized (lock) {

            if (!phraseLoaded[0] || !dictionaryLoaded[0]) {
                return;
            }

            phraseSnapshot = new ArrayList<>(phrases);
            wordSnapshot = new ArrayList<>(words);
        }

        runOnUiThread(() -> {

            if (binding == null || isFinishing() || isDestroyed()) {
                return;
            }

            displayDailyContent(
                    phraseSnapshot,
                    wordSnapshot
            );
        });
    }


    /**
     * Combines Phrasebook and Dictionary content.
     * Every item stores its original module.
     */
    private void displayDailyContent(
            List<Phrase> phrases,
            List<Word> words
    ) {

        if (binding == null) {
            return;
        }

        SharedPreferences preferences =
                getSharedPreferences(
                        "PhrasePrefs",
                        Context.MODE_PRIVATE
                );

        String selectedLanguage =
                preferences.getString(
                        "selectedLanguage",
                        "English"
                );

        boolean useTagalog =
                "Tagalog".equalsIgnoreCase(selectedLanguage);

        List<DailyCardItem> dailyItems = new ArrayList<>();


        // -----------------------------------------------------
        // ADD PHRASEBOOK RECORDS
        // -----------------------------------------------------

        if (phrases != null) {

            for (Phrase phrase : phrases) {

                if (phrase == null) {
                    continue;
                }

                String ilokanoText =
                        getIlokanoDisplayText(phrase);

                String translation =
                        getTranslation(phrase, useTagalog);

                if (!ilokanoText.trim().isEmpty()
                        && !translation.trim().isEmpty()) {

                    dailyItems.add(
                            new DailyCardItem(
                                    "PHRASEBOOK",
                                    ilokanoText,
                                    translation
                            )
                    );
                }
            }
        }


        // -----------------------------------------------------
        // ADD DICTIONARY RECORDS
        // -----------------------------------------------------

        if (words != null) {

            for (Word word : words) {

                if (word == null) {
                    continue;
                }

                String ilokanoText = word.getIlokanoWord();

                String translation;

                if (useTagalog) {

                    translation = word.getTagalogTranslation();

                    // Use English if Tagalog is unavailable.
                    if (translation == null
                            || translation.trim().isEmpty()) {

                        translation = word.getEnglishTranslation();
                    }

                } else {

                    translation = word.getEnglishTranslation();
                }

                if (ilokanoText != null
                        && translation != null
                        && !ilokanoText.trim().isEmpty()
                        && !translation.trim().isEmpty()) {

                    dailyItems.add(
                            new DailyCardItem(
                                    "DICTIONARY",
                                    ilokanoText.trim(),
                                    translation.trim()
                            )
                    );
                }
            }
        }


        // -----------------------------------------------------
        // CHECK AVAILABLE CONTENT
        // -----------------------------------------------------

        if (dailyItems.isEmpty()) {
            showEmptyDailyPhrase();
            return;
        }


        // -----------------------------------------------------
        // SELECT DAILY CONTENT
        // -----------------------------------------------------

        Calendar calendar = Calendar.getInstance();

        int year = calendar.get(Calendar.YEAR);
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);

        int dailySeed = year * 1000 + dayOfYear;

        int dailyIndex = Math.floorMod(
                dailySeed,
                dailyItems.size()
        );

        DailyCardItem selectedItem =
                dailyItems.get(dailyIndex);


        // -----------------------------------------------------
        // SAVE THE SELECTED ITEM AND ITS SOURCE
        // -----------------------------------------------------

        dailyPhraseSearchTerm =
                selectedItem.ilokanoText;

        dailyPhraseDestination =
                selectedItem.source;


        // -----------------------------------------------------
        // DISPLAY DAILY CONTENT
        // -----------------------------------------------------

        binding.tvDailyPhrase.setText(
                selectedItem.ilokanoText
        );

        binding.tvDailyPhraseTranslation.setText(
                selectedItem.translation
        );

        binding.tvDailyPhrase.setVisibility(View.VISIBLE);

        binding.tvDailyPhraseTranslation.setVisibility(
                View.VISIBLE
        );
    }


    // =========================================================
    // DAILY CONTENT MODEL
    // =========================================================

    private static class DailyCardItem {

        final String source;
        final String ilokanoText;
        final String translation;

        DailyCardItem(
                String source,
                String ilokanoText,
                String translation
        ) {

            this.source = source;
            this.ilokanoText = ilokanoText;
            this.translation = translation;
        }
    }


    // =========================================================
    // PHRASEBOOK DISPLAY HELPERS
    // =========================================================

    private String getIlokanoDisplayText(Phrase phrase) {

        if (phrase == null) {
            return "";
        }

        String sentence = phrase.getIlokanoSentence();

        if (sentence != null && !sentence.trim().isEmpty()) {
            return sentence.trim();
        }

        String word = phrase.getIlokanoWord();

        return word == null ? "" : word.trim();
    }


    private String getTranslation(Phrase phrase, boolean useTagalog) {

        if (phrase == null) {
            return "";
        }

        if (useTagalog) {

            String tagalog = phrase.getTagalogTranslation();

            if (tagalog != null && !tagalog.trim().isEmpty()) {
                return tagalog.trim();
            }
        }

        return getEnglishDisplayText(phrase);
    }


    private String getEnglishDisplayText(Phrase phrase) {

        if (phrase == null) {
            return "";
        }

        String sentence = phrase.getEnglishSentence();

        if (sentence != null && !sentence.trim().isEmpty()) {
            return sentence.trim();
        }

        String translation = phrase.getEnglishTranslation();

        return translation == null ? "" : translation.trim();
    }


    // =========================================================
    // EMPTY DAILY CARD
    // =========================================================

    private void showEmptyDailyPhrase() {

        if (binding == null) {
            return;
        }

        dailyPhraseSearchTerm = "";
        dailyPhraseDestination = "PHRASEBOOK";

        binding.tvDailyPhrase.setText(
                "Daily phrase is not available yet."
        );

        binding.tvDailyPhraseTranslation.setText(
                "Open Phrasebook or Dictionary to load learning content."
        );

        binding.tvDailyPhrase.setVisibility(View.VISIBLE);

        binding.tvDailyPhraseTranslation.setVisibility(
                View.VISIBLE
        );
    }


    // =========================================================
    // OFFLINE / ONLINE / SYNC INDICATOR
    // =========================================================

    private void setupSyncIndicator() {

        binding.btnRetrySync.setOnClickListener(v -> {

            if (!NetworkUtil.isOnline(this)) {

                refreshConnectionStatus();

                binding.tvSyncStatus.setText(
                        "No internet connection. Please reconnect and retry."
                );

                return;
            }

            startSync();
        });

        updateLastSyncText();

        binding.progressSync.setVisibility(View.GONE);
    }


    private void startSync() {

        if (binding == null) {
            return;
        }

        if (!NetworkUtil.isOnline(this)) {

            refreshConnectionStatus();

            binding.tvSyncStatus.setText(
                    "Offline mode. Your saved data remains available."
            );

            return;
        }

        binding.btnRetrySync.setEnabled(false);
        binding.progressSync.setVisibility(View.VISIBLE);

        binding.tvSyncStatus.setText(
                "Checking for updates..."
        );

        SyncManager.sync(this, new SyncManager.SyncListener() {

            @Override
            public void onSyncStarted() {

                if (binding == null || isFinishing() || isDestroyed()) {
                    return;
                }

                binding.progressSync.setVisibility(View.VISIBLE);
                binding.btnRetrySync.setEnabled(false);

                binding.tvSyncStatus.setText("Syncing...");
            }


            @Override
            public void onSyncFinished(
                    boolean success,
                    String message
            ) {

                if (binding == null || isFinishing() || isDestroyed()) {
                    return;
                }

                binding.progressSync.setVisibility(View.GONE);
                binding.btnRetrySync.setEnabled(true);

                binding.tvSyncStatus.setText(message);

                updateLastSyncText();
                refreshConnectionStatus();

                // Refresh the daily card after synchronization.
                loadDailyPhrase();
            }
        });
    }


    private void refreshConnectionStatus() {

        if (binding == null) {
            return;
        }

        boolean online = NetworkUtil.isOnline(this);

        binding.tvConnectionStatus.setText(
                online ? "Online" : "Offline mode"
        );

        if (!online && !SyncManager.isSyncing()) {

            binding.tvSyncStatus.setText(
                    "Your saved data is available offline."
            );
        }
    }


    private void updateLastSyncText() {

        if (binding == null) {
            return;
        }

        long lastSync =
                SyncManager.getLastSuccessfulSync(this);

        if (lastSync <= 0) {

            binding.tvLastSync.setText(
                    "Last successful sync: Not yet synced"
            );

            return;
        }

        SimpleDateFormat formatter = new SimpleDateFormat(
                "MMM d, yyyy h:mm a",
                Locale.getDefault()
        );

        binding.tvLastSync.setText(
                "Last successful sync: "
                        + formatter.format(new Date(lastSync))
        );
    }


    // =========================================================
    // MONITOR CONNECTIVITY CHANGES
    // =========================================================

    @Override
    protected void onStart() {

        super.onStart();

        connectivityManager =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (connectivityManager == null
                || networkCallbackRegistered) {
            return;
        }

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET
                        )
                        .build();

        networkCallback =
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(Network network) {
                        runOnUiThread(
                                () -> handleConnectivityChange()
                        );
                    }


                    @Override
                    public void onLost(Network network) {
                        runOnUiThread(
                                () -> handleConnectivityChange()
                        );
                    }


                    @Override
                    public void onCapabilitiesChanged(
                            Network network,
                            NetworkCapabilities capabilities
                    ) {

                        runOnUiThread(
                                () -> handleConnectivityChange()
                        );
                    }
                };

        try {

            connectivityManager.registerNetworkCallback(
                    request,
                    networkCallback
            );

            networkCallbackRegistered = true;

        } catch (Exception ignored) {
            // Initial status checks still work if registration fails.
        }
    }


    private void handleConnectivityChange() {

        if (binding == null) {
            return;
        }

        boolean online = NetworkUtil.isOnline(this);
        boolean wasOnline = lastKnownOnline;

        lastKnownOnline = online;

        refreshConnectionStatus();

        if (online && !wasOnline) {
            startSync();
        }
    }


    @Override
    protected void onStop() {

        if (connectivityManager != null
                && networkCallback != null
                && networkCallbackRegistered) {

            try {

                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );

            } catch (Exception ignored) {
                // Callback may already have been unregistered.
            }
        }

        networkCallbackRegistered = false;
        networkCallback = null;

        super.onStop();
    }


    // =========================================================
    // ACTIVITY LIFECYCLE
    // =========================================================

    @Override
    protected void onResume() {

        super.onResume();

        if (hasResumedOnce) {

            loadDailyPhrase();

            refreshConnectionStatus();
            updateLastSyncText();

        } else {

            hasResumedOnce = true;
        }
    }


    @Override
    protected void onDestroy() {

        if (phraseRepository != null) {

            phraseRepository.close();
            phraseRepository = null;
        }

        // DictionaryRepository is not explicitly closed here
        // because its close() method has not been confirmed.

        dictionaryRepository = null;

        binding = null;

        super.onDestroy();
    }
}

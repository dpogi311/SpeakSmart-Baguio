
package com.example.speaksmartbaguio;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.speaksmartbaguio.databinding.FragmentPhrasebookBinding;
import com.example.speaksmartbaguio.databinding.ItemPhrasebookHeaderBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.PhraseRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.NetworkUtil;
import com.example.speaksmartbaguio.utils.SettingsMenuHelper;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class PhrasebookFragment extends Fragment {

    private static final String TAG = "PHRASEBOOK";
    private static final String TTS_TAG = "PHRASEBOOK_TTS";

    private static final int PAGE_SIZE = 100;

    private static final String PREFS_NAME = "PhrasePrefs";
    private static final String KEY_LANGUAGE = "selectedLanguage";

    private FragmentPhrasebookBinding binding;
    private ItemPhrasebookHeaderBinding headerBinding;

    private PhrasebookAdapter adapter;
    private PhrasebookHeaderAdapter headerAdapter;

    private ApiService apiService;
    private PhraseRepository repository;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean incomingSearchHandled = false;
    private String selectedLanguage = "English";
    private String pendingSearchQuery = "";

    private int currentPage = 1;

    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean isApplyingIncomingSearch = false;
    private boolean headerInitialized = false;

    private final Handler searchHandler =
            new Handler(Looper.getMainLooper());

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable searchRunnable = () -> {
        if (binding != null && isAdded()) {
            loadPhrases(true);
        }
    };

    public PhrasebookFragment() {
        super(R.layout.fragment_phrasebook);
    }

    // =========================================================
    // VIEW CREATION
    // =========================================================

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentPhrasebookBinding.bind(view);

        // Allow incoming search handling for this view lifecycle.
        incomingSearchHandled = false;

        apiService = ApiService.getInstance();

        repository = new PhraseRepository(requireContext());

        setupTextToSpeech();
        setupRecyclerView();

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                view
        );
    }
    // =========================================================
    // LOAD SAVED LANGUAGE
    // =========================================================

    private void loadSavedLanguage() {

        if (!isAdded()) {
            selectedLanguage = "English";
            return;
        }

        SharedPreferences prefs =
                requireContext().getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        selectedLanguage = prefs.getString(
                KEY_LANGUAGE,
                "English"
        );

        if (!"English".equals(selectedLanguage)
                && !"Tagalog".equals(selectedLanguage)) {

            selectedLanguage = "English";
        }
    }

    // =========================================================
    // RECYCLER VIEW + HEADER + FAST SCROLLER
    // =========================================================

    private void setupRecyclerView() {

        // Load the saved language BEFORE creating the adapter.
        loadSavedLanguage();

        adapter = new PhrasebookAdapter(
                new ArrayList<>(),
                this::speakPhrase,
                selectedLanguage
        );

        adapter.setSearchQuery(pendingSearchQuery);

        adapter.setOnLoadMoreListener(() -> {
            if (!isLoading && hasMore) {
                loadPhrases(false);
            }
        });

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(requireContext());

        binding.recyclerViewPhrasebook.setLayoutManager(
                layoutManager
        );

        headerAdapter = new PhrasebookHeaderAdapter(
                this::onHeaderBound
        );

        ConcatAdapter concatAdapter = new ConcatAdapter(
                headerAdapter,
                adapter
        );

        binding.recyclerViewPhrasebook.setAdapter(
                concatAdapter
        );

        binding.recyclerViewPhrasebook.setNestedScrollingEnabled(
                true
        );

        binding.phrasebookFastScroller.attachToRecyclerView(
                binding.recyclerViewPhrasebook
        );

        binding.phrasebookFastScroller.setOnReachBottomListener(
                () -> {
                    if (!isLoading && hasMore) {
                        loadPhrases(false);
                    }
                }
        );
    }

    // =========================================================
    // HEADER INITIALIZATION
    // =========================================================

    private void onHeaderBound(
            ItemPhrasebookHeaderBinding newHeaderBinding
    ) {

        if (binding == null || !isAdded()) {
            return;
        }

        headerBinding = newHeaderBinding;

        // Prevent duplicate initialization during header rebinding.
        if (headerInitialized) {
            return;
        }

        headerInitialized = true;

        setupSettingsMenuButton();
        setupLanguageDropdown();
        setupSearch();

        // Wait until RecyclerView has finished its layout.
        runWhenRecyclerViewIsIdle(() -> {

            if (binding == null
                    || !isAdded()
                    || adapter == null
                    || headerBinding == null) {
                return;
            }

            // Apply the incoming Daily Ilokano search.
            handleIncomingSearch();

            if (!pendingSearchQuery.isEmpty()) {

                // Load matching online phrases.
                loadPhrases(true);

            } else {

                // Load locally saved phrases first.
                loadOfflinePhrases();

                Context context = getContext();

                // Refresh from the API when internet is available.
                if (context != null
                        && NetworkUtil.isOnline(context)) {

                    loadPhrases(true);
                }
            }
        });
    }
    private void runWhenRecyclerViewIsIdle(Runnable action) {

        if (binding == null || !isAdded()) {
            return;
        }

        RecyclerView recyclerView =
                binding.recyclerViewPhrasebook;

        recyclerView.post(new Runnable() {

            @Override
            public void run() {

                if (binding == null
                        || !isAdded()
                        || binding.recyclerViewPhrasebook != recyclerView) {
                    return;
                }

                if (recyclerView.isComputingLayout()) {

                    recyclerView.post(this);

                    return;
                }

                action.run();
            }
        });
    }
    // =========================================================
    // INCOMING SEARCH
    // =========================================================

    private void handleIncomingSearch() {

        if (!isAdded()
                || binding == null
                || headerBinding == null
                || incomingSearchHandled) {
            return;
        }

        Bundle args = getArguments();

        incomingSearchHandled = true;

        if (args == null) {
            return;
        }

        String query = args.getString(
                MainActivity.EXTRA_PHRASEBOOK_SEARCH
        );

        // Consume the argument so it cannot be applied again.
        args.remove(MainActivity.EXTRA_PHRASEBOOK_SEARCH);

        if (query == null || query.trim().isEmpty()) {
            return;
        }

        pendingSearchQuery = query.trim();

        // Prevent the TextWatcher from treating this as
        // a manually entered search.
        isApplyingIncomingSearch = true;

        headerBinding.searchBar.setText(
                pendingSearchQuery
        );

        headerBinding.searchBar.setSelection(
                headerBinding.searchBar.getText().length()
        );

        isApplyingIncomingSearch = false;

        if (adapter != null) {
            adapter.setSearchQuery(pendingSearchQuery);
        }
    }

    // =========================================================
    // SETTINGS MENU
    // =========================================================

    private void setupSettingsMenuButton() {

        if (headerBinding == null) {
            return;
        }

        headerBinding.buttonSettingsMenu.setOnClickListener(
                v -> SettingsMenuHelper.show(v)
        );
    }

    // =========================================================
    // TEXT-TO-SPEECH
    // =========================================================

    private void setupTextToSpeech() {

        ttsReady = false;

        tts = new TextToSpeech(
                requireContext().getApplicationContext(),
                status -> {

                    if (tts == null) {
                        return;
                    }

                    if (status != TextToSpeech.SUCCESS) {

                        ttsReady = false;

                        Log.e(
                                TTS_TAG,
                                "Text-to-Speech initialization failed."
                        );

                        return;
                    }

                    try {

                        tts.setPitch(0.95f);
                        tts.setSpeechRate(0.85f);

                        ttsReady = setBestAvailableTtsLanguage();

                        if (!ttsReady) {
                            Log.e(
                                    TTS_TAG,
                                    "No supported TTS language was found."
                            );
                        }

                    } catch (Exception e) {

                        ttsReady = false;

                        Log.e(
                                TTS_TAG,
                                "Unable to configure Text-to-Speech.",
                                e
                        );
                    }
                }
        );
    }

    /**
     * Android does not guarantee a native Ilokano voice.
     * Try Filipino/Tagalog first, then English as fallback.
     */
    private boolean setBestAvailableTtsLanguage() {

        if (tts == null) {
            return false;
        }

        Locale[] preferredLocales = {
                new Locale("fil", "PH"),
                new Locale("tl", "PH"),
                new Locale("en", "PH"),
                Locale.US
        };

        for (Locale locale : preferredLocales) {

            try {

                int availability =
                        tts.isLanguageAvailable(locale);

                if (availability == TextToSpeech.LANG_MISSING_DATA
                        || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
                    continue;
                }

                int result = tts.setLanguage(locale);

                if (result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED) {

                    Log.d(
                            TTS_TAG,
                            "Selected TTS language: " + locale
                    );

                    return true;
                }

            } catch (IllegalArgumentException e) {

                Log.w(
                        TTS_TAG,
                        "Could not select TTS language: " + locale,
                        e
                );
            }
        }

        return false;
    }

    // =========================================================
    // LANGUAGE DROPDOWN
    // =========================================================

    private void setupLanguageDropdown() {

        if (headerBinding == null || !isAdded()) {
            return;
        }

        final String[] languages = {
                "English",
                "Tagalog"
        };

        SharedPreferences prefs =
                requireContext().getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        ArrayAdapter<String> dropdownAdapter =
                new ArrayAdapter<String>(
                        requireContext(),
                        R.layout.dropdown_item_bold,
                        new ArrayList<>(Arrays.asList(languages))
                ) {

                    private final Filter showAllLanguagesFilter =
                            new Filter() {

                                @Override
                                protected FilterResults performFiltering(
                                        CharSequence constraint
                                ) {

                                    FilterResults results =
                                            new FilterResults();

                                    ArrayList<String> allLanguages =
                                            new ArrayList<>(
                                                    Arrays.asList(languages)
                                            );

                                    results.values = allLanguages;
                                    results.count = allLanguages.size();

                                    return results;
                                }

                                @Override
                                protected void publishResults(
                                        CharSequence constraint,
                                        FilterResults results
                                ) {

                                    if (results == null
                                            || results.values == null) {
                                        return;
                                    }

                                    @SuppressWarnings("unchecked")
                                    List<String> allLanguages =
                                            (List<String>) results.values;

                                    clear();
                                    addAll(allLanguages);
                                    notifyDataSetChanged();
                                }
                            };

                    @NonNull
                    @Override
                    public Filter getFilter() {
                        return showAllLanguagesFilter;
                    }
                };

        headerBinding.languageDropdown.setAdapter(
                dropdownAdapter
        );

        headerBinding.languageDropdown.setDropDownBackgroundResource(
                R.drawable.dropdown_glass_background
        );

        headerBinding.languageDropdown.setKeyListener(null);
        headerBinding.languageDropdown.setFocusable(false);
        headerBinding.languageDropdown.setInputType(InputType.TYPE_NULL);
        headerBinding.languageDropdown.setThreshold(0);

        headerBinding.languageDropdownLayout.setHint(
                "Select Target Language"
        );

        headerBinding.languageDropdown.setText(
                selectedLanguage,
                false
        );

        final boolean[] isDropdownOpen = {false};

        View.OnClickListener toggleDropdown = v -> {

            if (headerBinding == null) {
                return;
            }

            if (isDropdownOpen[0]) {

                headerBinding.languageDropdown.dismissDropDown();
                isDropdownOpen[0] = false;

                return;
            }

            dropdownAdapter.getFilter().filter(
                    "",
                    count -> {

                        if (headerBinding == null) {
                            return;
                        }

                        headerBinding.languageDropdown.setAdapter(
                                dropdownAdapter
                        );

                        headerBinding.languageDropdown.setText(
                                selectedLanguage,
                                false
                        );

                        headerBinding.languageDropdown.showDropDown();

                        isDropdownOpen[0] = true;
                    }
            );
        };

        headerBinding.languageDropdown.setOnClickListener(
                toggleDropdown
        );

        headerBinding.languageDropdownLayout.setEndIconOnClickListener(
                toggleDropdown
        );

        headerBinding.languageDropdown.setOnItemClickListener(
                (parent, itemView, position, id) -> {

                    Object selectedItem =
                            parent.getItemAtPosition(position);

                    if (selectedItem == null) {
                        return;
                    }

                    selectedLanguage = selectedItem.toString();

                    prefs.edit()
                            .putString(
                                    KEY_LANGUAGE,
                                    selectedLanguage
                            )
                            .apply();

                    headerBinding.languageDropdown.setText(
                            selectedLanguage,
                            false
                    );

                    isDropdownOpen[0] = false;

                    if (adapter != null) {
                        adapter.setSelectedLanguage(
                                selectedLanguage
                        );
                    }
                }
        );
    }

    // =========================================================
    // SEARCH
    // =========================================================

    private void setupSearch() {

        if (headerBinding == null) {
            return;
        }

        headerBinding.searchBar.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {

                        if (isApplyingIncomingSearch) {
                            return;
                        }

                        pendingSearchQuery =
                                s == null
                                        ? ""
                                        : s.toString().trim();

                        if (adapter != null) {
                            adapter.setSearchQuery(
                                    pendingSearchQuery
                            );
                        }

                        searchHandler.removeCallbacks(
                                searchRunnable
                        );

                        searchHandler.postDelayed(
                                searchRunnable,
                                300
                        );
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                }
        );
    }

    // =========================================================
    // MODERN SNACKBAR NOTIFICATION
    // =========================================================

    private void showPhrasebookMessage(String message) {

        if (!isAdded() || binding == null) {
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

        snackbar.setAnimationMode(
                Snackbar.ANIMATION_MODE_SLIDE
        );

        snackbar.show();
    }

    // =========================================================
    // LOAD ONLINE PHRASES
    // =========================================================

    private void loadPhrases(boolean reset) {

        if (binding == null || !isAdded()) {
            return;
        }

        Context context = getContext();

        if (context == null) {
            return;
        }

        // Offline mode.
        if (!NetworkUtil.isOnline(context)) {

            isLoading = false;

            if (reset) {
                currentPage = 1;
                hasMore = false;
            }

            if (adapter != null) {
                adapter.setPaginationState(false, false);
            }

            if (pendingSearchQuery.isEmpty()) {
                loadOfflinePhrases();
            } else {
                searchOfflinePhrases(pendingSearchQuery);
            }

            showPhrasebookMessage(
                    "Offline Mode — showing saved phrases"
            );

            return;
        }

        if (isLoading) {
            return;
        }

        if (!reset && !hasMore) {
            return;
        }

        isLoading = true;

        if (reset) {

            currentPage = 1;
            hasMore = true;

            if (headerBinding != null) {
                headerBinding.progressBar.setVisibility(
                        View.VISIBLE
                );
            }

            adapter.setPaginationState(false, false);

        } else {

            adapter.setPaginationState(true, true);
        }

        final String requestedQuery = pendingSearchQuery;
        final int requestedPage = currentPage;

        apiService.getPhrasebook(
                requestedPage,
                PAGE_SIZE,
                requestedQuery,
                new ApiService.ApiCallback<Phrase>() {

                    @Override
                    public void onSuccess(
                            List<Phrase> items,
                            boolean more
                    ) {

                        mainHandler.post(() -> {

                            if (binding == null || !isAdded()) {
                                isLoading = false;
                                return;
                            }

                            // Ignore results from an older search.
                            if (!requestedQuery.equals(pendingSearchQuery)) {

                                isLoading = false;

                                adapter.setPaginationState(
                                        false,
                                        hasMore
                                );

                                loadPhrases(true);
                                return;
                            }

                            isLoading = false;

                            if (headerBinding != null) {
                                headerBinding.progressBar.setVisibility(
                                        View.GONE
                                );
                            }

                            List<Phrase> safeItems =
                                    items != null
                                            ? items
                                            : new ArrayList<>();

                            if (reset) {

                                adapter.filterList(
                                        new ArrayList<>(safeItems)
                                );

                            } else {

                                adapter.addItems(safeItems);
                            }

                            if (!safeItems.isEmpty()) {

                                repository.insertAll(
                                        EntityMapper.toPhraseEntityList(
                                                safeItems
                                        )
                                );
                            }

                            hasMore = more;
                            currentPage = requestedPage + 1;

                            adapter.setPaginationState(
                                    false,
                                    hasMore
                            );

                            updateEmptyState(
                                    adapterPhraseCount(),
                                    requestedQuery
                            );
                        });
                    }

                    @Override
                    public void onError(String error) {

                        mainHandler.post(() -> {

                            isLoading = false;

                            if (binding == null || !isAdded()) {
                                return;
                            }

                            if (!requestedQuery.equals(pendingSearchQuery)) {
                                loadPhrases(true);
                                return;
                            }

                            if (headerBinding != null) {
                                headerBinding.progressBar.setVisibility(
                                        View.GONE
                                );
                            }

                            adapter.setPaginationState(
                                    false,
                                    hasMore
                            );

                            if (requestedQuery.isEmpty()) {
                                loadOfflinePhrases();
                            } else {
                                searchOfflinePhrases(requestedQuery);
                            }

                            showPhrasebookMessage(
                                    "Unable to refresh phrases. "
                                            + "Showing saved results if available."
                            );

                            Log.e(
                                    TAG,
                                    "Unable to load phrases: " + error
                            );
                        });
                    }
                }
        );
    }

    // =========================================================
    // LOAD OFFLINE PHRASES
    // =========================================================

    private void loadOfflinePhrases() {

        if (repository == null) {
            return;
        }

        if (!pendingSearchQuery.isEmpty()) {
            searchOfflinePhrases(pendingSearchQuery);
            return;
        }

        repository.getAllPhrases(result -> {

            mainHandler.post(() -> {

                if (binding == null
                        || !isAdded()
                        || adapter == null) {
                    return;
                }

                List<Phrase> phrases =
                        EntityMapper.toPhraseList(result);

                if (phrases == null) {
                    phrases = new ArrayList<>();
                }

                adapter.filterList(phrases);

                updateEmptyState(
                        phrases.size(),
                        pendingSearchQuery
                );

                if (headerBinding != null) {
                    headerBinding.progressBar.setVisibility(
                            View.GONE
                    );
                }
            });
        });
    }

    // =========================================================
    // SEARCH OFFLINE PHRASES
    // =========================================================

    private void searchOfflinePhrases(String query) {

        if (repository == null) {
            return;
        }

        repository.searchPhrases(query, result -> {

            mainHandler.post(() -> {

                if (binding == null
                        || !isAdded()
                        || adapter == null) {
                    return;
                }

                // Do not display results for an outdated query.
                if (!query.equals(pendingSearchQuery)) {
                    return;
                }

                List<Phrase> phrases =
                        EntityMapper.toPhraseList(result);

                if (phrases == null) {
                    phrases = new ArrayList<>();
                }

                adapter.setSearchQuery(query);
                adapter.filterList(phrases);

                updateEmptyState(
                        phrases.size(),
                        query
                );

                if (headerBinding != null) {
                    headerBinding.progressBar.setVisibility(
                            View.GONE
                    );
                }
            });
        });
    }

    // =========================================================
    // EMPTY STATE
    // =========================================================

    private int adapterPhraseCount() {

        if (adapter == null) {
            return 0;
        }

        // PhrasebookAdapter includes one footer row.
        return Math.max(
                0,
                adapter.getItemCount() - 1
        );
    }

    private void updateEmptyState(
            int phraseCount,
            String query
    ) {

        if (headerBinding == null || binding == null) {
            return;
        }

        if (phraseCount == 0) {

            headerBinding.textViewEmpty.setVisibility(
                    View.VISIBLE
            );

            if (query != null && !query.trim().isEmpty()) {

                headerBinding.textViewEmpty.setText(
                        "No phrases found. Try another search."
                );

            } else {

                headerBinding.textViewEmpty.setText(
                        "No phrases available yet. "
                                + "Connect to the internet to refresh."
                );
            }

        } else {

            headerBinding.textViewEmpty.setVisibility(
                    View.GONE
            );
        }

        binding.recyclerViewPhrasebook.setVisibility(
                View.VISIBLE
        );
    }

    // =========================================================
    // ILOKANO PRONUNCIATION
    // =========================================================

    private void speakPhrase(Phrase phrase) {

        if (phrase == null || !isAdded()) {
            return;
        }

        String ilokanoText = phrase.getIlokanoWord();

        if (ilokanoText == null || ilokanoText.trim().isEmpty()) {

            showPhrasebookMessage(
                    "Ilokano word is not available."
            );

            return;
        }

        if (tts == null || !ttsReady) {

            showPhrasebookMessage(
                    "Text-to-Speech is still starting. Please try again."
            );

            return;
        }

        try {

            tts.stop();

            int result = tts.speak(
                    ilokanoText.trim(),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "phrasebook_word_" + System.currentTimeMillis()
            );

            if (result == TextToSpeech.ERROR) {

                showPhrasebookMessage(
                        "Unable to pronounce this Ilokano word."
                );
            }

        } catch (Exception e) {

            Log.e(
                    TTS_TAG,
                    "Error speaking Ilokano word: " + ilokanoText,
                    e
            );

            showPhrasebookMessage(
                    "Unable to pronounce this word."
            );
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    public void onDestroyView() {

        searchHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);

        ttsReady = false;

        if (tts != null) {

            try {

                tts.stop();
                tts.shutdown();

            } catch (Exception e) {

                Log.w(
                        TTS_TAG,
                        "Error shutting down Text-to-Speech.",
                        e
                );
            }

            tts = null;
        }

        if (binding != null) {

            binding.phrasebookFastScroller.setOnReachBottomListener(
                    null
            );

            binding.phrasebookFastScroller.setScrollCallback(
                    null
            );

            binding.phrasebookFastScroller.attachToRecyclerView(
                    null
            );
        }

        headerBinding = null;
        headerAdapter = null;
        adapter = null;

        headerInitialized = false;
        incomingSearchHandled = false;

        isLoading = false;

        binding = null;

        super.onDestroyView();
    }
}
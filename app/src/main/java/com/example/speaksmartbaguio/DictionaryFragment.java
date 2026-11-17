
package com.example.speaksmartbaguio;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Toast;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.databinding.FragmentDictionaryBinding;
import com.example.speaksmartbaguio.databinding.ItemDictionaryHeaderBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.NetworkUtil;
import com.example.speaksmartbaguio.utils.SettingsMenuHelper;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DictionaryFragment extends Fragment {

    private static final String TAG = "DICTIONARY";
    private static final String TTS_TAG = "DICTIONARY_TTS";

    private static final int PAGE_SIZE = 100;
    private static final long SEARCH_DELAY = 300L;

    private static final String PREFS_NAME = "DictionaryPrefs";
    private static final String KEY_LANGUAGE = "selectedLanguage";
    private static final String KEY_SORT_ORDER = "sortOrder";

    private FragmentDictionaryBinding binding;
    private ItemDictionaryHeaderBinding headerBinding;

    private DictionaryHeaderAdapter headerAdapter;
    private DictionaryAdapter dictionaryAdapter;
    private ConcatAdapter concatAdapter;

    private RecyclerView.OnScrollListener paginationScrollListener;

    private ApiService apiService;
    private DictionaryRepository repository;

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    private String selectedLanguage = "English";

    private String selectedSortOrder =
            DictionaryAdapter.SORT_A_TO_Z;

    private String pendingSearchQuery = "";

    private int currentPage = 1;

    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean isApplyingIncomingSearch = false;

    private boolean headerInitialized = false;
    private boolean initialLoadScheduled = false;

    private long dataGeneration = 0L;

    private boolean onlineResultApplied = false;

    private final Handler searchHandler =
            new Handler(Looper.getMainLooper());

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public DictionaryFragment() {
        super(R.layout.fragment_dictionary);
    }

    // =========================================================
    // INITIALIZATION
    // =========================================================

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(view, savedInstanceState);

        binding = FragmentDictionaryBinding.bind(view);

        apiService = ApiService.getInstance();

        repository = new DictionaryRepository(
                requireContext().getApplicationContext()
        );

        restorePreferences();

        setupTextToSpeech();
        setupRecyclerView();

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                binding.getRoot()
        );
    }

    // =========================================================
    // RESTORE PREFERENCES
    // =========================================================

    private void restorePreferences() {

        SharedPreferences preferences =
                requireContext().getSharedPreferences(
                        PREFS_NAME,
                        android.content.Context.MODE_PRIVATE
                );

        selectedLanguage = preferences.getString(
                KEY_LANGUAGE,
                "English"
        );

        if (!"English".equals(selectedLanguage)
                && !"Tagalog".equals(selectedLanguage)) {

            selectedLanguage = "English";
        }

        selectedSortOrder = preferences.getString(
                KEY_SORT_ORDER,
                DictionaryAdapter.SORT_A_TO_Z
        );

        if (!DictionaryAdapter.SORT_Z_TO_A.equals(selectedSortOrder)) {
            selectedSortOrder = DictionaryAdapter.SORT_A_TO_Z;
        }

        preferences.edit()
                .remove("selectedLetter")
                .apply();
    }

    // =========================================================
    // RECYCLER VIEW
    // =========================================================

    private void setupRecyclerView() {

        dictionaryAdapter = new DictionaryAdapter(
                new ArrayList<>(),
                this::speakWord,
                selectedLanguage
        );

        dictionaryAdapter.setSortOrder(selectedSortOrder);
        dictionaryAdapter.clearLetterFilter();

        dictionaryAdapter.setOnLoadMoreListener(() -> {

            if (!isLoading && hasMore) {
                loadWords(false);
            }
        });

        headerAdapter = new DictionaryHeaderAdapter(
                this::onHeaderBound
        );

        concatAdapter = new ConcatAdapter(
                headerAdapter,
                dictionaryAdapter
        );

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(requireContext());

        binding.recyclerViewDictionary.setLayoutManager(
                layoutManager
        );

        binding.recyclerViewDictionary.setHasFixedSize(false);

        binding.recyclerViewDictionary.setItemViewCacheSize(8);

        binding.recyclerViewDictionary.setAdapter(
                concatAdapter
        );

        paginationScrollListener =
                new RecyclerView.OnScrollListener() {

                    @Override
                    public void onScrolled(
                            @NonNull RecyclerView recyclerView,
                            int dx,
                            int dy
                    ) {

                        if (dy <= 0
                                || isLoading
                                || !hasMore) {
                            return;
                        }

                        int lastVisible =
                                layoutManager.findLastVisibleItemPosition();

                        int totalItems =
                                concatAdapter.getItemCount();

                        if (lastVisible >= totalItems - 5) {
                            loadWords(false);
                        }
                    }
                };

        binding.recyclerViewDictionary.addOnScrollListener(
                paginationScrollListener
        );

        binding.dictionaryFastScroller.attachToRecyclerView(
                binding.recyclerViewDictionary
        );

        binding.dictionaryFastScroller.setOnReachBottomListener(
                () -> {

                    if (!isLoading && hasMore) {
                        loadWords(false);
                    }
                }
        );
    }

    // =========================================================
    // HEADER INITIALIZATION
    // =========================================================

    private void onHeaderBound(
            ItemDictionaryHeaderBinding newHeaderBinding
    ) {

        if (binding == null || !isAdded()) {
            return;
        }

        if (headerBinding == newHeaderBinding
                && headerInitialized) {
            return;
        }

        headerBinding = newHeaderBinding;

        if (headerInitialized) {
            return;
        }

        headerInitialized = true;

        setupSettingsMenuButton();
        setupLanguageDropdown();
        setupFilterPanel();
        setupSearch();

        updateSortRadioButtons();

        // Capture incoming search only.
        // Do not notify the adapter during header binding.
        handleIncomingSearch();

        scheduleInitialLoad();
    }

    private void scheduleInitialLoad() {

        if (initialLoadScheduled || binding == null) {
            return;
        }

        initialLoadScheduled = true;

        binding.recyclerViewDictionary.post(() -> {

            initialLoadScheduled = false;

            if (!isAdded()
                    || binding == null
                    || dictionaryAdapter == null
                    || repository == null) {
                return;
            }

            // Apply incoming search after RecyclerView layout.
            dictionaryAdapter.setSearchQuery(
                    pendingSearchQuery
            );

            // Preserve existing local-first and online loading.
            loadWords(true);
        });
    }

    // =========================================================
    // INCOMING SEARCH
    // =========================================================

    private void handleIncomingSearch() {

        if (headerBinding == null || getArguments() == null) {
            return;
        }

        String query = getArguments().getString(
                MainActivity.EXTRA_DICTIONARY_SEARCH
        );

        if (query == null || query.trim().isEmpty()) {
            return;
        }

        pendingSearchQuery = query.trim();

        isApplyingIncomingSearch = true;

        headerBinding.searchBar.setText(
                pendingSearchQuery
        );

        headerBinding.searchBar.setSelection(
                headerBinding.searchBar.getText().length()
        );

        isApplyingIncomingSearch = false;
    }

    // =========================================================
    // SETTINGS MENU
    // =========================================================

    private void setupSettingsMenuButton() {

        headerBinding.buttonSettingsMenu.setOnClickListener(
                v -> SettingsMenuHelper.show(v)
        );
    }

    // =========================================================
    // TEXT TO SPEECH
    // =========================================================

    private void setupTextToSpeech() {

        isTtsReady = false;

        tts = new TextToSpeech(
                requireContext().getApplicationContext(),
                status -> {

                    if (tts == null) {
                        return;
                    }

                    if (status != TextToSpeech.SUCCESS) {

                        isTtsReady = false;

                        Log.e(
                                TTS_TAG,
                                "Text-to-Speech initialization failed."
                        );

                        return;
                    }

                    try {

                        tts.setPitch(0.95f);
                        tts.setSpeechRate(0.85f);

                        configureTtsLanguage();

                        isTtsReady = true;

                        Log.d(
                                TTS_TAG,
                                "Text-to-Speech initialized successfully."
                        );

                    } catch (Exception e) {

                        isTtsReady = false;

                        Log.e(
                                TTS_TAG,
                                "Unable to configure Text-to-Speech.",
                                e
                        );
                    }
                }
        );
    }

    private void configureTtsLanguage() {

        if (tts == null) {
            return;
        }

        Locale[] preferredLocales = {
                new Locale("fil", "PH"),
                new Locale("tl", "PH"),
                new Locale("en", "PH"),
                Locale.US
        };

        boolean languageSelected = false;

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

                    languageSelected = true;
                    break;
                }

            } catch (IllegalArgumentException e) {

                Log.w(
                        TTS_TAG,
                        "Unable to select TTS language: " + locale,
                        e
                );
            }
        }

        if (!languageSelected) {

            Log.e(
                    TTS_TAG,
                    "No supported TTS language was found."
            );
        }
    }

    // =========================================================
    // LANGUAGE DROPDOWN
    // =========================================================

    private void setupLanguageDropdown() {

        MaterialAutoCompleteTextView languageDropdown =
                headerBinding.languageDropdown;

        List<String> languages = Arrays.asList(
                "English",
                "Tagalog"
        );

        ArrayAdapter<String> languageAdapter =
                new ArrayAdapter<String>(
                        requireContext(),
                        R.layout.item_language_dropdown,
                        new ArrayList<>(languages)
                ) {

                    private final Filter showAllLanguagesFilter =
                            new Filter() {

                                @Override
                                protected FilterResults performFiltering(
                                        CharSequence constraint
                                ) {

                                    FilterResults results =
                                            new FilterResults();

                                    List<String> allLanguages =
                                            new ArrayList<>(languages);

                                    results.values = allLanguages;
                                    results.count = allLanguages.size();

                                    return results;
                                }

                                @Override
                                protected void publishResults(
                                        CharSequence constraint,
                                        FilterResults results
                                ) {

                                    if (results != null
                                            && results.values != null) {
                                        notifyDataSetChanged();
                                    }
                                }
                            };

                    @NonNull
                    @Override
                    public Filter getFilter() {
                        return showAllLanguagesFilter;
                    }
                };

        languageDropdown.setAdapter(languageAdapter);

        languageDropdown.setDropDownBackgroundResource(
                R.drawable.dropdown_glass_background
        );

        languageDropdown.setThreshold(0);

        languageDropdown.setText(
                selectedLanguage,
                false
        );

        languageDropdown.setOnItemClickListener(
                (parent, itemView, position, id) -> {

                    Object selectedItem =
                            parent.getItemAtPosition(position);

                    if (selectedItem == null) {
                        return;
                    }

                    selectedLanguage = selectedItem.toString();

                    languageDropdown.setText(
                            selectedLanguage,
                            false
                    );

                    requireContext()
                            .getSharedPreferences(
                                    PREFS_NAME,
                                    android.content.Context.MODE_PRIVATE
                            )
                            .edit()
                            .putString(
                                    KEY_LANGUAGE,
                                    selectedLanguage
                            )
                            .apply();

                    if (dictionaryAdapter != null) {

                        dictionaryAdapter.setSelectedLanguage(
                                selectedLanguage
                        );
                    }
                }
        );
    }

    // =========================================================
    // SORTING PANEL
    // =========================================================

    private void setupFilterPanel() {

        headerBinding.dictionaryFilterPanel.setVisibility(
                View.GONE
        );

        updateSortRadioButtons();

        headerBinding.buttonDictionaryFilter.setOnClickListener(v -> {

            boolean isVisible =
                    headerBinding.dictionaryFilterPanel.getVisibility()
                            == View.VISIBLE;

            headerBinding.dictionaryFilterPanel.setVisibility(
                    isVisible ? View.GONE : View.VISIBLE
            );

            if (!isVisible) {
                updateSortRadioButtons();
            }
        });

        headerBinding.sortOrderGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {

                    if (checkedId == headerBinding.radioSortZA.getId()) {

                        selectedSortOrder =
                                DictionaryAdapter.SORT_Z_TO_A;

                    } else if (checkedId
                            == headerBinding.radioSortAZ.getId()) {

                        selectedSortOrder =
                                DictionaryAdapter.SORT_A_TO_Z;
                    }
                }
        );

        headerBinding.buttonApplyDictionaryFilters.setOnClickListener(v -> {

            selectedSortOrder =
                    headerBinding.radioSortZA.isChecked()
                            ? DictionaryAdapter.SORT_Z_TO_A
                            : DictionaryAdapter.SORT_A_TO_Z;

            saveSortPreference();

            dictionaryAdapter.setSortOrder(
                    selectedSortOrder
            );

            dictionaryAdapter.clearLetterFilter();

            headerBinding.dictionaryFilterPanel.setVisibility(
                    View.GONE
            );

            loadWords(true);
        });

        headerBinding.buttonClearDictionaryFilters.setOnClickListener(v -> {

            selectedSortOrder =
                    DictionaryAdapter.SORT_A_TO_Z;

            saveSortPreference();

            updateSortRadioButtons();

            dictionaryAdapter.setSortOrder(
                    selectedSortOrder
            );

            dictionaryAdapter.clearLetterFilter();

            headerBinding.dictionaryFilterPanel.setVisibility(
                    View.GONE
            );

            loadWords(true);
        });
    }

    private void saveSortPreference() {

        requireContext()
                .getSharedPreferences(
                        PREFS_NAME,
                        android.content.Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                        KEY_SORT_ORDER,
                        selectedSortOrder
                )
                .remove("selectedLetter")
                .apply();
    }

    private void updateSortRadioButtons() {

        if (headerBinding == null) {
            return;
        }

        if (DictionaryAdapter.SORT_Z_TO_A.equals(selectedSortOrder)) {

            headerBinding.radioSortZA.setChecked(true);

        } else {

            headerBinding.radioSortAZ.setChecked(true);
        }
    }

    // =========================================================
    // SEARCH
    // =========================================================

    private void setupSearch() {

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
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (isApplyingIncomingSearch) return;

                        final String query = s == null ? "" : s.toString().trim();

                        Log.d("DictionarySearch",
                                "Text changed: " + query
                                        + " | Focus: " + headerBinding.searchBar.hasFocus());

                        searchHandler.removeCallbacksAndMessages(null);

                        searchHandler.postDelayed(() -> {
                            if (!isAdded() || binding == null || headerBinding == null) return;

                            Log.d("DictionarySearch",
                                    "Before filtering: "
                                            + headerBinding.searchBar.hasFocus());

                            pendingSearchQuery = query;

                            if (dictionaryAdapter != null) {
                                dictionaryAdapter.setSearchQuery(query);
                            }

                            loadWords(true);

                            Log.d("DictionarySearch",
                                    "After loadWords: "
                                            + headerBinding.searchBar.hasFocus());

                        }, SEARCH_DELAY);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                }
        );headerBinding.searchBar.setOnFocusChangeListener((view, hasFocus) -> {
            Log.d("DictionarySearch", "Search focus changed: " + hasFocus);
        });
    }

    // =========================================================
    // DICTIONARY LOADING
    // LOCAL FIRST, ONLINE IN BACKGROUND
    // =========================================================

    private void loadWords(boolean reset) {

        if (!isAdded()
                || binding == null
                || repository == null
                || dictionaryAdapter == null) {
            return;
        }

        android.content.Context context = getContext();

        if (context == null) {
            return;
        }

        boolean isOnline = NetworkUtil.isOnline(context);

        if (isOnline && isLoading) {
            return;
        }

        if (!reset && !hasMore) {
            return;
        }

        if (reset) {

            currentPage = 1;
            hasMore = true;

            dataGeneration++;

            onlineResultApplied = false;
        }

        final long requestGeneration = dataGeneration;
        final String requestedQuery = pendingSearchQuery;

        // =====================================================
        // OFFLINE MODE
        // =====================================================

        if (!isOnline) {

            isLoading = false;
            hasMore = false;

            dictionaryAdapter.setPaginationState(
                    false,
                    false
            );

            if (requestedQuery.isEmpty()) {

                loadOfflineWords(
                        requestGeneration,
                        requestedQuery
                );

            } else {

                searchOffline(
                        requestedQuery,
                        requestGeneration
                );
            }

            return;
        }

        // =====================================================
        // LOAD LOCAL DATA FIRST
        // =====================================================

        if (reset) {

            if (headerBinding != null) {

                headerBinding.progressBar.setVisibility(View.GONE);
            }

            if (requestedQuery.isEmpty()) {

                loadOfflineWords(
                        requestGeneration,
                        requestedQuery
                );

            } else {

                searchOffline(
                        requestedQuery,
                        requestGeneration
                );
            }
        }

        // =====================================================
        // START ONLINE REFRESH
        // =====================================================

        isLoading = true;

        dictionaryAdapter.setPaginationState(
                !reset,
                hasMore
        );

        final int requestedPage = currentPage;

        apiService.getDictionary(
                requestedPage,
                PAGE_SIZE,
                requestedQuery,
                new ApiService.ApiCallback<Word>() {

                    @Override
                    public void onSuccess(
                            List<Word> items,
                            boolean more
                    ) {

                        mainHandler.post(() -> {

                            if (!isAdded()
                                    || binding == null
                                    || dictionaryAdapter == null) {
                                return;
                            }

                            if (requestGeneration != dataGeneration) {
                                return;
                            }

                            if (!requestedQuery.equals(pendingSearchQuery)) {

                                isLoading = false;
                                loadWords(true);
                                return;
                            }

                            isLoading = false;

                            onlineResultApplied = true;

                            if (headerBinding != null) {

                                headerBinding.progressBar.setVisibility(
                                        View.GONE
                                );
                            }

                            List<Word> safeItems =
                                    items != null
                                            ? items
                                            : new ArrayList<>();

                            if (reset) {

                                dictionaryAdapter.filterList(
                                        new ArrayList<>(safeItems)
                                );

                            } else {

                                dictionaryAdapter.addItems(safeItems);
                            }

                            // Save downloaded words for offline use.
                            if (!safeItems.isEmpty()
                                    && repository != null) {

                                repository.insertAll(
                                        EntityMapper.toEntityList(safeItems)
                                );
                            }

                            hasMore = more;
                            currentPage = requestedPage + 1;

                            dictionaryAdapter.setPaginationState(
                                    false,
                                    hasMore
                            );

                            boolean noVisibleWords =
                                    dictionaryAdapter.getItemCount() <= 1;

                            if (headerBinding != null) {

                                headerBinding.textViewEmpty.setVisibility(
                                        noVisibleWords && !hasMore
                                                ? View.VISIBLE
                                                : View.GONE
                                );

                                if (noVisibleWords && !hasMore) {

                                    headerBinding.textViewEmpty.setText(
                                            requestedQuery.isEmpty()
                                                    ? "No dictionary words available."
                                                    : "No matching words found."
                                    );
                                }
                            }

                            if (!requestedQuery.isEmpty()
                                    && noVisibleWords
                                    && hasMore) {

                                searchHandler.post(
                                        () -> loadWords(false)
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {

                        mainHandler.post(() -> {

                            if (!isAdded()
                                    || binding == null
                                    || dictionaryAdapter == null) {
                                return;
                            }

                            if (requestGeneration != dataGeneration) {
                                return;
                            }

                            isLoading = false;

                            if (!requestedQuery.equals(pendingSearchQuery)) {

                                loadWords(true);
                                return;
                            }

                            if (headerBinding != null) {

                                headerBinding.progressBar.setVisibility(
                                        View.GONE
                                );
                            }

                            dictionaryAdapter.setPaginationState(
                                    false,
                                    hasMore
                            );

                            Log.e(
                                    TAG,
                                    "Online dictionary loading failed: " + error
                            );

                            // Keep local data available.
                            if (!onlineResultApplied) {

                                if (requestedQuery.isEmpty()) {

                                    loadOfflineWords(
                                            requestGeneration,
                                            requestedQuery
                                    );

                                } else {

                                    searchOffline(
                                            requestedQuery,
                                            requestGeneration
                                    );
                                }
                            }

                            Log.d(
                                    TAG,
                                    "Keeping saved dictionary data available."
                            );
                        });
                    }
                }
        );
    }

    // =========================================================
    // LOAD SAVED DICTIONARY WORDS
    // =========================================================

    private void loadOfflineWords(
            long requestGeneration,
            String requestedQuery
    ) {

        if (!isAdded() || repository == null) {
            return;
        }

        repository.getAllWords(result -> {

            mainHandler.post(() -> {

                if (!isAdded()
                        || binding == null
                        || dictionaryAdapter == null) {
                    return;
                }

                if (requestGeneration != dataGeneration) {
                    return;
                }

                if (!requestedQuery.equals(pendingSearchQuery)) {
                    return;
                }

                if (onlineResultApplied) {
                    return;
                }

                List<Word> words =
                        result == null
                                ? new ArrayList<>()
                                : EntityMapper.toWordList(result);

                dictionaryAdapter.filterList(words);

                if (headerBinding != null) {

                    headerBinding.progressBar.setVisibility(View.GONE);

                    boolean noWords =
                            dictionaryAdapter.getItemCount() <= 1;

                    headerBinding.textViewEmpty.setVisibility(
                            noWords ? View.VISIBLE : View.GONE
                    );

                    if (noWords) {

                        headerBinding.textViewEmpty.setText(
                                "No saved dictionary words found."
                        );
                    }
                }

                Log.d(
                        TAG,
                        "Displayed " + words.size()
                                + " saved dictionary words."
                );
            });
        });
    }

    // =========================================================
    // SEARCH SAVED DICTIONARY WORDS
    // =========================================================

    private void searchOffline(
            String query,
            long requestGeneration
    ) {

        if (!isAdded() || repository == null) {
            return;
        }

        final String requestedQuery = query;

        repository.searchWords(
                requestedQuery,
                result -> {

                    mainHandler.post(() -> {

                        if (!isAdded()
                                || binding == null
                                || dictionaryAdapter == null) {
                            return;
                        }

                        if (requestGeneration != dataGeneration) {
                            return;
                        }

                        if (!requestedQuery.equals(pendingSearchQuery)) {
                            return;
                        }

                        if (onlineResultApplied) {
                            return;
                        }

                        List<Word> words =
                                result == null
                                        ? new ArrayList<>()
                                        : EntityMapper.toWordList(result);

                        dictionaryAdapter.setSearchQuery(
                                requestedQuery
                        );

                        dictionaryAdapter.filterList(words);

                        if (headerBinding != null) {

                            headerBinding.progressBar.setVisibility(
                                    View.GONE
                            );

                            boolean noWords =
                                    dictionaryAdapter.getItemCount() <= 1;

                            headerBinding.textViewEmpty.setVisibility(
                                    noWords ? View.VISIBLE : View.GONE
                            );

                            if (noWords) {

                                headerBinding.textViewEmpty.setText(
                                        "No matching saved words found."
                                );
                            }
                        }

                        Log.d(
                                TAG,
                                "Displayed " + words.size()
                                        + " saved search results."
                        );
                    });
                }
        );
    }

    // =========================================================
    // MODERN NOTIFICATIONS
    // =========================================================

    private void showDictionaryMessage(String message) {

        if (!isAdded() || binding == null) {
            return;
        }

        View rootView = binding.getRoot();

        Snackbar snackbar = Snackbar.make(
                rootView,
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
    // ILOKANO PRONUNCIATION
    // =========================================================

    private void speakWord(Word word) {

        if (word == null || !isAdded()) {
            return;
        }

        if (tts == null || !isTtsReady) {

            showDictionaryMessage(
                    "Speech is still initializing. Please try again."
            );

            return;
        }

        String textToSpeak = word.getIlokanoWord();

        if (textToSpeak == null || textToSpeak.trim().isEmpty()) {

            showDictionaryMessage(
                    "Ilokano pronunciation is unavailable."
            );

            return;
        }

        try {

            tts.stop();

            int result = tts.speak(
                    textToSpeak.trim(),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "dictionary_word_" + System.currentTimeMillis()
            );

            if (result == TextToSpeech.ERROR) {

                Log.e(
                        TTS_TAG,
                        "Unable to generate speech."
                );

                showDictionaryMessage(
                        "Unable to generate pronunciation."
                );
            }

        } catch (Exception e) {

            Log.e(
                    TTS_TAG,
                    "Error speaking dictionary word.",
                    e
            );

            showDictionaryMessage(
                    "Unable to generate pronunciation."
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

        dataGeneration++;

        isLoading = false;
        onlineResultApplied = false;
        headerInitialized = false;
        initialLoadScheduled = false;

        if (binding != null) {

            if (paginationScrollListener != null) {

                binding.recyclerViewDictionary.removeOnScrollListener(
                        paginationScrollListener
                );
            }

            binding.dictionaryFastScroller.setScrollCallback(null);

            binding.dictionaryFastScroller.setOnReachBottomListener(
                    null
            );

            binding.dictionaryFastScroller.attachToRecyclerView(null);

            binding.recyclerViewDictionary.setAdapter(null);
        }

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

        isTtsReady = false;

        if (repository != null) {

            repository.close();
            repository = null;
        }

        headerBinding = null;
        headerAdapter = null;
        dictionaryAdapter = null;
        concatAdapter = null;
        paginationScrollListener = null;
        apiService = null;

        binding = null;

        super.onDestroyView();
    }
}


package com.example.speaksmartbaguio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.databinding.FragmentPhrasebookBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.PhraseRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.NetworkUtil;
import com.example.speaksmartbaguio.utils.SettingsMenuHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class PhrasebookFragment extends Fragment {

    private FragmentPhrasebookBinding binding;
    private PhrasebookAdapter adapter;
    private ApiService apiService;
    private PhraseRepository repository;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private String selectedLanguage = "English";
    private String pendingSearchQuery = "";

    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean isApplyingIncomingSearch = false;

    private static final int PAGE_SIZE = 100;

    private static final String PREFS_NAME = "PhrasePrefs";
    private static final String KEY_LANGUAGE = "selectedLanguage";

    private final Handler searchHandler =
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

        apiService = ApiService.getInstance();
        repository = new PhraseRepository(requireContext());

        setupTextToSpeech();
        setupRecyclerView();
        setupLanguageDropdown();
        setupSearch();
        setupSettingsMenuButton();

        // Read the search request sent by HomeActivity.
        handleIncomingSearch();

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                view
        );

        // When a search was requested, search directly
        // instead of first loading every saved phrase.
        if (!pendingSearchQuery.isEmpty()) {

            loadPhrases(true);

        } else {

            loadOfflinePhrases();

            if (NetworkUtil.isOnline(requireContext())) {
                loadPhrases(true);
            }
        }
    }

    // =========================================================
    // INCOMING SEARCH FROM HOME
    // =========================================================

    private void handleIncomingSearch() {

        if (!isAdded() || binding == null) {
            return;
        }

        Intent intent = requireActivity().getIntent();

        if (intent == null
                || !intent.hasExtra(MainActivity.EXTRA_PHRASEBOOK_SEARCH)) {
            return;
        }

        String query = intent.getStringExtra(
                MainActivity.EXTRA_PHRASEBOOK_SEARCH
        );

        // Consume the request so it is not applied again unnecessarily.
        intent.removeExtra(MainActivity.EXTRA_PHRASEBOOK_SEARCH);

        if (query == null || query.trim().isEmpty()) {
            return;
        }

        pendingSearchQuery = query.trim();

        if (adapter != null) {
            adapter.setSearchQuery(pendingSearchQuery);
        }

        // Avoid scheduling another search through the TextWatcher.
        isApplyingIncomingSearch = true;

        binding.searchBar.setText(pendingSearchQuery);
        binding.searchBar.setSelection(
                binding.searchBar.getText().length()
        );

        isApplyingIncomingSearch = false;
    }

    // =========================================================
    // SHARED SETTINGS MENU
    // =========================================================

    private void setupSettingsMenuButton() {

        if (binding == null) {
            return;
        }

        binding.buttonSettingsMenu.setOnClickListener(
                v -> SettingsMenuHelper.show(v)
        );
    }

    // =========================================================
    // ANDROID TEXT-TO-SPEECH
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
                                "PHRASEBOOK_TTS",
                                "Android Text-to-Speech initialization failed."
                        );

                        return;
                    }

                    tts.setPitch(0.95f);
                    tts.setSpeechRate(0.85f);

                    ttsReady = setBestAvailableTtsLanguage();

                    if (!ttsReady) {

                        Log.e(
                                "PHRASEBOOK_TTS",
                                "No supported TTS language was found."
                        );
                    }
                }
        );
    }

    private boolean setBestAvailableTtsLanguage() {

        if (tts == null) {
            return false;
        }

        Locale[] preferredLocales = new Locale[] {
                new Locale("fil", "PH"),
                new Locale("tl", "PH"),
                new Locale("en", "PH"),
                Locale.US
        };

        for (Locale locale : preferredLocales) {

            try {

                int availability =
                        tts.isLanguageAvailable(locale);

                if (availability != TextToSpeech.LANG_MISSING_DATA
                        && availability != TextToSpeech.LANG_NOT_SUPPORTED) {

                    int result = tts.setLanguage(locale);

                    if (result != TextToSpeech.LANG_MISSING_DATA
                            && result != TextToSpeech.LANG_NOT_SUPPORTED) {

                        Log.d(
                                "PHRASEBOOK_TTS",
                                "Selected TTS language: " + locale
                        );

                        return true;
                    }
                }

            } catch (IllegalArgumentException e) {

                Log.w(
                        "PHRASEBOOK_TTS",
                        "TTS language could not be selected: " + locale,
                        e
                );
            }
        }

        return false;
    }

    // =========================================================
    // RECYCLER VIEW + CUSTOM FAST SCROLLER
    // =========================================================

    private void setupRecyclerView() {

        adapter = new PhrasebookAdapter(
                new ArrayList<>(),
                this::speakPhrase,
                selectedLanguage
        );

        adapter.setSearchQuery(pendingSearchQuery);

        // Existing adapter pagination.
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

        binding.recyclerViewPhrasebook.setAdapter(adapter);

        // =====================================================
        // CUSTOM DICTIONARY-STYLE FAST SCROLLER
        // =====================================================

        binding.phrasebookFastScroller.attachToRecyclerView(
                binding.recyclerViewPhrasebook
        );

        // Load another API page when the scrollbar
        // is dragged close to the bottom.
        binding.phrasebookFastScroller.setOnReachBottomListener(
                () -> {

                    if (!isLoading && hasMore) {
                        loadPhrases(false);
                    }
                }
        );

        // =====================================================
        // EXISTING RECYCLER VIEW PAGINATION
        // =====================================================

        binding.recyclerViewPhrasebook.addOnScrollListener(
                new RecyclerView.OnScrollListener() {

                    @Override
                    public void onScrolled(
                            @NonNull RecyclerView recyclerView,
                            int dx,
                            int dy
                    ) {

                        super.onScrolled(recyclerView, dx, dy);

                        if (dy <= 0 || isLoading || !hasMore) {
                            return;
                        }

                        int totalCount = adapter.getItemCount();

                        int lastVisible =
                                layoutManager.findLastVisibleItemPosition();

                        if (totalCount > 0
                                && lastVisible >= totalCount - 3) {

                            loadPhrases(false);
                        }
                    }
                }
        );
    }

    // =========================================================
    // LANGUAGE DROPDOWN
    // =========================================================

    private void setupLanguageDropdown() {

        final String[] languages = {
                "English",
                "Tagalog"
        };

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

        binding.languageDropdown.setAdapter(dropdownAdapter);

        binding.languageDropdown.setDropDownBackgroundResource(
                R.drawable.dropdown_glass_background
        );

        binding.languageDropdown.setKeyListener(null);
        binding.languageDropdown.setFocusable(false);
        binding.languageDropdown.setInputType(InputType.TYPE_NULL);
        binding.languageDropdown.setThreshold(0);

        binding.languageDropdownLayout.setHint(
                "Select Target Language"
        );

        binding.languageDropdown.setText(
                selectedLanguage,
                false
        );

        if (adapter != null) {
            adapter.setSelectedLanguage(selectedLanguage);
            adapter.notifyDataSetChanged();
        }

        final boolean[] isDropdownOpen = {false};

        View.OnClickListener toggleDropdown = v -> {

            if (binding == null) {
                return;
            }

            if (isDropdownOpen[0]) {

                binding.languageDropdown.dismissDropDown();
                isDropdownOpen[0] = false;

                return;
            }

            dropdownAdapter.getFilter().filter(
                    "",
                    count -> {

                        if (binding == null) {
                            return;
                        }

                        binding.languageDropdown.setAdapter(
                                dropdownAdapter
                        );

                        binding.languageDropdown.setText(
                                selectedLanguage,
                                false
                        );

                        binding.languageDropdown.showDropDown();
                        isDropdownOpen[0] = true;
                    }
            );
        };

        binding.languageDropdown.setOnClickListener(
                toggleDropdown
        );

        binding.languageDropdownLayout.setEndIconOnClickListener(
                toggleDropdown
        );

        binding.languageDropdown.setOnItemClickListener(
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

                    binding.languageDropdown.setText(
                            selectedLanguage,
                            false
                    );

                    isDropdownOpen[0] = false;

                    if (adapter != null) {

                        adapter.setSelectedLanguage(
                                selectedLanguage
                        );

                        adapter.notifyDataSetChanged();
                    }
                }
        );
    }

    // =========================================================
    // SEARCH
    // =========================================================

    private void setupSearch() {

        binding.searchBar.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                        // Not needed.
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
                                s == null ? "" : s.toString().trim();

                        if (adapter != null) {
                            adapter.setSearchQuery(
                                    pendingSearchQuery
                            );
                        }

                        searchHandler.removeCallbacks(searchRunnable);

                        searchHandler.postDelayed(
                                searchRunnable,
                                300
                        );
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        // Not needed.
                    }
                }
        );
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

        if (!NetworkUtil.isOnline(context)) {

            if (pendingSearchQuery.isEmpty()) {
                loadOfflinePhrases();
            } else {
                searchOfflinePhrases(pendingSearchQuery);
            }

            Toast.makeText(
                    context,
                    "Offline Mode — showing saved phrases",
                    Toast.LENGTH_SHORT
            ).show();

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

            binding.progressBar.setVisibility(View.VISIBLE);

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

                        if (binding == null || !isAdded()) {
                            isLoading = false;
                            return;
                        }

                        // Ignore results from a previous search.
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

                        binding.progressBar.setVisibility(View.GONE);

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
                    }

                    @Override
                    public void onError(String error) {

                        isLoading = false;

                        if (binding == null || !isAdded()) {
                            return;
                        }

                        if (!requestedQuery.equals(pendingSearchQuery)) {
                            loadPhrases(true);
                            return;
                        }

                        binding.progressBar.setVisibility(View.GONE);

                        adapter.setPaginationState(
                                false,
                                hasMore
                        );

                        if (requestedQuery.isEmpty()) {
                            loadOfflinePhrases();
                        } else {
                            searchOfflinePhrases(requestedQuery);
                        }

                        Context currentContext = getContext();

                        if (currentContext != null) {

                            Toast.makeText(
                                    currentContext,
                                    "Unable to refresh phrases. "
                                            + "Showing saved results if available.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                        Log.e(
                                "PHRASEBOOK",
                                "Unable to load phrases: " + error
                        );
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

        // If there is an active search, do not load every phrase.
        if (!pendingSearchQuery.isEmpty()) {
            searchOfflinePhrases(pendingSearchQuery);
            return;
        }

        repository.getAllPhrases(result -> {

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {

                if (binding == null || adapter == null) {
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

                binding.progressBar.setVisibility(View.GONE);
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

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {

                if (binding == null || adapter == null) {
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

                binding.progressBar.setVisibility(View.GONE);
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

        // The adapter includes one footer row.
        return Math.max(
                0,
                adapter.getItemCount() - 1
        );
    }

    private void updateEmptyState(
            int phraseCount,
            String query
    ) {

        if (binding == null) {
            return;
        }

        if (phraseCount == 0) {

            binding.textViewEmpty.setVisibility(View.VISIBLE);

            if (query != null && !query.trim().isEmpty()) {

                binding.textViewEmpty.setText(
                        "No phrases found. Try another search."
                );

            } else {

                binding.textViewEmpty.setText(
                        "No phrases available yet. "
                                + "Connect to the internet to refresh."
                );
            }

            binding.recyclerViewPhrasebook.setVisibility(View.GONE);

        } else {

            binding.textViewEmpty.setVisibility(View.GONE);

            binding.recyclerViewPhrasebook.setVisibility(
                    View.VISIBLE
            );
        }
    }

    // =========================================================
    // ILOKANO PRONUNCIATION
    // =========================================================

    private void speakPhrase(Phrase phrase) {

        if (phrase == null || !isAdded()) {
            return;
        }

        // Prefer the complete Ilokano sentence when available.
        String ilokanoText = phrase.getIlokanoSentence();

        if (ilokanoText == null || ilokanoText.trim().isEmpty()) {
            ilokanoText = phrase.getIlokanoWord();
        }

        if (ilokanoText == null || ilokanoText.trim().isEmpty()) {

            Toast.makeText(
                    requireContext(),
                    "Ilokano phrase is not available.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (tts == null || !ttsReady) {

            Toast.makeText(
                    requireContext(),
                    "Text-to-Speech is still starting. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        tts.stop();

        int result = tts.speak(
                ilokanoText.trim(),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "phrasebook_" + System.currentTimeMillis()
        );

        if (result == TextToSpeech.ERROR) {

            Toast.makeText(
                    requireContext(),
                    "Unable to speak this phrase.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    public void onDestroyView() {

        searchHandler.removeCallbacksAndMessages(null);

        ttsReady = false;

        if (tts != null) {

            try {
                tts.stop();
                tts.shutdown();

            } catch (Exception e) {

                Log.w(
                        "PHRASEBOOK_TTS",
                        "Error shutting down TTS.",
                        e
                );
            }

            tts = null;
        }

        // Detach the custom fast scroller before destroying the view.
        if (binding != null) {

            binding.phrasebookFastScroller.attachToRecyclerView(null);

            binding.phrasebookFastScroller.setOnReachBottomListener(null);
        }

        binding = null;

        super.onDestroyView();
    }
}


package com.example.speaksmartbaguio;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Toast;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.databinding.FragmentDictionaryBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.NetworkUtil;
import com.example.speaksmartbaguio.utils.SettingsMenuHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DictionaryFragment extends Fragment {

    private FragmentDictionaryBinding binding;

    private DictionaryAdapter dictionaryAdapter;
    private ApiService apiService;
    private TextToSpeech tts;
    private MediaPlayer mediaPlayer;
    private DictionaryRepository repository;

    private String selectedLanguage = "English";
    private String selectedSortOrder = DictionaryAdapter.SORT_A_TO_Z;

    private boolean isLoading = false;
    private boolean hasMore = true;

    private int currentPage = 1;

    private static final int PAGE_SIZE = 100;

    private static final String PREFS_NAME = "DictionaryPrefs";
    private static final String KEY_LANGUAGE = "selectedLanguage";
    private static final String KEY_SORT_ORDER = "sortOrder";

    private final Handler searchHandler =
            new Handler(Looper.getMainLooper());

    private String pendingSearchQuery = "";

    public DictionaryFragment() {
        super(R.layout.fragment_dictionary);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentDictionaryBinding.bind(view);

        apiService = ApiService.getInstance();
        repository = new DictionaryRepository(requireContext());

        SharedPreferences preferences =
                requireContext().getSharedPreferences(
                        PREFS_NAME,
                        android.content.Context.MODE_PRIVATE
                );

        // Restore the saved language.
        selectedLanguage = preferences.getString(
                KEY_LANGUAGE,
                "English"
        );

        if (!"English".equals(selectedLanguage)
                && !"Tagalog".equals(selectedLanguage)) {
            selectedLanguage = "English";
        }

        // Restore the saved sorting order.
        selectedSortOrder = preferences.getString(
                KEY_SORT_ORDER,
                DictionaryAdapter.SORT_A_TO_Z
        );

        if (!DictionaryAdapter.SORT_Z_TO_A.equals(selectedSortOrder)) {
            selectedSortOrder = DictionaryAdapter.SORT_A_TO_Z;
        }

        // Remove any old alphabet-filter preference.
        preferences.edit()
                .remove("selectedLetter")
                .apply();

        setupSettingsMenuButton();
        setupTextToSpeech();
        setupRecyclerView();
        setupLanguageDropdown();
        setupFilterPanel();
        setupSearch();

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                binding.getRoot()
        );

        updateSortRadioButtons();

        // Show locally saved words immediately.
        loadOfflineWords();

        // Refresh the dictionary from the API when online.
        if (NetworkUtil.isOnline(requireContext())) {
            loadWords(true);
        }
    }

    // =========================================================
    // SETTINGS MENU
    // =========================================================

    private void setupSettingsMenuButton() {
        binding.buttonSettingsMenu.setOnClickListener(v ->
                SettingsMenuHelper.show(v)
        );
    }

    // =========================================================
    // TEXT TO SPEECH / RECORDED AUDIO SUPPORT
    // =========================================================

    private void setupTextToSpeech() {
        tts = new TextToSpeech(
                requireContext(),
                status -> {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        tts.setPitch(0.95f);
                        tts.setSpeechRate(0.85f);
                        tts.setLanguage(new Locale("fil", "PH"));
                    }
                }
        );
    }
    private void handleIncomingSearch() {

        if (getArguments() != null) {
            String query = getArguments().getString(
                    MainActivity.EXTRA_DICTIONARY_SEARCH
            );

            if (query != null && !query.trim().isEmpty()) {

                pendingSearchQuery = query.trim();

                binding.searchBar.setText(pendingSearchQuery);
                binding.searchBar.setSelection(
                        binding.searchBar.getText().length()
                );
            }
        }
    }
    // =========================================================
    // RECYCLER VIEW AND CUSTOM DRAGGABLE SCROLLBAR
    // =========================================================

    private void setupRecyclerView() {

        // Initialize the adapter using the original constructor.
        dictionaryAdapter = new DictionaryAdapter(
                new ArrayList<>(),
                this::speakWord,
                selectedLanguage
        );

        // Apply the saved sorting order.
        dictionaryAdapter.setSortOrder(selectedSortOrder);

        // Ensure no alphabet filter is active.
        dictionaryAdapter.clearLetterFilter();

        // Keep the adapter's automatic pagination enabled.
        dictionaryAdapter.setOnLoadMoreListener(
                () -> {
                    if (!isLoading && hasMore) {
                        loadWords(false);
                    }
                }
        );

        // Configure the RecyclerView layout manager.
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(requireContext());

        binding.recyclerViewDictionary.setLayoutManager(layoutManager);
        binding.recyclerViewDictionary.setAdapter(dictionaryAdapter);

        // Connect the custom draggable scrollbar to the RecyclerView.
        binding.dictionaryFastScroller.attachToRecyclerView(
                binding.recyclerViewDictionary
        );

        // Request another API page when dragging the scrollbar
        // reaches the bottom of the currently loaded words.
        binding.dictionaryFastScroller.setOnReachBottomListener(
                () -> {
                    if (!isLoading && hasMore) {
                        loadWords(false);
                    }
                }
        );

        // Load more words during normal downward scrolling.
        binding.recyclerViewDictionary.addOnScrollListener(
                new RecyclerView.OnScrollListener() {

                    @Override
                    public void onScrolled(
                            @NonNull RecyclerView recyclerView,
                            int dx,
                            int dy
                    ) {
                        super.onScrolled(recyclerView, dx, dy);

                        // Only load more when scrolling downward.
                        if (dy <= 0 || isLoading || !hasMore) {
                            return;
                        }

                        int lastVisiblePosition =
                                layoutManager.findLastVisibleItemPosition();

                        int totalCount =
                                dictionaryAdapter.getItemCount();

                        // Load another page before reaching the bottom.
                        if (totalCount > 0
                                && lastVisiblePosition >= totalCount - 3) {
                            loadWords(false);
                        }
                    }
                }
        );
    }



    private void setupLanguageDropdown() {

        MaterialAutoCompleteTextView languageDropdown =
                binding.languageDropdown;

        List<String> languages = Arrays.asList(
                "English",
                "Tagalog"
        );

        ArrayAdapter<String> languageAdapter =
                new ArrayAdapter<String>(
                        requireContext(),
                        R.layout.item_language_dropdown,
                        languages
                ) {

                    @NonNull
                    @Override
                    public Filter getFilter() {

                        return new Filter() {

                            @Override
                            protected FilterResults performFiltering(
                                    CharSequence constraint
                            ) {

                                FilterResults results =
                                        new FilterResults();

                                results.values = languages;
                                results.count = languages.size();

                                return results;
                            }

                            @Override
                            protected void publishResults(
                                    CharSequence constraint,
                                    FilterResults results
                            ) {
                                notifyDataSetChanged();
                            }
                        };
                    }
                };

        languageDropdown.setAdapter(languageAdapter);

        languageDropdown.setDropDownBackgroundResource(
                R.drawable.dropdown_glass_background
        );

        languageDropdown.setThreshold(0);

        languageDropdown.setText(selectedLanguage, false);

        // IMPORTANT:
        // Do not add a custom OnClickListener.
        // Do not override the TextInputLayout end-icon listener.
        // Material Components handles dropdown toggling.

        languageDropdown.setOnItemClickListener(
                (parent, itemView, position, id) -> {

                    Object selectedItem =
                            parent.getItemAtPosition(position);

                    selectedLanguage =
                            String.valueOf(selectedItem);

                    languageDropdown.setText(
                            selectedLanguage,
                            false
                    );

                    // Save selected language.
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

                    // Update dictionary adapter.
                    if (dictionaryAdapter != null) {

                        dictionaryAdapter.setSelectedLanguage(
                                selectedLanguage
                        );
                    }
                }
        );
    }



    // =========================================================
    // SORT FILTER PANEL
    // Only A–Z and Z–A sorting remain.
    // =========================================================

    private void setupFilterPanel() {

        binding.dictionaryFilterPanel.setVisibility(View.GONE);

        updateSortRadioButtons();

        binding.buttonDictionaryFilter.setOnClickListener(v -> {

            boolean isVisible =
                    binding.dictionaryFilterPanel.getVisibility()
                            == View.VISIBLE;

            binding.dictionaryFilterPanel.setVisibility(
                    isVisible ? View.GONE : View.VISIBLE
            );

            if (!isVisible) {
                updateSortRadioButtons();
            }
        });

        binding.sortOrderGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {

                    if (checkedId == binding.radioSortZA.getId()) {
                        selectedSortOrder =
                                DictionaryAdapter.SORT_Z_TO_A;

                    } else if (checkedId
                            == binding.radioSortAZ.getId()) {
                        selectedSortOrder =
                                DictionaryAdapter.SORT_A_TO_Z;
                    }
                }
        );

        // Apply the selected sorting order.
        binding.buttonApplyDictionaryFilters.setOnClickListener(v -> {

            selectedSortOrder =
                    binding.radioSortZA.isChecked()
                            ? DictionaryAdapter.SORT_Z_TO_A
                            : DictionaryAdapter.SORT_A_TO_Z;

            requireContext()
                    .getSharedPreferences(
                            PREFS_NAME,
                            android.content.Context.MODE_PRIVATE
                    )
                    .edit()
                    .putString(KEY_SORT_ORDER, selectedSortOrder)
                    .remove("selectedLetter")
                    .apply();

            if (dictionaryAdapter != null) {
                dictionaryAdapter.setSortOrder(selectedSortOrder);
                dictionaryAdapter.clearLetterFilter();
            }

            binding.dictionaryFilterPanel.setVisibility(View.GONE);

            loadWords(true);
        });

        // Reset sorting to A–Z.
        binding.buttonClearDictionaryFilters.setOnClickListener(v -> {

            selectedSortOrder = DictionaryAdapter.SORT_A_TO_Z;

            requireContext()
                    .getSharedPreferences(
                            PREFS_NAME,
                            android.content.Context.MODE_PRIVATE
                    )
                    .edit()
                    .putString(
                            KEY_SORT_ORDER,
                            DictionaryAdapter.SORT_A_TO_Z
                    )
                    .remove("selectedLetter")
                    .apply();

            updateSortRadioButtons();

            if (dictionaryAdapter != null) {
                dictionaryAdapter.setSortOrder(
                        DictionaryAdapter.SORT_A_TO_Z
                );

                dictionaryAdapter.clearLetterFilter();
            }

            binding.dictionaryFilterPanel.setVisibility(View.GONE);

            loadWords(true);
        });
    }

    private void updateSortRadioButtons() {

        if (binding == null) {
            return;
        }

        if (DictionaryAdapter.SORT_Z_TO_A.equals(selectedSortOrder)) {
            binding.radioSortZA.setChecked(true);
        } else {
            binding.radioSortAZ.setChecked(true);
        }
    }

    // =========================================================
    // SEARCH
    // =========================================================

    private void setupSearch() {

        binding.searchBar.addTextChangedListener(new TextWatcher() {

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
                searchHandler.removeCallbacksAndMessages(null);

                searchHandler.postDelayed(() -> {

                    if (!isAdded() || binding == null) {
                        return;
                    }

                    pendingSearchQuery = s == null
                            ? ""
                            : s.toString().trim();

                    if (dictionaryAdapter != null) {
                        dictionaryAdapter.setSearchQuery(
                                pendingSearchQuery
                        );
                    }

                    loadWords(true);

                }, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed.
            }
        });
    }

    // =========================================================
    // ONLINE DICTIONARY LOADING
    // =========================================================

    private void loadWords(boolean reset) {

        if (!isAdded() || binding == null) {
            return;
        }

        if (!NetworkUtil.isOnline(requireContext())) {

            if (!pendingSearchQuery.isEmpty()) {
                searchOffline(pendingSearchQuery);
            } else {
                loadOfflineWords();
            }

            return;
        }

        if (isLoading) {
            return;
        }

        if (!reset && !hasMore) {
            return;
        }

        if (reset) {
            currentPage = 1;
            hasMore = true;
        }

        isLoading = true;

        binding.progressBar.setVisibility(
                reset ? View.VISIBLE : View.GONE
        );

        dictionaryAdapter.setPaginationState(
                !reset,
                hasMore
        );

        final int requestedPage = currentPage;

        ApiService.ApiCallback<Word> callback =
                new ApiService.ApiCallback<Word>() {

                    @Override
                    public void onSuccess(
                            List<Word> items,
                            boolean more
                    ) {
                        isLoading = false;

                        if (!isAdded() || binding == null) {
                            return;
                        }

                        binding.progressBar.setVisibility(View.GONE);

                        List<Word> safeItems = items == null
                                ? new ArrayList<>()
                                : items;

                        if (reset) {
                            dictionaryAdapter.filterList(
                                    new ArrayList<>(safeItems)
                            );
                        } else {
                            dictionaryAdapter.addItems(safeItems);
                        }

                        if (!safeItems.isEmpty()) {
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

                        binding.textViewEmpty.setVisibility(
                                noVisibleWords && !hasMore
                                        ? View.VISIBLE
                                        : View.GONE
                        );

                        if (!pendingSearchQuery.isEmpty()) {
                            dictionaryAdapter.setSearchQuery(
                                    pendingSearchQuery
                            );
                        }

                        // Continue searching later API pages if the
                        // current page has no visible matching words.
                        if (!pendingSearchQuery.isEmpty()
                                && noVisibleWords
                                && hasMore) {

                            searchHandler.post(
                                    () -> loadWords(false)
                            );
                        }
                    }

                    @Override
                    public void onError(String error) {

                        isLoading = false;

                        if (!isAdded() || binding == null) {
                            return;
                        }

                        binding.progressBar.setVisibility(View.GONE);

                        dictionaryAdapter.setPaginationState(
                                false,
                                hasMore
                        );

                        Log.e("DICTIONARY", error);

                        if (dictionaryAdapter.getItemCount() <= 1) {

                            binding.textViewEmpty.setVisibility(
                                    View.VISIBLE
                            );

                            binding.textViewEmpty.setText(
                                    "Unable to load words. Please try again."
                            );
                        }

                        Toast.makeText(
                                requireContext(),
                                "Could not load dictionary: " + error,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                };

        // Load words without alphabet-letter API filtering.
        apiService.getDictionary(
                requestedPage,
                PAGE_SIZE,
                pendingSearchQuery,
                callback
        );
    }

    // =========================================================
    // OFFLINE DICTIONARY
    // =========================================================

    private void loadOfflineWords() {

        if (!isAdded() || repository == null) {
            return;
        }

        repository.getAllWords(result -> {

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {

                if (!isAdded() || binding == null) {
                    return;
                }

                List<Word> words = result == null
                        ? new ArrayList<>()
                        : EntityMapper.toWordList(result);

                dictionaryAdapter.filterList(words);

                binding.progressBar.setVisibility(View.GONE);

                boolean noWords =
                        dictionaryAdapter.getItemCount() <= 1;

                binding.textViewEmpty.setVisibility(
                        noWords ? View.VISIBLE : View.GONE
                );

                if (noWords) {
                    binding.textViewEmpty.setText(
                            "No saved dictionary words found offline."
                    );
                }
            });
        });
    }

    private void searchOffline(String query) {

        if (!isAdded() || repository == null) {
            return;
        }

        repository.searchWords(query, result -> {

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {

                if (!isAdded() || binding == null) {
                    return;
                }

                List<Word> words = result == null
                        ? new ArrayList<>()
                        : EntityMapper.toWordList(result);

                dictionaryAdapter.setSearchQuery(query);
                dictionaryAdapter.filterList(words);

                binding.progressBar.setVisibility(View.GONE);

                boolean noWords =
                        dictionaryAdapter.getItemCount() <= 1;

                binding.textViewEmpty.setVisibility(
                        noWords ? View.VISIBLE : View.GONE
                );

                if (noWords) {
                    binding.textViewEmpty.setText(
                            "No matching offline words found."
                    );
                }
            });
        });
    }

    // =========================================================
    // RECORDED PRONUNCIATION AUDIO
    // =========================================================

    private void speakWord(Word word) {

        if (word == null) {
            return;
        }

        if (tts != null) {
            tts.stop();
        }

        String audioUrl = word.getTtsUrl();

        if (audioUrl == null || audioUrl.trim().isEmpty()) {

            Toast.makeText(
                    requireContext(),
                    "Recorded pronunciation is not available.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        playAudio(audioUrl.trim());
    }

    private void playAudio(String audioUrl) {

        try {

            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            mediaPlayer = new MediaPlayer();

            mediaPlayer.setDataSource(audioUrl);

            mediaPlayer.setOnPreparedListener(player -> {

                if (isAdded()) {
                    player.start();
                }
            });

            mediaPlayer.setOnErrorListener((player, what, extra) -> {

                Log.e(
                        "DICTIONARY_AUDIO",
                        "Playback error: " + what + ", " + extra
                );

                player.release();

                if (mediaPlayer == player) {
                    mediaPlayer = null;
                }

                if (isAdded()) {
                    Toast.makeText(
                            requireContext(),
                            "Unable to play pronunciation audio.",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (IOException | IllegalStateException e) {

            Log.e(
                    "DICTIONARY_AUDIO",
                    "Audio setup failed",
                    e
            );

            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            if (isAdded()) {
                Toast.makeText(
                        requireContext(),
                        "Unable to play pronunciation audio.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    public void onDestroyView() {

        // Stop pending search tasks.
        searchHandler.removeCallbacksAndMessages(null);

        // Detach the custom scrollbar to prevent it from retaining
        // the old RecyclerView or receiving callbacks after this view
        // has been destroyed.
        if (binding != null) {
            binding.dictionaryFastScroller.attachToRecyclerView(null);
            binding.dictionaryFastScroller.setOnReachBottomListener(null);
        }

        // Release recorded audio playback.
        if (mediaPlayer != null) {

            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
                // The player may already have stopped.
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Shut down TextToSpeech.
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        binding = null;

        super.onDestroyView();
    }
}

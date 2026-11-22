package com.example.speaksmartbaguio;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.databinding.FragmentDictionaryBinding;
import com.example.speaksmartbaguio.databinding.ItemDictionaryHeaderBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.AudioPreferences;
import com.example.speaksmartbaguio.utils.IlokanoTtsHelper;
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

    // Floating "Scroll to Top" arrow button created programmatically.
    private TextView scrollToTopButton;
    private boolean isScrollToTopVisible = false;

    private ApiService apiService;
    private DictionaryRepository repository;

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    private String selectedLanguage = "English";
    private String selectedCategory = "All Categories";

    private String selectedSortOrder =
            DictionaryAdapter.SORT_A_TO_Z;

    private String pendingSearchQuery = "";

    private int currentPage = 1;

    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean isApplyingIncomingSearch = false;
    private boolean incomingSearchHandled = false;

    private boolean headerInitialized = false;
    private boolean initialLoadScheduled = false;

    private long dataGeneration = 0L;

    private boolean onlineResultApplied = false;

    private SharedPreferences dictionaryPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener dictionaryPrefListener;

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

        incomingSearchHandled = false;

        apiService = ApiService.getInstance();

        repository = new DictionaryRepository(
                requireContext().getApplicationContext()
        );

        restorePreferences();
        registerPreferenceListener();

        setupTextToSpeech();
        setupRecyclerView();
        setupScrollToTopButton();
        animatePageEntrance();

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                binding.getRoot()
        );
    }

    private void registerPreferenceListener() {

        dictionaryPrefs = requireContext().getSharedPreferences(
                PREFS_NAME,
                android.content.Context.MODE_PRIVATE
        );

        dictionaryPrefListener = (sharedPreferences, key) -> {
            if (!KEY_LANGUAGE.equals(key) || !isAdded() || binding == null) {
                return;
            }

            String updatedLang = sharedPreferences.getString(
                    KEY_LANGUAGE,
                    "English"
            );

            if (!"English".equals(updatedLang)
                    && !"Tagalog".equals(updatedLang)) {
                updatedLang = "English";
            }

            if (updatedLang.equals(selectedLanguage)) {
                return;
            }

            selectedLanguage = updatedLang;

            if (headerBinding != null) {
                headerBinding.languageDropdown.setText(selectedLanguage, false);
            }

            if (dictionaryAdapter != null) {
                dictionaryAdapter.setSelectedLanguage(selectedLanguage);
            }
        };

        dictionaryPrefs.registerOnSharedPreferenceChangeListener(
                dictionaryPrefListener
        );
    }

    private void animatePageEntrance() {

        if (binding == null) {
            return;
        }

        RecyclerView recyclerView = binding.recyclerViewDictionary;
        recyclerView.setAlpha(0f);
        recyclerView.setTranslationY(dpToPx(16f));
        recyclerView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateButtonTap(@Nullable View view, @Nullable Runnable action) {

        if (view == null) {
            if (action != null) {
                action.run();
            }
            return;
        }

        view.animate().cancel();
        view.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(70L)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100L)
                            .setInterpolator(new OvershootInterpolator(1.3f))
                            .start();

                    if (action != null) {
                        action.run();
                    }
                })
                .start();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Sync language preference if changed from SettingsActivity.
        if (isAdded()) {
            String previousLanguage = selectedLanguage;
            restorePreferences();
            if (!previousLanguage.equals(selectedLanguage)) {
                if (headerBinding != null) {
                    headerBinding.languageDropdown.setText(selectedLanguage, false);
                }
                if (dictionaryAdapter != null) {
                    dictionaryAdapter.setSelectedLanguage(selectedLanguage);
                }
            }
        }

        // If the fragment was already alive and received new search arguments,
        // apply them now once the header is initialized.
        if (headerInitialized && headerBinding != null) {
            Bundle args = getArguments();
            if (args != null && args.containsKey(MainActivity.EXTRA_DICTIONARY_SEARCH)) {
                incomingSearchHandled = false;
                handleIncomingSearch();
                if (dictionaryAdapter != null) {
                    dictionaryAdapter.setSearchQuery(pendingSearchQuery);
                }
                loadWords(true);
            }
        }
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

                        // Show or hide the "Scroll to Top" button based on scroll position.
                        int firstVisible =
                                layoutManager.findFirstVisibleItemPosition();

                        updateScrollToTopButtonVisibility(firstVisible > 0);

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
    // FLOATING "SCROLL TO TOP" ARROW BUTTON & TRANSITION
    // =========================================================

    private void setupScrollToTopButton() {

        if (binding == null || !isAdded()) {
            return;
        }

        View root = binding.getRoot();

        if (!(root instanceof ViewGroup)) {
            return;
        }

        ViewGroup rootGroup = (ViewGroup) root;

        int sizePx = dpToPx(48f);
        int bottomMarginPx = dpToPx(86f);
        int endMarginPx = dpToPx(20f);

        scrollToTopButton = new TextView(requireContext());
        scrollToTopButton.setId(View.generateViewId());
        scrollToTopButton.setText("↑");
        scrollToTopButton.setGravity(Gravity.CENTER);
        scrollToTopButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        scrollToTopButton.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        );
        scrollToTopButton.setTextColor(Color.parseColor("#E0F2FE"));
        scrollToTopButton.setContentDescription("Scroll to top");
        scrollToTopButton.setElevation(dpToPx(8f));

        // Frosted Dark-Teal Glass circle with a glowing cyan border.
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Color.parseColor("#E6163242"));
        circleBg.setStroke(
                dpToPx(1.5f),
                Color.parseColor("#CC70D6ED")
        );
        scrollToTopButton.setBackground(circleBg);

        // Start hidden at the top of the list.
        scrollToTopButton.setVisibility(View.GONE);
        scrollToTopButton.setAlpha(0f);
        scrollToTopButton.setScaleX(0.8f);
        scrollToTopButton.setScaleY(0.8f);
        isScrollToTopVisible = false;

        // Smooth upward transition to top when tapped.
        scrollToTopButton.setOnClickListener(v -> animateScrollToTop());

        // Position in bottom-end corner regardless of root ViewGroup type.
        if (rootGroup instanceof ConstraintLayout) {
            ConstraintLayout.LayoutParams params =
                    new ConstraintLayout.LayoutParams(sizePx, sizePx);
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            params.bottomMargin = bottomMarginPx;
            params.setMarginEnd(endMarginPx);
            params.rightMargin = endMarginPx;
            rootGroup.addView(scrollToTopButton, params);

        } else if (rootGroup instanceof RelativeLayout) {
            RelativeLayout.LayoutParams params =
                    new RelativeLayout.LayoutParams(sizePx, sizePx);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.bottomMargin = bottomMarginPx;
            params.setMarginEnd(endMarginPx);
            params.rightMargin = endMarginPx;
            rootGroup.addView(scrollToTopButton, params);

        } else {
            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(
                            sizePx,
                            sizePx,
                            Gravity.BOTTOM | Gravity.END
                    );
            params.bottomMargin = bottomMarginPx;
            params.setMarginEnd(endMarginPx);
            params.rightMargin = endMarginPx;
            rootGroup.addView(scrollToTopButton, params);
        }
    }

    /**
     * Performs a fast, fluid upward scroll transition to the top of the list
     * and animates the arrow button launching upward.
     */
    private void animateScrollToTop() {

        if (binding == null) {
            return;
        }

        RecyclerView recyclerView = binding.recyclerViewDictionary;
        recyclerView.stopScroll();

        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            int firstVisible =
                    ((LinearLayoutManager) lm).findFirstVisibleItemPosition();

            // If far down the list, snap close to the top first so the
            // smooth scroll transition finishes quickly and fluidly.
            if (firstVisible > 8) {
                recyclerView.scrollToPosition(8);
            }
        }

        recyclerView.post(() -> {
            if (binding != null) {
                binding.recyclerViewDictionary.smoothScrollToPosition(0);
            }
        });

        // Subtle upward-glide entrance transition on the RecyclerView content.
        recyclerView.animate().cancel();
        recyclerView.setTranslationY(dpToPx(18f));
        recyclerView.setAlpha(0.88f);
        recyclerView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Launch-upward exit animation on the arrow button itself.
        if (scrollToTopButton != null) {
            isScrollToTopVisible = false;
            scrollToTopButton.animate().cancel();
            scrollToTopButton.animate()
                    .translationY(-dpToPx(18f))
                    .alpha(0f)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(200L)
                    .withEndAction(() -> {
                        if (scrollToTopButton != null) {
                            scrollToTopButton.setTranslationY(0f);
                            if (!isScrollToTopVisible) {
                                scrollToTopButton.setVisibility(View.GONE);
                            }
                        }
                    })
                    .start();
        }
    }

    private void updateScrollToTopButtonVisibility(boolean show) {

        if (scrollToTopButton == null || isScrollToTopVisible == show) {
            return;
        }

        isScrollToTopVisible = show;

        scrollToTopButton.animate().cancel();

        if (show) {
            scrollToTopButton.setTranslationY(dpToPx(12f));
            scrollToTopButton.setVisibility(View.VISIBLE);
            scrollToTopButton.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            scrollToTopButton.animate()
                    .translationY(dpToPx(10f))
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(150L)
                    .withEndAction(() -> {
                        if (scrollToTopButton != null && !isScrollToTopVisible) {
                            scrollToTopButton.setTranslationY(0f);
                            scrollToTopButton.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private int dpToPx(float dp) {
        return Math.round(
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp,
                        getResources().getDisplayMetrics()
                )
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

        setupSettingsMenuButton();
        setupLanguageDropdown();
        setupCategoryDropdown();
        setupFilterPanel();
        setupSearch();
        updateSortRadioButtons();

        if (headerInitialized) {
            return;
        }

        headerInitialized = true;

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
            applyLocalDictionaryFilter();
        });
    }

    // =========================================================
    // INCOMING SEARCH
    // =========================================================

    private void handleIncomingSearch() {

        if (!isAdded()
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
                MainActivity.EXTRA_DICTIONARY_SEARCH
        );

        // Consume the argument so it cannot be applied again.
        args.remove(MainActivity.EXTRA_DICTIONARY_SEARCH);

        if (query == null || query.trim().isEmpty()) {
            return;
        }

        pendingSearchQuery = query.trim();

        if (dictionaryAdapter != null) {
            dictionaryAdapter.clearLetterFilter();
        }

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
                v -> animateButtonTap(v, () -> SettingsMenuHelper.show(v))
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

                        if (isAdded()) {
                            AudioPreferences.applyToTextToSpeech(
                                    requireContext(),
                                    tts
                            );
                        }

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
    // CATEGORY DROPDOWN
    // =========================================================

    private void setupCategoryDropdown() {

        if (headerBinding == null || !isAdded()) {
            return;
        }

        final List<String> categories = new ArrayList<>();
        categories.add("All Categories");

        MaterialAutoCompleteTextView categoryDropdown =
                headerBinding.categoryDropdown;

        ArrayAdapter<String> categoryAdapter =
                new ArrayAdapter<String>(
                        requireContext(),
                        R.layout.item_language_dropdown,
                        categories
                ) {

                    private final Filter showAllCategoriesFilter =
                            new Filter() {

                                @Override
                                protected FilterResults performFiltering(
                                        CharSequence constraint
                                ) {
                                    FilterResults results =
                                            new FilterResults();
                                    results.values = new ArrayList<>(categories);
                                    results.count = categories.size();
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
                                    List<String> allCats =
                                            (List<String>) results.values;

                                    clear();
                                    addAll(allCats);
                                    notifyDataSetChanged();
                                }
                            };

                    @NonNull
                    @Override
                    public Filter getFilter() {
                        return showAllCategoriesFilter;
                    }
                };

        categoryDropdown.setAdapter(categoryAdapter);
        categoryDropdown.setDropDownBackgroundResource(
                R.drawable.dropdown_glass_background
        );

        categoryDropdown.setKeyListener(null);
        categoryDropdown.setFocusable(false);
        categoryDropdown.setInputType(InputType.TYPE_NULL);
        categoryDropdown.setThreshold(0);

        headerBinding.categoryDropdownLayout.setHint("Select Category");
        categoryDropdown.setText(selectedCategory, false);

        if (repository != null) {
            repository.getAllCategories(result -> {
                mainHandler.post(() -> {
                    if (headerBinding == null || !isAdded()) {
                        return;
                    }

                    if (result != null && !result.isEmpty()) {
                        for (String cat : result) {
                            if (cat != null && !cat.trim().isEmpty() && !categories.contains(cat.trim())) {
                                categories.add(cat.trim());
                            }
                        }
                    } else {
                        String[] defaults = {
                                "Abstract Concepts & Values",
                                "Actions & Movement",
                                "Animals",
                                "Arts & Culture",
                                "Body & Health",
                                "Clothing & Accessories",
                                "Colors & Shapes",
                                "Communication & Language",
                                "Conflict & Security",
                                "Education & Knowledge",
                                "Emotions & Feelings",
                                "Food & Drink",
                                "Grammar & Function Words",
                                "Greetings & Courtesy",
                                "Home & Objects",
                                "Law & Government",
                                "Money & Finance",
                                "Nature & Environment",
                                "Numbers & Quantity",
                                "People & Relationships",
                                "Places & Directions",
                                "Qualities & Descriptions",
                                "Religion & Spirituality",
                                "Sports & Recreation",
                                "Technology & Science",
                                "Thinking & Mental States",
                                "Time & Frequency",
                                "Transportation & Travel",
                                "Weather",
                                "Work & Business"
                        };
                        for (String d : defaults) {
                            if (!categories.contains(d)) {
                                categories.add(d);
                            }
                        }
                    }

                    categoryAdapter.notifyDataSetChanged();
                });
            });
        }

        final boolean[] isDropdownOpen = {false};

        View.OnClickListener toggleDropdown = v -> {
            if (headerBinding == null) {
                return;
            }

            if (isDropdownOpen[0]) {
                categoryDropdown.dismissDropDown();
                isDropdownOpen[0] = false;
                return;
            }

            categoryAdapter.getFilter().filter("", count -> {
                if (headerBinding == null) {
                    return;
                }
                categoryDropdown.setAdapter(categoryAdapter);
                categoryDropdown.setDropDownBackgroundResource(
                        R.drawable.dropdown_glass_background
                );
                categoryDropdown.setText(selectedCategory, false);
                categoryDropdown.showDropDown();
                isDropdownOpen[0] = true;
            });
        };

        categoryDropdown.setOnClickListener(toggleDropdown);
        headerBinding.categoryDropdownLayout.setEndIconOnClickListener(toggleDropdown);

        categoryDropdown.setOnItemClickListener(
                (parent, itemView, position, id) -> {
                    Object selectedItem = parent.getItemAtPosition(position);
                    if (selectedItem == null) return;
                    selectedCategory = selectedItem.toString();
                    categoryDropdown.setText(selectedCategory, false);
                    isDropdownOpen[0] = false;

                    applyLocalDictionaryFilter();
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

        headerBinding.buttonDictionaryFilter.setOnClickListener(v ->
                animateButtonTap(v, () -> {
                    if (headerBinding == null) {
                        return;
                    }

                    boolean isVisible =
                            headerBinding.dictionaryFilterPanel.getVisibility()
                                    == View.VISIBLE;

                    setFilterPanelVisible(!isVisible);

                    if (!isVisible) {
                        updateSortRadioButtons();
                    }
                })
        );

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

        headerBinding.buttonApplyDictionaryFilters.setOnClickListener(v ->
                animateButtonTap(v, () -> {
                    if (headerBinding == null) {
                        return;
                    }

                    selectedSortOrder =
                            headerBinding.radioSortZA.isChecked()
                                    ? DictionaryAdapter.SORT_Z_TO_A
                                    : DictionaryAdapter.SORT_A_TO_Z;

                    saveSortPreference();

                    dictionaryAdapter.setSortOrder(
                            selectedSortOrder
                    );

                    dictionaryAdapter.clearLetterFilter();

                    setFilterPanelVisible(false);

                    applyLocalDictionaryFilter();
                })
        );

        headerBinding.buttonClearDictionaryFilters.setOnClickListener(v ->
                animateButtonTap(v, () -> {
                    if (headerBinding == null) {
                        return;
                    }

                    selectedSortOrder =
                            DictionaryAdapter.SORT_A_TO_Z;

                    saveSortPreference();

                    updateSortRadioButtons();

                    dictionaryAdapter.setSortOrder(
                            selectedSortOrder
                    );

                    dictionaryAdapter.clearLetterFilter();

                    setFilterPanelVisible(false);

                    applyLocalDictionaryFilter();
                })
        );
    }

    private void setFilterPanelVisible(boolean show) {

        if (headerBinding == null) {
            return;
        }

        View panel = headerBinding.dictionaryFilterPanel;
        panel.animate().cancel();

        if (show) {
            panel.setAlpha(0f);
            panel.setTranslationY(-dpToPx(10f));
            panel.setVisibility(View.VISIBLE);
            panel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            if (panel.getVisibility() == View.GONE) {
                return;
            }
            panel.animate()
                    .alpha(0f)
                    .translationY(-dpToPx(8f))
                    .setDuration(160L)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (headerBinding != null) {
                            panel.setVisibility(View.GONE);
                            panel.setTranslationY(0f);
                        }
                    })
                    .start();
        }
    }

    private void updateEmptyState(boolean show, @Nullable String message) {

        if (headerBinding == null) {
            return;
        }

        TextView emptyView = headerBinding.textViewEmpty;

        if (show) {
            if (message != null) {
                emptyView.setText(message);
            }
            if (emptyView.getVisibility() != View.VISIBLE) {
                emptyView.setAlpha(0f);
                emptyView.setVisibility(View.VISIBLE);
                emptyView.animate()
                        .alpha(1f)
                        .setDuration(200L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        } else {
            emptyView.animate().cancel();
            emptyView.setVisibility(View.GONE);
        }
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

                            applyLocalDictionaryFilter();

                            Log.d("DictionarySearch",
                                    "After applyLocalDictionaryFilter: "
                                            + headerBinding.searchBar.hasFocus());

                        }, SEARCH_DELAY);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                }
        );

        headerBinding.searchBar.setOnFocusChangeListener((view, hasFocus) -> {
            Log.d("DictionarySearch", "Search focus changed: " + hasFocus);
        });
    }

    // =========================================================
    // LOCAL DICTIONARY FILTERING & SEARCH
    // =========================================================

    private void applyLocalDictionaryFilter() {

        if (!isAdded() || repository == null) {
            return;
        }

        long requestGeneration = ++dataGeneration;
        String query = pendingSearchQuery == null ? "" : pendingSearchQuery.trim();
        boolean hasCategory = selectedCategory != null
                && !selectedCategory.isEmpty()
                && !"All Categories".equalsIgnoreCase(selectedCategory);

        DictionaryRepository.LoadCallback callback = result -> {
            mainHandler.post(() -> {
                if (!isAdded() || binding == null || dictionaryAdapter == null) {
                    return;
                }
                if (requestGeneration != dataGeneration) {
                    return;
                }

                List<Word> words = result == null
                        ? new ArrayList<>()
                        : EntityMapper.toWordList(result);

                if (!query.isEmpty()) {
                    dictionaryAdapter.setSearchQuery(query);
                } else {
                    dictionaryAdapter.setSearchQuery("");
                }

                dictionaryAdapter.filterList(words);

                if (headerBinding != null) {
                    headerBinding.progressBar.setVisibility(View.GONE);
                    boolean noWords = dictionaryAdapter.getItemCount() <= 1;
                    String emptyMsg = hasCategory
                            ? "No dictionary words found in \"" + selectedCategory + "\"."
                            : "No dictionary words found.";
                    updateEmptyState(noWords, emptyMsg);
                }
            });
        };

        if (headerBinding != null) {
            headerBinding.progressBar.setVisibility(View.VISIBLE);
        }

        if (hasCategory) {
            if (!query.isEmpty()) {
                repository.searchWordsByCategory(query, selectedCategory, callback);
            } else {
                repository.getWordsByCategory(selectedCategory, callback);
            }
        } else {
            if (!query.isEmpty()) {
                repository.searchWords(query, callback);
            } else {
                repository.getAllWords(callback);
            }
        }
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

                            if (headerBinding != null) {

                                headerBinding.progressBar.setVisibility(
                                        View.GONE
                                );
                            }

                            List<Word> safeItems =
                                    items != null
                                            ? items
                                            : new ArrayList<>();

                            // If searching for a specific word (e.g. from Daily Card)
                            // and the online API returned 0 matches on page 1 without more pages,
                            // fall back to local offline search instead of wiping out local results.
                            if (reset && safeItems.isEmpty() && !more && !requestedQuery.isEmpty()) {
                                hasMore = false;
                                onlineResultApplied = false;
                                dictionaryAdapter.setPaginationState(false, false);
                                searchOffline(requestedQuery, requestGeneration);
                                return;
                            }

                            onlineResultApplied = true;

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

                            updateEmptyState(
                                    noVisibleWords && !hasMore,
                                    requestedQuery.isEmpty()
                                            ? "No dictionary words available."
                                            : "No matching words found."
                            );

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

                    updateEmptyState(
                            noWords,
                            "No saved dictionary words found."
                    );
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

                            updateEmptyState(
                                    noWords,
                                    "No matching saved words found."
                            );
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

        TextView snackText = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text
        );
        if (snackText != null) {
            Typeface bodyFont = ResourcesCompat.getFont(
                    requireContext(),
                    R.font.font_body
            );
            if (bodyFont != null) {
                snackText.setTypeface(bodyFont);
            }
        }

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

            int result = IlokanoTtsHelper.speakIlokano(
                    requireContext(),
                    tts,
                    textToSpeak.trim(),
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
        incomingSearchHandled = false;
        isScrollToTopVisible = false;

        if (dictionaryPrefs != null && dictionaryPrefListener != null) {
            dictionaryPrefs.unregisterOnSharedPreferenceChangeListener(
                    dictionaryPrefListener
            );
            dictionaryPrefListener = null;
            dictionaryPrefs = null;
        }

        if (scrollToTopButton != null) {
            scrollToTopButton.animate().cancel();
            scrollToTopButton = null;
        }

        if (headerBinding != null) {
            headerBinding.dictionaryFilterPanel.animate().cancel();
            headerBinding.textViewEmpty.animate().cancel();
        }

        if (binding != null) {

            binding.recyclerViewDictionary.animate().cancel();

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
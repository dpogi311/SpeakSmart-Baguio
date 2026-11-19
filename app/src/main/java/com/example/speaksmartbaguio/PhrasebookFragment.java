package com.example.speaksmartbaguio;

import android.content.Context;
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

import com.example.speaksmartbaguio.databinding.FragmentPhrasebookBinding;
import com.example.speaksmartbaguio.databinding.ItemPhrasebookHeaderBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.PhraseRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.AudioPreferences;
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

    private RecyclerView.OnScrollListener scrollToTopScrollListener;

    // Floating "Scroll to Top" arrow button created programmatically.
    private TextView scrollToTopButton;
    private boolean isScrollToTopVisible = false;

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

    private SharedPreferences phrasePrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener phrasePrefListener;

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
        registerPreferenceListener();
        setupScrollToTopButton();
        animatePageEntrance();

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                view
        );
    }

    private void registerPreferenceListener() {

        phrasePrefs = requireContext().getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        phrasePrefListener = (sharedPreferences, key) -> {
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

            if (adapter != null) {
                adapter.setSelectedLanguage(selectedLanguage);
            }
        };

        phrasePrefs.registerOnSharedPreferenceChangeListener(
                phrasePrefListener
        );
    }

    private void animatePageEntrance() {

        if (binding == null) {
            return;
        }

        RecyclerView recyclerView = binding.recyclerViewPhrasebook;
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
                .scaleX(0.90f)
                .scaleY(0.90f)
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
            loadSavedLanguage();
            if (!previousLanguage.equals(selectedLanguage)) {
                if (headerBinding != null) {
                    headerBinding.languageDropdown.setText(selectedLanguage, false);
                }
                if (adapter != null) {
                    adapter.setSelectedLanguage(selectedLanguage);
                }
            }
        }

        // If the fragment was already alive and received new search arguments,
        // apply them now once the header is initialized.
        if (headerInitialized && headerBinding != null) {
            Bundle args = getArguments();
            if (args != null && args.containsKey(MainActivity.EXTRA_PHRASEBOOK_SEARCH)) {
                incomingSearchHandled = false;
                handleIncomingSearch();
                if (!pendingSearchQuery.isEmpty()) {
                    searchOfflinePhrases(pendingSearchQuery);
                }
            }
        }
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

        scrollToTopScrollListener =
                new RecyclerView.OnScrollListener() {

                    @Override
                    public void onScrolled(
                            @NonNull RecyclerView recyclerView,
                            int dx,
                            int dy
                    ) {
                        int firstVisible =
                                layoutManager.findFirstVisibleItemPosition();

                        updateScrollToTopButtonVisibility(firstVisible > 0);
                    }
                };

        binding.recyclerViewPhrasebook.addOnScrollListener(
                scrollToTopScrollListener
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
        int bottomMarginPx = dpToPx(24f);
        int endMarginPx = dpToPx(28f);

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

        RecyclerView recyclerView = binding.recyclerViewPhrasebook;
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
                binding.recyclerViewPhrasebook.smoothScrollToPosition(0);
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

                // 1. Immediately display matching locally saved phrases first
                //    (since Daily Phrase comes from local storage).
                searchOfflinePhrases(pendingSearchQuery);

                // 2. Also check online phrases if internet is available.
                Context context = getContext();
                if (context != null && NetworkUtil.isOnline(context)) {
                    loadPhrases(true);
                }

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
                v -> animateButtonTap(v, () -> SettingsMenuHelper.show(v))
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

                        if (isAdded()) {
                            AudioPreferences.applyToTextToSpeech(
                                    requireContext(),
                                    tts
                            );
                        }

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

                            // If the online API returned 0 matches for a search query,
                            // fall back to local database search so Daily Phrase entries
                            // (such as full Ilokano sentences) are never wiped out.
                            if (reset && safeItems.isEmpty() && !requestedQuery.isEmpty()) {
                                hasMore = false;
                                adapter.setPaginationState(false, false);
                                searchOfflinePhrases(requestedQuery);
                                return;
                            }

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

        TextView emptyView = headerBinding.textViewEmpty;

        if (phraseCount == 0) {

            if (query != null && !query.trim().isEmpty()) {

                emptyView.setText(
                        "No phrases found. Try another search."
                );

            } else {

                emptyView.setText(
                        "No phrases available yet. "
                                + "Connect to the internet to refresh."
                );
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
            emptyView.setVisibility(
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

            AudioPreferences.applyToTextToSpeech(
                    requireContext(),
                    tts
            );

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
        isScrollToTopVisible = false;

        if (phrasePrefs != null && phrasePrefListener != null) {
            phrasePrefs.unregisterOnSharedPreferenceChangeListener(
                    phrasePrefListener
            );
            phrasePrefListener = null;
            phrasePrefs = null;
        }

        if (scrollToTopButton != null) {
            scrollToTopButton.animate().cancel();
            scrollToTopButton = null;
        }

        if (headerBinding != null) {
            headerBinding.textViewEmpty.animate().cancel();
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

        if (binding != null) {

            binding.recyclerViewPhrasebook.animate().cancel();

            if (scrollToTopScrollListener != null) {
                binding.recyclerViewPhrasebook.removeOnScrollListener(
                        scrollToTopScrollListener
                );
            }

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

        scrollToTopScrollListener = null;
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
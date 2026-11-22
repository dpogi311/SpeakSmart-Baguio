package com.example.speaksmartbaguio;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.entity.FavoriteEntity;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.repository.FavoriteRepository;
import com.example.speaksmartbaguio.repository.PhraseRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.IlokanoTtsHelper;
import com.example.speaksmartbaguio.utils.SettingsMenuHelper;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends Fragment {

    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_WORDS = "WORDS";
    private static final String FILTER_PHRASES = "PHRASES";

    private RecyclerView recyclerViewFavorites;
    private FrameLayout favoritesListContainer;
    private LinearLayout layoutFavoritesEmpty;
    private ProgressBar progressFavorites;
    private TextView textFavoritesCount;

    private TextView chipFavoritesAll;
    private TextView chipFavoritesWords;
    private TextView chipFavoritesPhrases;

    private TextView scrollToTopButton;
    private RecyclerView.OnScrollListener scrollToTopScrollListener;
    private boolean isScrollToTopVisible = false;

    private FavoriteRepository favoriteRepository;
    private DictionaryRepository dictionaryRepository;
    private PhraseRepository phraseRepository;
    private FavoritesAdapter favoritesAdapter;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private String selectedFilter = FILTER_ALL;
    private final List<FavoriteEntity> allFavorites = new ArrayList<>();

    private boolean isLoadingFavorites = false;
    private boolean hasAnimatedInitialContent = false;

    public FavoritesFragment() {
        // Required empty public constructor.
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_favorites,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        hasAnimatedInitialContent = false;

        recyclerViewFavorites = view.findViewById(
                R.id.recyclerViewFavorites
        );

        layoutFavoritesEmpty = view.findViewById(
                R.id.layoutFavoritesEmpty
        );

        progressFavorites = view.findViewById(
                R.id.progressFavorites
        );

        textFavoritesCount = view.findViewById(
                R.id.textFavoritesCount
        );

        chipFavoritesAll = view.findViewById(
                R.id.chipFavoritesAll
        );

        chipFavoritesWords = view.findViewById(
                R.id.chipFavoritesWords
        );

        chipFavoritesPhrases = view.findViewById(
                R.id.chipFavoritesPhrases
        );

        // Connect the shared Settings popup to the gear button.
        View settingsButton = view.findViewById(
                R.id.buttonSettingsMenu
        );

        if (settingsButton != null) {
            settingsButton.setOnClickListener(
                    v -> animateButtonTap(v, () -> SettingsMenuHelper.show(v))
            );
        }

        // Apply saved accessibility preferences to this screen.
        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                view
        );

        favoriteRepository = new FavoriteRepository(
                requireContext().getApplicationContext()
        );
        dictionaryRepository = new DictionaryRepository(
                requireContext().getApplicationContext()
        );
        phraseRepository = new PhraseRepository(
                requireContext().getApplicationContext()
        );

        favoritesAdapter = new FavoritesAdapter(
                favoriteRepository,
                this::loadFavorites,
                this::openFavoriteDetails,
                this::speakFavorite
        );

        recyclerViewFavorites.setAdapter(favoritesAdapter);

        setupFilterChips();
        initTextToSpeech();
        setupScrollToTopButton();
        animatePageEntrance(view);

        loadFavorites();
    }

    private void animatePageEntrance(@NonNull View view) {
        view.setAlpha(0f);
        view.setTranslationY(dpToPx(16f));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
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

        // Refresh whenever the user returns to Favorites.
        if (favoriteRepository != null) {
            loadFavorites();
        }
    }

    // ---------------------------------------------------------
    // FLOATING "SCROLL TO TOP" ARROW BUTTON & TRANSITION
    // ---------------------------------------------------------

    private void setupScrollToTopButton() {

        if (recyclerViewFavorites == null || !isAdded()) {
            return;
        }

        ViewGroup parent = (ViewGroup) recyclerViewFavorites.getParent();
        if (parent == null) {
            return;
        }

        int index = parent.indexOfChild(recyclerViewFavorites);
        ViewGroup.LayoutParams originalParams =
                recyclerViewFavorites.getLayoutParams();

        parent.removeView(recyclerViewFavorites);

        favoritesListContainer = new FrameLayout(requireContext());
        favoritesListContainer.setVisibility(
                recyclerViewFavorites.getVisibility()
        );
        parent.addView(favoritesListContainer, index, originalParams);

        FrameLayout.LayoutParams listParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
        favoritesListContainer.addView(recyclerViewFavorites, listParams);

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

        scrollToTopButton.setVisibility(View.GONE);
        scrollToTopButton.setAlpha(0f);
        scrollToTopButton.setScaleX(0.8f);
        scrollToTopButton.setScaleY(0.8f);
        isScrollToTopVisible = false;

        scrollToTopButton.setOnClickListener(v -> animateScrollToTop());

        FrameLayout.LayoutParams buttonParams =
                new FrameLayout.LayoutParams(
                        sizePx,
                        sizePx,
                        Gravity.BOTTOM | Gravity.END
                );
        buttonParams.bottomMargin = bottomMarginPx;
        buttonParams.setMarginEnd(endMarginPx);
        buttonParams.rightMargin = endMarginPx;

        favoritesListContainer.addView(scrollToTopButton, buttonParams);

        scrollToTopScrollListener =
                new RecyclerView.OnScrollListener() {

                    @Override
                    public void onScrolled(
                            @NonNull RecyclerView recyclerView,
                            int dx,
                            int dy
                    ) {
                        RecyclerView.LayoutManager lm =
                                recyclerView.getLayoutManager();
                        if (lm instanceof LinearLayoutManager) {
                            int firstVisible =
                                    ((LinearLayoutManager) lm)
                                            .findFirstVisibleItemPosition();
                            updateScrollToTopButtonVisibility(firstVisible > 0);
                        }
                    }
                };

        recyclerViewFavorites.addOnScrollListener(scrollToTopScrollListener);
    }

    private void animateScrollToTop() {

        if (recyclerViewFavorites == null) {
            return;
        }

        recyclerViewFavorites.stopScroll();

        RecyclerView.LayoutManager lm =
                recyclerViewFavorites.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            int firstVisible =
                    ((LinearLayoutManager) lm).findFirstVisibleItemPosition();

            if (firstVisible > 8) {
                recyclerViewFavorites.scrollToPosition(8);
            }
        }

        recyclerViewFavorites.post(() -> {
            if (recyclerViewFavorites != null) {
                recyclerViewFavorites.smoothScrollToPosition(0);
            }
        });

        // Subtle upward-glide entrance transition on the RecyclerView content.
        recyclerViewFavorites.animate().cancel();
        recyclerViewFavorites.setTranslationY(dpToPx(18f));
        recyclerViewFavorites.setAlpha(0.88f);
        recyclerViewFavorites.animate()
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

    // ---------------------------------------------------------
    // LOAD FAVORITES
    // ---------------------------------------------------------

    private void loadFavorites() {
        if (favoriteRepository == null || isLoadingFavorites) {
            return;
        }

        isLoadingFavorites = true;

        if (progressFavorites != null) {
            progressFavorites.setVisibility(View.VISIBLE);
        }

        favoriteRepository.getAllFavorites(favorites -> {

            // Do not update a fragment whose view has been destroyed.
            if (!isAdded() || getView() == null) {
                isLoadingFavorites = false;
                return;
            }

            isLoadingFavorites = false;

            if (progressFavorites != null) {
                progressFavorites.setVisibility(View.GONE);
            }

            if (favorites == null) {
                showFavoritesMessage(
                        "Unable to load Favorites. Please try again."
                );

                return;
            }

            displayFavorites(favorites);
        });
    }

    private void showFavoritesMessage(String message) {

        View rootView = getView();
        if (!isAdded() || rootView == null) {
            return;
        }

        Snackbar snackbar = Snackbar.make(
                rootView,
                message,
                Snackbar.LENGTH_LONG
        );

        snackbar.setBackgroundTint(Color.rgb(25, 35, 40));
        snackbar.setTextColor(Color.WHITE);

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

        snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE);
        snackbar.show();
    }

    // ---------------------------------------------------------
    // FILTER CHIPS, TTS & DETAIL NAVIGATION
    // ---------------------------------------------------------

    private void setupFilterChips() {
        if (chipFavoritesAll != null) {
            chipFavoritesAll.setOnClickListener(v -> animateButtonTap(v, () -> selectFilter(FILTER_ALL)));
        }
        if (chipFavoritesWords != null) {
            chipFavoritesWords.setOnClickListener(v -> animateButtonTap(v, () -> selectFilter(FILTER_WORDS)));
        }
        if (chipFavoritesPhrases != null) {
            chipFavoritesPhrases.setOnClickListener(v -> animateButtonTap(v, () -> selectFilter(FILTER_PHRASES)));
        }
        updateFilterChipStyles();
    }

    private void selectFilter(String filter) {
        if (filter.equals(selectedFilter)) {
            return;
        }
        selectedFilter = filter;
        updateFilterChipStyles();
        applyFavoriteFilter();
    }

    private void updateFilterChipStyles() {
        if (!isAdded()) {
            return;
        }
        styleFilterChip(chipFavoritesAll, FILTER_ALL.equals(selectedFilter));
        styleFilterChip(chipFavoritesWords, FILTER_WORDS.equals(selectedFilter));
        styleFilterChip(chipFavoritesPhrases, FILTER_PHRASES.equals(selectedFilter));
    }

    private void styleFilterChip(@Nullable TextView chip, boolean active) {
        if (chip == null || !isAdded()) {
            return;
        }
        int padH = dpToPx(14f);
        int padV = dpToPx(7f);
        if (active) {
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setShape(GradientDrawable.RECTANGLE);
            activeBg.setCornerRadius(dpToPx(16f));
            activeBg.setColor(ContextCompat.getColor(requireContext(), R.color.cyan_primary));
            chip.setBackground(activeBg);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_cyan));
        } else {
            chip.setBackgroundResource(R.drawable.dropdown_glass_background);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.cyan_secondary));
        }
        chip.setPadding(padH, padV, padH, padV);
    }

    private void initTextToSpeech() {
        if (!isAdded()) {
            return;
        }
        tts = new TextToSpeech(requireContext().getApplicationContext(), status -> {
            if (!isAdded() || tts == null) {
                return;
            }
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                IlokanoTtsHelper.configureForIlokano(tts);
            }
        });
    }

    private void speakFavorite(@Nullable FavoriteEntity favorite) {
        if (favorite == null || !isAdded()) {
            return;
        }
        String text = favorite.getIlokanoText() == null ? "" : favorite.getIlokanoText().trim();
        if (text.isEmpty()) {
            showFavoritesMessage("Pronunciation is unavailable.");
            return;
        }
        if (tts == null || !ttsReady) {
            showFavoritesMessage("Speech is still initializing. Please try again.");
            return;
        }
        int result = IlokanoTtsHelper.speakIlokano(
                requireContext(),
                tts,
                text,
                "fav_tts_" + System.currentTimeMillis()
        );
        if (result == TextToSpeech.ERROR) {
            showFavoritesMessage("Unable to generate pronunciation.");
        }
    }

    private void openFavoriteDetails(@Nullable FavoriteEntity favorite) {
        if (favorite == null || !isAdded() || getActivity() == null) {
            return;
        }

        String ilokano = favorite.getIlokanoText().trim();
        String english = favorite.getEnglishText().trim();
        String tagalog = favorite.getTagalogText().trim();
        boolean isDict = FavoriteRepository.TYPE_DICTIONARY.equals(favorite.getType());

        if (isDict && dictionaryRepository != null) {
            dictionaryRepository.searchWords(ilokano, entities -> {
                Word matched = null;
                List<Word> words = EntityMapper.toWordList(entities);
                for (Word w : words) {
                    if (w != null && w.getIlokanoWord().trim().equalsIgnoreCase(ilokano)) {
                        matched = w;
                        if (w.getEnglishTranslation().trim().equalsIgnoreCase(english)) {
                            break;
                        }
                    }
                }
                final Word finalWord = matched;
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || getActivity() == null) {
                        return;
                    }
                    Intent intent = new Intent(requireContext(), WordDetailActivity.class);
                    intent.putExtra("ilokano", finalWord != null ? finalWord.getIlokanoWord() : ilokano);
                    intent.putExtra("english", finalWord != null && !finalWord.getEnglishTranslation().isEmpty() ? finalWord.getEnglishTranslation() : english);
                    intent.putExtra("tagalog", finalWord != null && !finalWord.getTagalogTranslation().isEmpty() ? finalWord.getTagalogTranslation() : tagalog);
                    intent.putExtra("pos", finalWord != null ? finalWord.getPartOfSpeech() : "");
                    intent.putExtra("category", finalWord != null ? finalWord.getCategory() : "");
                    intent.putExtra("definition", finalWord != null ? finalWord.getDefinition() : "");
                    startActivity(intent);
                    requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                });
            });
        } else if (phraseRepository != null) {
            phraseRepository.searchPhrases(ilokano, entities -> {
                Phrase matched = null;
                List<Phrase> phrases = EntityMapper.toPhraseList(entities);
                for (Phrase p : phrases) {
                    if (p != null && p.getIlokanoWord().trim().equalsIgnoreCase(ilokano)) {
                        matched = p;
                        if (p.getEnglishTranslation().trim().equalsIgnoreCase(english)) {
                            break;
                        }
                    }
                }
                final Phrase finalPhrase = matched;
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || getActivity() == null) {
                        return;
                    }
                    Intent intent = new Intent(requireContext(), PhraseDetailActivity.class);
                    intent.putExtra("ilokano", finalPhrase != null ? finalPhrase.getIlokanoWord() : ilokano);
                    intent.putExtra("english", finalPhrase != null && !finalPhrase.getEnglishTranslation().isEmpty() ? finalPhrase.getEnglishTranslation() : english);
                    intent.putExtra("tagalog", finalPhrase != null && !finalPhrase.getTagalogTranslation().isEmpty() ? finalPhrase.getTagalogTranslation() : tagalog);
                    intent.putExtra("pos", finalPhrase != null ? finalPhrase.getPartOfSpeech() : "");
                    intent.putExtra("category", finalPhrase != null ? finalPhrase.getCategory() : "");
                    intent.putExtra("sentence", finalPhrase != null ? finalPhrase.getIlokanoSentence() : "");
                    intent.putExtra("englishSentence", finalPhrase != null ? finalPhrase.getEnglishSentence() : "");
                    startActivity(intent);
                    requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                });
            });
        }
    }

    // ---------------------------------------------------------
    // DISPLAY FAVORITES
    // ---------------------------------------------------------

    private void displayFavorites(
            List<FavoriteEntity> favorites
    ) {
        if (favoritesAdapter == null
                || textFavoritesCount == null
                || layoutFavoritesEmpty == null
                || recyclerViewFavorites == null
                || favorites == null) {
            return;
        }

        allFavorites.clear();
        allFavorites.addAll(favorites);
        applyFavoriteFilter();
    }

    private void applyFavoriteFilter() {
        if (favoritesAdapter == null
                || textFavoritesCount == null
                || layoutFavoritesEmpty == null
                || recyclerViewFavorites == null) {
            return;
        }

        List<FavoriteEntity> filtered = new ArrayList<>();
        for (FavoriteEntity item : allFavorites) {
            if (item == null) {
                continue;
            }
            if (FILTER_WORDS.equals(selectedFilter)) {
                if (FavoriteRepository.TYPE_DICTIONARY.equals(item.getType())) {
                    filtered.add(item);
                }
            } else if (FILTER_PHRASES.equals(selectedFilter)) {
                if (FavoriteRepository.TYPE_PHRASEBOOK.equals(item.getType())) {
                    filtered.add(item);
                }
            } else {
                filtered.add(item);
            }
        }

        favoritesAdapter.setFavorites(filtered);

        int count = filtered.size();
        String newCountText = count == 1
                ? "1 saved item"
                : count + " saved items";

        if (!newCountText.equals(textFavoritesCount.getText().toString())) {
            textFavoritesCount.setText(newCountText);
            textFavoritesCount.animate().cancel();
            textFavoritesCount.setScaleX(0.95f);
            textFavoritesCount.setScaleY(0.95f);
            textFavoritesCount.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }

        boolean isEmpty = filtered.isEmpty();

        if (isEmpty) {
            boolean wasEmptyHidden = layoutFavoritesEmpty.getVisibility() != View.VISIBLE;
            recyclerViewFavorites.setVisibility(View.GONE);
            if (favoritesListContainer != null) {
                favoritesListContainer.setVisibility(View.GONE);
            }
            layoutFavoritesEmpty.setVisibility(View.VISIBLE);

            if (wasEmptyHidden || !hasAnimatedInitialContent) {
                animateViewReveal(layoutFavoritesEmpty);
            }
            updateScrollToTopButtonVisibility(false);
        } else {
            boolean wasListHidden = recyclerViewFavorites.getVisibility() != View.VISIBLE;
            layoutFavoritesEmpty.setVisibility(View.GONE);
            recyclerViewFavorites.setVisibility(View.VISIBLE);
            if (favoritesListContainer != null) {
                favoritesListContainer.setVisibility(View.VISIBLE);
            }

            if (wasListHidden || !hasAnimatedInitialContent) {
                animateViewReveal(recyclerViewFavorites);
            }
        }

        hasAnimatedInitialContent = true;

        // Reapply accessibility preferences after updating the list.
        View currentView = getView();

        if (currentView != null && isAdded()) {
            AccessibilityPreferences.applyToViewTree(
                    requireContext(),
                    currentView
            );
            updateFilterChipStyles();
        }
    }

    private void animateViewReveal(@Nullable View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationY(dpToPx(14f));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    @Override
    public void onDestroyView() {
        View rootView = getView();
        if (rootView != null) {
            rootView.animate().cancel();
        }

        if (scrollToTopButton != null) {
            scrollToTopButton.animate().cancel();
            scrollToTopButton = null;
        }

        if (layoutFavoritesEmpty != null) {
            layoutFavoritesEmpty.animate().cancel();
        }

        if (textFavoritesCount != null) {
            textFavoritesCount.animate().cancel();
        }

        if (recyclerViewFavorites != null) {
            recyclerViewFavorites.animate().cancel();
            if (scrollToTopScrollListener != null) {
                recyclerViewFavorites.removeOnScrollListener(
                        scrollToTopScrollListener
                );
            }
        }

        ttsReady = false;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }

        allFavorites.clear();
        isScrollToTopVisible = false;
        hasAnimatedInitialContent = false;
        scrollToTopScrollListener = null;
        favoritesListContainer = null;
        recyclerViewFavorites = null;
        layoutFavoritesEmpty = null;
        progressFavorites = null;
        textFavoritesCount = null;
        chipFavoritesAll = null;
        chipFavoritesWords = null;
        chipFavoritesPhrases = null;
        favoritesAdapter = null;

        isLoadingFavorites = false;

        super.onDestroyView();
    }
}
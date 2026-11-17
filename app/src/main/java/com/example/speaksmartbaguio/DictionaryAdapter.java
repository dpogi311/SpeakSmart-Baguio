package com.example.speaksmartbaguio;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ReplacementSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.DiffUtil;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.entity.FavoriteEntity;
import com.example.speaksmartbaguio.repository.FavoriteRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DictionaryAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    private static final String HEART_EMPTY = "♡";
    private static final String HEART_FILLED = "♥";

    public static final String SORT_A_TO_Z = "A–Z";
    public static final String SORT_Z_TO_A = "Z–A";

    private final List<Word> allWords = new ArrayList<>();
    private final List<Word> wordList = new ArrayList<>();

    private final OnPlayClickListener playClickListener;
    private String lastAppliedSearchQuery = "";
    private String searchQuery = "";
    private String selectedLanguage = "English";
    private String sortOrder = SORT_A_TO_Z;
    private String selectedLetter = "";

    private boolean isLoading = false;
    private boolean hasMore = true;

    private Runnable loadMoreListener;
    private RecyclerView attachedRecyclerView;

    private FavoriteRepository favoriteRepository;

    // Favorite status is cached instead of queried every time a row is bound.
    private final Set<String> favoriteKeys = new HashSet<>();

    private boolean favoritesLoaded = false;
    private boolean favoritesLoading = false;

    // Used to avoid repeatedly creating a Collator.
    private final Collator collator =
            Collator.getInstance(new Locale("ilo"));

    public interface OnPlayClickListener {
        void onPlayClick(Word word);
    }

    public DictionaryAdapter(
            List<Word> words,
            OnPlayClickListener listener,
            String language
    ) {
        playClickListener = listener;

        selectedLanguage = language == null
                ? "English"
                : language;

        collator.setStrength(Collator.PRIMARY);

        if (words != null) {
            allWords.addAll(words);
        }

        applyFilters();
    }

    // =========================================================
    // LANGUAGE
    // =========================================================

    public void setSelectedLanguage(String language) {

        final String newLanguage = language == null
                ? "English"
                : language;

        runWhenLayoutIsSafe(() -> {
            selectedLanguage = newLanguage;
            notifyItemRangeChanged(0, getItemCount());
        });
    }

    // =========================================================
    // SEARCH
    // =========================================================

    public void setSearchQuery(String query) {
        searchQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
    }

    // =========================================================
    // SORTING
    // =========================================================

    public void setSortOrder(String order) {

        final String newOrder = SORT_Z_TO_A.equals(order)
                ? SORT_Z_TO_A
                : SORT_A_TO_Z;

        runWhenLayoutIsSafe(() -> {
            sortOrder = newOrder;
            applyFilters();
        });
    }

    public String getSortOrder() {
        return sortOrder;
    }

    // =========================================================
    // LETTER FILTER
    // =========================================================

    public void setSelectedLetter(String letter) {

        final String newLetter;

        if (letter == null || letter.trim().isEmpty()) {
            newLetter = "";
        } else {
            newLetter = letter.trim()
                    .substring(0, 1)
                    .toUpperCase(Locale.ROOT);
        }

        runWhenLayoutIsSafe(() -> {
            selectedLetter = newLetter;
            applyFilters();
        });
    }

    public String getSelectedLetter() {
        return selectedLetter;
    }

    public void clearLetterFilter() {

        runWhenLayoutIsSafe(() -> {
            selectedLetter = "";
            applyFilters();
        });
    }

    // =========================================================
    // FILTER AND SORT
    // =========================================================

    private void applyFilters() {

        runWhenLayoutIsSafe(() -> {

            // Keep the existing displayed list unchanged until DiffUtil
            // has calculated the differences.
            final List<Word> oldList = new ArrayList<>(wordList);
            final List<Word> newList = new ArrayList<>();

            for (Word word : allWords) {

                if (word == null) {
                    continue;
                }

                String ilokano =
                        safeText(word.getIlokanoWord()).trim();

                if (!selectedLetter.isEmpty()) {

                    if (ilokano.isEmpty()
                            || !ilokano.regionMatches(
                            true,
                            0,
                            selectedLetter,
                            0,
                            selectedLetter.length()
                    )) {
                        continue;
                    }
                }

                newList.add(word);
            }

            sortWords(newList);

            final String oldQuery = lastAppliedSearchQuery;
            final String newQuery = searchQuery;

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                    new DiffUtil.Callback() {

                        @Override
                        public int getOldListSize() {
                            return oldList.size();
                        }

                        @Override
                        public int getNewListSize() {
                            return newList.size();
                        }

                        @Override
                        public boolean areItemsTheSame(
                                int oldPosition,
                                int newPosition
                        ) {

                            Word oldWord = oldList.get(oldPosition);
                            Word newWord = newList.get(newPosition);

                            if (oldWord == null || newWord == null) {
                                return oldWord == newWord;
                            }

                            String oldId = safeText(oldWord.getId());
                            String newId = safeText(newWord.getId());

                            // Prefer database/API IDs.
                            if (!oldId.isEmpty() || !newId.isEmpty()) {
                                return !oldId.isEmpty()
                                        && oldId.equals(newId);
                            }

                            // Fallback for records without IDs.
                            return createDuplicateKey(
                                    oldWord.getIlokanoWord(),
                                    oldWord.getEnglishTranslation()
                            ).equals(
                                    createDuplicateKey(
                                            newWord.getIlokanoWord(),
                                            newWord.getEnglishTranslation()
                                    )
                            );
                        }

                        @Override
                        public boolean areContentsTheSame(
                                int oldPosition,
                                int newPosition
                        ) {

                            Word oldWord = oldList.get(oldPosition);
                            Word newWord = newList.get(newPosition);

                            if (oldWord == null || newWord == null) {
                                return oldWord == newWord;
                            }

                            // Search highlighting must also be refreshed
                            // when the query changes.
                            if (!oldQuery.equals(newQuery)) {
                                return false;
                            }

                            return safeText(oldWord.getIlokanoWord())
                                    .equals(safeText(newWord.getIlokanoWord()))

                                    && safeText(oldWord.getEnglishTranslation())
                                    .equals(safeText(newWord.getEnglishTranslation()))

                                    && safeText(oldWord.getTagalogTranslation())
                                    .equals(safeText(newWord.getTagalogTranslation()))

                                    && safeText(oldWord.getPartOfSpeech())
                                    .equals(safeText(newWord.getPartOfSpeech()))

                                    && safeText(oldWord.getCategory())
                                    .equals(safeText(newWord.getCategory()))

                                    && safeText(oldWord.getDefinition())
                                    .equals(safeText(newWord.getDefinition()))

                                    && safeText(oldWord.getTtsUrl())
                                    .equals(safeText(newWord.getTtsUrl()))

                                    && safeText(oldWord.getUpdatedAt())
                                    .equals(safeText(newWord.getUpdatedAt()));
                        }
                    }
            );

            // Replace the displayed data only after calculating differences.
            wordList.clear();
            wordList.addAll(newList);

            lastAppliedSearchQuery = newQuery;

            // Dispatch targeted insert, remove, move and change operations.
            diffResult.dispatchUpdatesTo(this);

            // The footer is always the final adapter item.
            int footerPosition = wordList.size();

            if (footerPosition < getItemCount()) {
                notifyItemChanged(footerPosition);
            }
        });
    }

    private void sortWords(List<Word> words) {

        Collections.sort(words, (first, second) -> {

            String firstWord =
                    safeText(first.getIlokanoWord()).trim();

            String secondWord =
                    safeText(second.getIlokanoWord()).trim();

            int result = collator.compare(firstWord, secondWord);

            if (result == 0) {
                result = firstWord.compareToIgnoreCase(secondWord);
            }

            if (SORT_Z_TO_A.equals(sortOrder)) {
                return -Integer.signum(result);
            }

            return result;
        });
    }

    // =========================================================
    // PAGINATION
    // =========================================================

    public void setPaginationState(
            boolean loading,
            boolean moreAvailable
    ) {

        runWhenLayoutIsSafe(() -> {

            boolean changed =
                    isLoading != loading || hasMore != moreAvailable;

            isLoading = loading;
            hasMore = moreAvailable;

            if (changed) {
                notifyPaginationFooterChanged();
            }
        });
    }

    /**
     * Defers adapter updates while RecyclerView is measuring, laying out,
     * or scrolling. The callback is ignored if this adapter has been detached
     * or attached to a different RecyclerView in the meantime.
     */
    private void runWhenLayoutIsSafe(Runnable action) {

        if (action == null) {
            return;
        }

        RecyclerView recyclerView = attachedRecyclerView;

        if (recyclerView != null && recyclerView.isComputingLayout()) {

            recyclerView.post(() -> {

                if (attachedRecyclerView != recyclerView) {
                    return;
                }

                action.run();
            });

        } else {
            action.run();
        }
    }

    private void notifyPaginationFooterChanged() {

        runWhenLayoutIsSafe(() -> {

            int footerPosition = wordList.size();

            if (footerPosition < getItemCount()) {
                notifyItemChanged(footerPosition);
            }
        });
    }

    @Override
    public void onAttachedToRecyclerView(
            @NonNull RecyclerView recyclerView
    ) {
        super.onAttachedToRecyclerView(recyclerView);
        attachedRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(
            @NonNull RecyclerView recyclerView
    ) {
        if (attachedRecyclerView == recyclerView) {
            attachedRecyclerView = null;
        }
        super.onDetachedFromRecyclerView(recyclerView);
    }

    public void setOnLoadMoreListener(Runnable listener) {
        loadMoreListener = listener;
    }

    @Override
    public int getItemViewType(int position) {

        return position == wordList.size()
                ? TYPE_FOOTER
                : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        if (viewType == TYPE_FOOTER) {

            View view = LayoutInflater.from(parent.getContext())
                    .inflate(
                            R.layout.item_pagination_footer,
                            parent,
                            false
                    );

            return new FooterViewHolder(view);
        }

        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.item_list_card,
                        parent,
                        false
                );

        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {

        if (holder instanceof WordViewHolder) {
            bindWord((WordViewHolder) holder, position);

        } else if (holder instanceof FooterViewHolder) {
            bindFooter((FooterViewHolder) holder);
        }
    }

    // =========================================================
    // WORD CARD
    // =========================================================

    private void bindWord(
            WordViewHolder holder,
            int position
    ) {

        if (position < 0 || position >= wordList.size()) {
            return;
        }

        Word word = wordList.get(position);

        if (word == null) {
            return;
        }

        String id = safeText(word.getId());
        String ilokano = safeText(word.getIlokanoWord());
        String english = safeText(word.getEnglishTranslation());
        String tagalog = safeText(word.getTagalogTranslation());
        String partOfSpeech = safeText(word.getPartOfSpeech());
        String category = safeText(word.getCategory());

        holder.boundId = id;
        holder.boundIlokano = ilokano;
        holder.boundEnglish = english;
        holder.boundTagalog = tagalog;

        holder.ilokanoWordText.setText(
                getHighlightedText(ilokano, searchQuery)
        );

        holder.partOfSpeechText.setText(partOfSpeech);

        holder.partOfSpeechText.setVisibility(
                partOfSpeech.isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );

        holder.categoryText.setText(category);

        holder.categoryText.setVisibility(
                category.isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );

        String translation =
                "Tagalog".equals(selectedLanguage)
                        ? tagalog
                        : english;

        holder.selectedTranslationText.setText(
                getHighlightedText(translation, searchQuery)
        );

        // Audio
        holder.playAudioButton.setOnClickListener(v -> {

            if (playClickListener != null) {
                playClickListener.onPlayClick(word);
            }
        });

        // Favorite state from local cache.
        String favoriteKey = createFavoriteKey(
                ilokano,
                english,
                tagalog
        );

        setHeartState(
                holder,
                favoritesLoaded && favoriteKeys.contains(favoriteKey)
        );

        holder.favoriteButton.setOnClickListener(v ->
                toggleFavorite(holder, word)
        );

        // Load favorites only once instead of querying the database
        // every time RecyclerView binds a word.
        loadFavoritesIfNeeded(holder.itemView);

        // Word details
        holder.itemView.setOnClickListener(v -> {

            Intent intent = new Intent(
                    v.getContext(),
                    WordDetailActivity.class
            );

            intent.putExtra("id", id);
            intent.putExtra("ilokano", ilokano);
            intent.putExtra("english", english);
            intent.putExtra("tagalog", tagalog);
            intent.putExtra("pos", partOfSpeech);
            intent.putExtra("category", category);
            intent.putExtra(
                    "definition",
                    safeText(word.getDefinition())
            );
            intent.putExtra(
                    "ttsUrl",
                    safeText(word.getTtsUrl())
            );

            v.getContext().startActivity(intent);
        });

        AccessibilityPreferences.applyToViewTree(
                holder.itemView.getContext(),
                holder.itemView
        );
    }

    // =========================================================
    // FAVORITES CACHE
    // =========================================================

    private FavoriteRepository getFavoriteRepository(View view) {

        if (favoriteRepository == null) {

            favoriteRepository = new FavoriteRepository(
                    view.getContext().getApplicationContext()
            );
        }

        return favoriteRepository;
    }

    private String createFavoriteKey(
            String ilokano,
            String english,
            String tagalog
    ) {

        return safeText(ilokano).toLowerCase(Locale.ROOT)
                + "\u001F"
                + safeText(english).toLowerCase(Locale.ROOT)
                + "\u001F"
                + safeText(tagalog).toLowerCase(Locale.ROOT);
    }

    private void loadFavoritesIfNeeded(View view) {

        if (favoritesLoaded || favoritesLoading) {
            return;
        }

        favoritesLoading = true;

        FavoriteRepository repository =
                getFavoriteRepository(view);

        repository.getFavoritesByType(
                FavoriteRepository.TYPE_DICTIONARY,
                favorites -> {

                    runWhenLayoutIsSafe(() -> {

                        favoriteKeys.clear();

                        if (favorites != null) {

                            for (FavoriteEntity favorite : favorites) {

                                if (favorite == null) {
                                    continue;
                                }

                                favoriteKeys.add(
                                        createFavoriteKey(
                                                favorite.getIlokanoText(),
                                                favorite.getEnglishText(),
                                                favorite.getTagalogText()
                                        )
                                );
                            }
                        }

                        favoritesLoaded = true;
                        favoritesLoading = false;

                        // Refresh visible heart icons safely.
                        notifyItemRangeChanged(0, getItemCount());
                    });
                }
        );
    }

    // =========================================================
    // TOGGLE FAVORITE
    // =========================================================

    private void toggleFavorite(
            WordViewHolder holder,
            Word word
    ) {

        String ilokano = safeText(word.getIlokanoWord());
        String english = safeText(word.getEnglishTranslation());
        String tagalog = safeText(word.getTagalogTranslation());

        String key = createFavoriteKey(
                ilokano,
                english,
                tagalog
        );

        FavoriteRepository repository =
                getFavoriteRepository(holder.itemView);

        if (!favoritesLoaded) {

            Toast.makeText(
                    holder.itemView.getContext(),
                    "Loading favorites. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();

            loadFavoritesIfNeeded(holder.itemView);
            return;
        }

        if (favoriteKeys.contains(key)) {

            removeDictionaryFavorite(
                    holder,
                    repository,
                    key,
                    ilokano,
                    english,
                    tagalog
            );

        } else {

            repository.saveFavorite(
                    FavoriteRepository.TYPE_DICTIONARY,
                    ilokano,
                    english,
                    tagalog,
                    (success, message) -> {

                        if (!success) {

                            showFavoriteToast(
                                    holder,
                                    message,
                                    "Could not save favorite"
                            );

                            return;
                        }

                        favoriteKeys.add(key);

                        if (isSameBoundWord(holder, word)) {
                            setHeartState(holder, true);
                        }

                        showFavoriteToast(
                                holder,
                                message,
                                "Added to favorites"
                        );
                    }
            );
        }
    }

    // =========================================================
    // REMOVE FAVORITE
    // =========================================================

    private void removeDictionaryFavorite(
            WordViewHolder holder,
            FavoriteRepository repository,
            String key,
            String ilokano,
            String english,
            String tagalog
    ) {

        repository.getFavoritesByType(
                FavoriteRepository.TYPE_DICTIONARY,
                favorites -> {

                    FavoriteEntity match = null;

                    if (favorites != null) {

                        for (FavoriteEntity favorite : favorites) {

                            if (favorite == null) {
                                continue;
                            }

                            if (safeText(favorite.getIlokanoText())
                                    .equalsIgnoreCase(ilokano)
                                    && safeText(favorite.getEnglishText())
                                    .equalsIgnoreCase(english)
                                    && safeText(favorite.getTagalogText())
                                    .equalsIgnoreCase(tagalog)) {

                                match = favorite;
                                break;
                            }
                        }
                    }

                    if (match == null) {

                        favoriteKeys.remove(key);

                        if (isSameBoundWordByValues(
                                holder,
                                ilokano,
                                english,
                                tagalog
                        )) {
                            setHeartState(holder, false);
                        }

                        return;
                    }

                    int favoriteId = match.getId();

                    repository.removeFavorite(
                            favoriteId,
                            (success, message) -> {

                                if (success) {
                                    favoriteKeys.remove(key);
                                }

                                if (isSameBoundWordByValues(
                                        holder,
                                        ilokano,
                                        english,
                                        tagalog
                                )) {
                                    setHeartState(holder, !success);
                                }

                                showFavoriteToast(
                                        holder,
                                        message,
                                        success
                                                ? "Removed from favorites"
                                                : "Could not remove favorite"
                                );
                            }
                    );
                }
        );
    }

    private void showFavoriteToast(
            WordViewHolder holder,
            String message,
            String fallback
    ) {

        if (!isSameBoundWordByValues(
                holder,
                holder.boundIlokano,
                holder.boundEnglish,
                holder.boundTagalog
        )) {
            return;
        }

        String toastMessage =
                message != null && !message.trim().isEmpty()
                        ? message
                        : fallback;

        Toast.makeText(
                holder.itemView.getContext(),
                toastMessage,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void setHeartState(
            WordViewHolder holder,
            boolean isFavorite
    ) {

        holder.favoriteButton.setText(
                isFavorite ? HEART_FILLED : HEART_EMPTY
        );

        holder.favoriteButton.setTextColor(
                isFavorite
                        ? Color.parseColor("#06B8A8")
                        : Color.LTGRAY
        );

        holder.favoriteButton.setContentDescription(
                isFavorite
                        ? "Remove from favorites"
                        : "Add to favorites"
        );
    }

    private boolean isSameBoundWord(
            WordViewHolder holder,
            Word word
    ) {

        return isSameBoundWordByValues(
                holder,
                safeText(word.getIlokanoWord()),
                safeText(word.getEnglishTranslation()),
                safeText(word.getTagalogTranslation())
        );
    }

    private boolean isSameBoundWordByValues(
            WordViewHolder holder,
            String ilokano,
            String english,
            String tagalog
    ) {

        return holder.boundIlokano.equals(ilokano)
                && holder.boundEnglish.equals(english)
                && holder.boundTagalog.equals(tagalog);
    }

    // =========================================================
    // PAGINATION FOOTER
    // =========================================================

    private void bindFooter(FooterViewHolder holder) {

        if (isLoading) {

            holder.footerProgress.setVisibility(View.VISIBLE);
            holder.footerText.setVisibility(View.VISIBLE);
            holder.footerText.setText("Loading more words");

            holder.itemView.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(null);

        } else if (hasMore && !wordList.isEmpty()) {

            holder.footerProgress.setVisibility(View.GONE);
            holder.footerText.setVisibility(View.VISIBLE);
            holder.footerText.setText("Load more");

            holder.itemView.setVisibility(View.VISIBLE);

            holder.itemView.setOnClickListener(v -> {

                if (loadMoreListener != null) {
                    loadMoreListener.run();
                }
            });

        } else {

            holder.footerProgress.setVisibility(View.GONE);
            holder.footerText.setVisibility(View.GONE);

            holder.itemView.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(null);
        }

        AccessibilityPreferences.applyToViewTree(
                holder.itemView.getContext(),
                holder.itemView
        );
    }

    @Override
    public int getItemCount() {
        return wordList.size() + 1;
    }

    // =========================================================
    // REPLACE WORDS
    // =========================================================

    public void filterList(List<Word> words) {

        // Copy the incoming list so later repository changes cannot mutate
        // the adapter's backing data unexpectedly.
        final List<Word> replacement = words == null
                ? new ArrayList<>()
                : new ArrayList<>(words);

        runWhenLayoutIsSafe(() -> {
            allWords.clear();
            allWords.addAll(replacement);
            applyFilters();
        });
    }

    // =========================================================
    // ADD API PAGE
    // =========================================================

    public void addItems(List<Word> words) {

        if (words == null || words.isEmpty()) {
            return;
        }

        final List<Word> incomingWords = new ArrayList<>(words);

        runWhenLayoutIsSafe(() -> addItemsNow(incomingWords));
    }

    private void addItemsNow(List<Word> words) {

        // Build lookup sets once.
        // This avoids scanning the entire existing list for every word.
        Set<String> existingIds = new HashSet<>();
        Set<String> existingFallbackKeys = new HashSet<>();

        for (Word existing : allWords) {

            if (existing == null) {
                continue;
            }

            String existingId = safeText(existing.getId());

            if (!existingId.isEmpty()) {
                existingIds.add(existingId);
            } else {
                existingFallbackKeys.add(
                        createDuplicateKey(
                                existing.getIlokanoWord(),
                                existing.getEnglishTranslation()
                        )
                );
            }
        }

        boolean added = false;

        for (Word incoming : words) {

            if (incoming == null) {
                continue;
            }

            String incomingId = safeText(incoming.getId());

            if (!incomingId.isEmpty()) {

                if (existingIds.add(incomingId)) {
                    allWords.add(incoming);
                    added = true;
                }

            } else {

                String fallbackKey = createDuplicateKey(
                        incoming.getIlokanoWord(),
                        incoming.getEnglishTranslation()
                );

                if (existingFallbackKeys.add(fallbackKey)) {
                    allWords.add(incoming);
                    added = true;
                }
            }
        }

        // Only rebuild the displayed list if something was added.
        if (added) {
            applyFilters();
        }
    }

    private String createDuplicateKey(
            String ilokano,
            String english
    ) {

        return safeText(ilokano).toLowerCase(Locale.ROOT)
                + "\u001F"
                + safeText(english).toLowerCase(Locale.ROOT);
    }

    // =========================================================
    // SEARCH HIGHLIGHTING
    // =========================================================

    private CharSequence getHighlightedText(
            String text,
            String query
    ) {

        if (text == null) {
            text = "";
        }

        if (query == null || query.trim().isEmpty()) {
            return text;
        }

        SpannableString spannable = new SpannableString(text);

        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);

        if (lowerQuery.isEmpty()) {
            return text;
        }

        int start = 0;

        while (true) {

            int index = lowerText.indexOf(lowerQuery, start);

            if (index < 0) {
                break;
            }

            spannable.setSpan(
                    new GlassHighlightSpan(),
                    index,
                    index + lowerQuery.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            start = index + lowerQuery.length();
        }

        return spannable;
    }

    // =========================================================
    // FROSTED GLASS HIGHLIGHT
    // =========================================================

    private static class GlassHighlightSpan extends ReplacementSpan {

        private static final int GLASS_COLOR =
                Color.parseColor("#CC24465C");

        private static final int BORDER_COLOR =
                Color.parseColor("#FF70D6ED");

        private static final int TEXT_COLOR =
                Color.parseColor("#FFFFFFFF");

        private final float horizontalPadding;
        private final float verticalPadding;
        private final float cornerRadius;

        private final Paint backgroundPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint borderPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint textPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF backgroundRect = new RectF();

        private GlassHighlightSpan() {

            float density = android.content.res.Resources.getSystem()
                    .getDisplayMetrics().density;

            horizontalPadding = 4f * density;
            verticalPadding = 2f * density;
            cornerRadius = 5f * density;

            backgroundPaint.setColor(GLASS_COLOR);
            backgroundPaint.setStyle(Paint.Style.FILL);

            borderPaint.setColor(BORDER_COLOR);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(Math.max(1f, density));

            textPaint.setColor(TEXT_COLOR);
        }

        @Override
        public int getSize(
                Paint paint,
                CharSequence text,
                int start,
                int end,
                Paint.FontMetricsInt fm
        ) {

            if (fm != null) {

                Paint.FontMetricsInt metrics =
                        paint.getFontMetricsInt();

                fm.ascent = metrics.ascent
                        - (int) Math.ceil(verticalPadding);

                fm.descent = metrics.descent
                        + (int) Math.ceil(verticalPadding);

                fm.top = metrics.top
                        - (int) Math.ceil(verticalPadding);

                fm.bottom = metrics.bottom
                        + (int) Math.ceil(verticalPadding);

                fm.leading = metrics.leading;
            }

            return (int) Math.ceil(
                    paint.measureText(text, start, end)
                            + (horizontalPadding * 2)
            );
        }

        @Override
        public void draw(
                Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int y,
                int bottom,
                Paint paint
        ) {

            float textWidth = paint.measureText(
                    text,
                    start,
                    end
            );

            Paint.FontMetricsInt metrics =
                    paint.getFontMetricsInt();

            float rectTop =
                    y + metrics.ascent - verticalPadding;

            float rectBottom =
                    y + metrics.descent + verticalPadding;

            backgroundRect.set(
                    x,
                    rectTop,
                    x + textWidth + (horizontalPadding * 2),
                    rectBottom
            );

            // Glass background.
            canvas.drawRoundRect(
                    backgroundRect,
                    cornerRadius,
                    cornerRadius,
                    backgroundPaint
            );

            // Cyan outline.
            borderPaint.setStrokeWidth(
                    Math.max(1f, paint.getTextSize() * 0.045f)
            );

            canvas.drawRoundRect(
                    backgroundRect,
                    cornerRadius,
                    cornerRadius,
                    borderPaint
            );

            // Copy the original text styling while preserving
            // the white highlight color.
            textPaint.set(paint);
            textPaint.setAntiAlias(true);
            textPaint.setColor(TEXT_COLOR);

            canvas.drawText(
                    text,
                    start,
                    end,
                    x + horizontalPadding,
                    y,
                    textPaint
            );
        }
    }

    // =========================================================
    // SAFE TEXT
    // =========================================================

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    // =========================================================
    // VIEW HOLDERS
    // =========================================================

    public static class WordViewHolder
            extends RecyclerView.ViewHolder {

        TextView ilokanoWordText;
        TextView partOfSpeechText;
        TextView selectedTranslationText;
        TextView categoryText;
        TextView favoriteButton;

        ImageView playAudioButton;

        String boundId = "";
        String boundIlokano = "";
        String boundEnglish = "";
        String boundTagalog = "";

        public WordViewHolder(@NonNull View itemView) {

            super(itemView);

            ilokanoWordText =
                    itemView.findViewById(R.id.textViewIlokano);

            partOfSpeechText =
                    itemView.findViewById(R.id.textViewPartOfSpeech);

            categoryText =
                    itemView.findViewById(R.id.textViewCategory);

            selectedTranslationText =
                    itemView.findViewById(
                            R.id.textViewSelectedTranslation
                    );

            favoriteButton =
                    itemView.findViewById(R.id.buttonFavorite);

            playAudioButton =
                    itemView.findViewById(R.id.buttonPlayAudio);
        }
    }

    public static class FooterViewHolder
            extends RecyclerView.ViewHolder {

        ProgressBar footerProgress;
        TextView footerText;

        public FooterViewHolder(@NonNull View itemView) {

            super(itemView);

            footerProgress =
                    itemView.findViewById(R.id.footerProgress);

            footerText =
                    itemView.findViewById(R.id.footerText);
        }
    }
}


package com.example.speaksmartbaguio;

import android.content.Intent;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.entity.FavoriteEntity;
import com.example.speaksmartbaguio.repository.FavoriteRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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

    private String searchQuery = "";
    private String selectedLanguage = "English";
    private String sortOrder = SORT_A_TO_Z;
    private String selectedLetter = "";

    private boolean isLoading = false;
    private boolean hasMore = true;

    private Runnable loadMoreListener;
    private FavoriteRepository favoriteRepository;

    public interface OnPlayClickListener {
        void onPlayClick(Word word);
    }

    public DictionaryAdapter(
            List<Word> words,
            OnPlayClickListener listener,
            String language
    ) {
        playClickListener = listener;
        selectedLanguage = language == null ? "English" : language;

        if (words != null) {
            allWords.addAll(words);
        }

        applyFilters();
    }

    // =========================================================
    // LANGUAGE
    // =========================================================

    public void setSelectedLanguage(String language) {
        selectedLanguage = language == null ? "English" : language;
        notifyDataSetChanged();
    }

    // =========================================================
    // SEARCH HIGHLIGHTING
    // =========================================================

    public void setSearchQuery(String query) {
        searchQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        notifyDataSetChanged();
    }

    // =========================================================
    // SORTING
    // =========================================================

    public void setSortOrder(String order) {
        sortOrder = SORT_Z_TO_A.equals(order)
                ? SORT_Z_TO_A
                : SORT_A_TO_Z;

        applyFilters();
    }

    public String getSortOrder() {
        return sortOrder;
    }

    // =========================================================
    // LETTER FILTER
    // =========================================================

    public void setSelectedLetter(String letter) {
        if (letter == null || letter.trim().isEmpty()) {
            selectedLetter = "";
        } else {
            selectedLetter = letter.trim()
                    .substring(0, 1)
                    .toUpperCase(Locale.ROOT);
        }

        applyFilters();
    }

    public String getSelectedLetter() {
        return selectedLetter;
    }

    public void clearLetterFilter() {
        selectedLetter = "";
        applyFilters();
    }

    // =========================================================
    // FILTER AND SORT
    // =========================================================

    private void applyFilters() {
        wordList.clear();

        for (Word word : allWords) {
            if (word == null) {
                continue;
            }

            String ilokano = safeText(word.getIlokanoWord()).trim();

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

            wordList.add(word);
        }

        sortWords();
        notifyDataSetChanged();
    }

    private void sortWords() {
        Collator collator = Collator.getInstance(new Locale("ilo"));
        collator.setStrength(Collator.PRIMARY);

        Collections.sort(wordList, (first, second) -> {
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
        isLoading = loading;
        hasMore = moreAvailable;

        int footerPosition = wordList.size();

        if (footerPosition < getItemCount()) {
            notifyItemChanged(footerPosition);
        }
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
                partOfSpeech.isEmpty() ? View.GONE : View.VISIBLE
        );

        holder.categoryText.setText(category);
        holder.categoryText.setVisibility(
                category.isEmpty() ? View.GONE : View.VISIBLE
        );

        String translation = "Tagalog".equals(selectedLanguage)
                ? tagalog
                : english;

        holder.selectedTranslationText.setText(
                getHighlightedText(translation, searchQuery)
        );

        holder.playAudioButton.setOnClickListener(v -> {
            if (playClickListener != null) {
                playClickListener.onPlayClick(word);
            }
        });

        setHeartState(holder, false);

        holder.favoriteButton.setOnClickListener(v ->
                toggleFavorite(holder, word)
        );

        getFavoriteRepository(holder.itemView).isFavorite(
                FavoriteRepository.TYPE_DICTIONARY,
                ilokano,
                english,
                tagalog,
                isFavorite -> {
                    if (!isSameBoundWord(holder, word)) {
                        return;
                    }

                    setHeartState(holder, isFavorite);
                }
        );

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
            intent.putExtra("definition", safeText(word.getDefinition()));
            intent.putExtra("ttsUrl", safeText(word.getTtsUrl()));

            v.getContext().startActivity(intent);
        });

        AccessibilityPreferences.applyToViewTree(
                holder.itemView.getContext(),
                holder.itemView
        );
    }

    // =========================================================
    // FAVORITES
    // =========================================================

    private FavoriteRepository getFavoriteRepository(View view) {
        if (favoriteRepository == null) {
            favoriteRepository = new FavoriteRepository(
                    view.getContext().getApplicationContext()
            );
        }

        return favoriteRepository;
    }

    private void toggleFavorite(
            WordViewHolder holder,
            Word word
    ) {
        String ilokano = safeText(word.getIlokanoWord());
        String english = safeText(word.getEnglishTranslation());
        String tagalog = safeText(word.getTagalogTranslation());

        FavoriteRepository repository =
                getFavoriteRepository(holder.itemView);

        repository.isFavorite(
                FavoriteRepository.TYPE_DICTIONARY,
                ilokano,
                english,
                tagalog,
                isSaved -> {
                    if (!isSameBoundWord(holder, word)) {
                        return;
                    }

                    if (isSaved) {
                        removeDictionaryFavorite(
                                holder,
                                repository,
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
                                    if (!isSameBoundWord(holder, word)) {
                                        return;
                                    }

                                    setHeartState(holder, success);

                                    String toastMessage =
                                            message != null
                                                    && !message.trim().isEmpty()
                                                    ? message
                                                    : (success
                                                       ? "Added to favorites"
                                                       : "Could not save favorite");

                                    Toast.makeText(
                                            holder.itemView.getContext(),
                                            toastMessage,
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        );
                    }
                }
        );
    }


    private void removeDictionaryFavorite(
            WordViewHolder holder,
            FavoriteRepository repository,
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
                                if (!isSameBoundWordByValues(
                                        holder,
                                        ilokano,
                                        english,
                                        tagalog
                                )) {
                                    return;
                                }

                                setHeartState(holder, !success);

                                String toastMessage =
                                        message != null
                                                && !message.trim().isEmpty()
                                                ? message
                                                : (success
                                                   ? "Removed from favorites"
                                                   : "Could not remove favorite");

                                Toast.makeText(
                                        holder.itemView.getContext(),
                                        toastMessage,
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                    );
                }
        );
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
            holder.footerText.setText("Loading more words...");
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
    // UPDATE WORDS
    // =========================================================

    /**
     * Replaces the loaded data, usually after a new search or refresh.
     */
    public void filterList(List<Word> words) {
        allWords.clear();

        if (words != null) {
            allWords.addAll(words);
        }

        applyFilters();
    }

    /**
     * Adds another API page without losing earlier pages.
     */
    public void addItems(List<Word> words) {
        if (words != null) {
            for (Word incoming : words) {
                if (incoming == null) {
                    continue;
                }

                boolean alreadyExists = false;

                String incomingId = safeText(incoming.getId());
                String incomingIlokano =
                        safeText(incoming.getIlokanoWord());

                for (Word existing : allWords) {
                    if (existing == null) {
                        continue;
                    }

                    String existingId = safeText(existing.getId());

                    if (!incomingId.isEmpty()
                            && incomingId.equals(existingId)) {
                        alreadyExists = true;
                        break;
                    }

                    if (incomingId.isEmpty()
                            && incomingIlokano.equalsIgnoreCase(
                            safeText(existing.getIlokanoWord())
                    )
                            && safeText(incoming.getEnglishTranslation())
                            .equalsIgnoreCase(
                                    safeText(
                                            existing.getEnglishTranslation()
                                    )
                            )) {
                        alreadyExists = true;
                        break;
                    }
                }

                if (!alreadyExists) {
                    allWords.add(incoming);
                }
            }
        }

        applyFilters();
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
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        int start = 0;

        while (true) {
            int index = lowerText.indexOf(lowerQuery, start);

            if (index < 0) {
                break;
            }

            spannable.setSpan(
                    new BackgroundColorSpan(Color.YELLOW),
                    index,
                    index + lowerQuery.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            start = index + lowerQuery.length();
        }

        return spannable;
    }

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

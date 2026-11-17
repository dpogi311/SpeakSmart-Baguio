
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
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.DiffUtil;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.entity.FavoriteEntity;
import com.example.speaksmartbaguio.repository.FavoriteRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PhrasebookAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    private static final String HEART_EMPTY = "♡";
    private static final String HEART_FILLED = "♥";

    private List<Phrase> items;

    private final OnPhraseClickListener clickListener;

    private String searchQuery = "";
    private String selectedLanguage = "English";

    private boolean isLoading = false;
    private boolean hasMore = true;

    private RecyclerView attachedRecyclerView;

    private Runnable loadMoreListener;

    private FavoriteRepository favoriteRepository;

    public interface OnPhraseClickListener {
        void onPlayClicked(Phrase phrase);
    }

    public PhrasebookAdapter(
            List<Phrase> items,
            OnPhraseClickListener listener,
            String selectedLanguage
    ) {
        this.items = items != null
                ? new ArrayList<>(items)
                : new ArrayList<>();

        this.clickListener = listener;

        this.selectedLanguage =
                selectedLanguage != null
                        ? selectedLanguage
                        : "English";
    }

    // ---------------------------------------------------------
    // RECYCLERVIEW ATTACHMENT
    // ---------------------------------------------------------

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

    /**
     * Executes adapter changes only when RecyclerView
     * is not computing its layout.
     */
    private void runWhenRecyclerViewIsReady(
            @NonNull Runnable action
    ) {

        RecyclerView recyclerView = attachedRecyclerView;

        if (recyclerView == null) {
            action.run();
            return;
        }

        if (recyclerView.isComputingLayout()) {

            recyclerView.post(() ->
                    runWhenRecyclerViewIsReady(action)
            );

            return;
        }

        action.run();
    }

    // ---------------------------------------------------------
    // FAVORITE REPOSITORY
    // ---------------------------------------------------------

    private FavoriteRepository getFavoriteRepository(View view) {

        if (favoriteRepository == null) {

            favoriteRepository = new FavoriteRepository(
                    view.getContext().getApplicationContext()
            );
        }

        return favoriteRepository;
    }

    // ---------------------------------------------------------
    // SEARCH
    // ---------------------------------------------------------

    public void setSearchQuery(String query) {

        String newQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        runWhenRecyclerViewIsReady(() -> {

            if (newQuery.equals(searchQuery)) {
                return;
            }

            searchQuery = newQuery;

            // Refresh phrase rows only, not the header or footer.
            if (!items.isEmpty()) {
                notifyItemRangeChanged(0, items.size());
            }
        });
    }

    // ---------------------------------------------------------
    // LANGUAGE
    // ---------------------------------------------------------

    public void setSelectedLanguage(String language) {

        String newLanguage =
                language == null || language.trim().isEmpty()
                        ? "English"
                        : language.trim();

        runWhenRecyclerViewIsReady(() -> {

            if (newLanguage.equals(selectedLanguage)) {
                return;
            }

            selectedLanguage = newLanguage;

            // Only phrase cards need to display the new translation.
            if (!items.isEmpty()) {
                notifyItemRangeChanged(0, items.size());
            }
        });
    }

    // ---------------------------------------------------------
    // PAGINATION STATE
    // ---------------------------------------------------------

    public void setPaginationState(
            boolean isLoading,
            boolean hasMore
    ) {

        runWhenRecyclerViewIsReady(() -> {

            this.isLoading = isLoading;
            this.hasMore = hasMore;

            int footerPosition = getItemCount() - 1;

            if (footerPosition >= 0) {
                notifyItemChanged(footerPosition);
            }
        });
    }

    public void setOnLoadMoreListener(Runnable listener) {
        loadMoreListener = listener;
    }

    // ---------------------------------------------------------
    // ITEM VIEW TYPE
    // ---------------------------------------------------------

    @Override
    public int getItemViewType(int position) {

        return position == items.size()
                ? TYPE_FOOTER
                : TYPE_ITEM;
    }

    // ---------------------------------------------------------
    // CREATE VIEW HOLDER
    // ---------------------------------------------------------

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        LayoutInflater inflater =
                LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_FOOTER) {

            View view = inflater.inflate(
                    R.layout.item_pagination_footer,
                    parent,
                    false
            );

            return new FooterViewHolder(view);
        }

        View view = inflater.inflate(
                R.layout.item_phrasebook_card,
                parent,
                false
        );

        return new PhraseViewHolder(view);
    }

    // ---------------------------------------------------------
    // BIND VIEW HOLDER
    // ---------------------------------------------------------

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {

        if (holder instanceof PhraseViewHolder) {

            bindPhrase((PhraseViewHolder) holder, position);

        } else if (holder instanceof FooterViewHolder) {

            bindFooter((FooterViewHolder) holder);
        }
    }

    // ---------------------------------------------------------
    // PHRASE CARD
    // ---------------------------------------------------------

    private void bindPhrase(
            PhraseViewHolder holder,
            int position
    ) {

        if (position < 0 || position >= items.size()) {
            return;
        }

        Phrase phrase = items.get(position);

        if (phrase == null) {
            return;
        }

        String ilokano = safeText(phrase.getIlokanoWord());
        String english = safeText(phrase.getEnglishTranslation());
        String tagalog = safeText(phrase.getTagalogTranslation());
        String partOfSpeech = safeText(phrase.getPartOfSpeech());

        holder.boundIlokano = ilokano;
        holder.boundEnglish = english;
        holder.boundTagalog = tagalog;

        String translation =
                "Tagalog".equalsIgnoreCase(selectedLanguage)
                        ? tagalog
                        : english;

        holder.textViewIlokano.setText(
                getHighlightedText(ilokano, searchQuery)
        );

        holder.textViewSelected.setText(
                getHighlightedText(translation, searchQuery)
        );

        if (partOfSpeech.trim().isEmpty()) {

            holder.textViewPartOfSpeech.setVisibility(View.GONE);

        } else {

            holder.textViewPartOfSpeech.setVisibility(View.VISIBLE);

            holder.textViewPartOfSpeech.setText(
                    getHighlightedText(partOfSpeech, searchQuery)
            );
        }

        // Open phrase details.
        holder.itemView.setOnClickListener(v -> {

            Intent intent = new Intent(
                    v.getContext(),
                    PhraseDetailActivity.class
            );

            intent.putExtra("id", phrase.getId());
            intent.putExtra("ilokano", ilokano);
            intent.putExtra("english", english);
            intent.putExtra("tagalog", tagalog);
            intent.putExtra("pos", partOfSpeech);

            intent.putExtra(
                    "category",
                    safeText(phrase.getCategory())
            );

            intent.putExtra(
                    "englishSentence",
                    safeText(phrase.getEnglishSentence())
            );

            intent.putExtra(
                    "sentence",
                    safeText(phrase.getIlokanoSentence())
            );

            v.getContext().startActivity(intent);
        });

        // Play pronunciation.
        holder.buttonPlayAudio.setOnClickListener(v -> {

            if (clickListener != null) {
                clickListener.onPlayClicked(phrase);
            }
        });

        holder.buttonPlayAudio.setContentDescription(
                "Play pronunciation for " + ilokano
        );

        // Default favorite state while checking Room.
        setHeartState(holder, false, ilokano);

        holder.buttonFavorite.setOnClickListener(v ->

                toggleFavorite(
                        holder,
                        ilokano,
                        english,
                        tagalog
                )
        );

        // Retrieve favorite state.
        getFavoriteRepository(holder.itemView).isFavorite(
                FavoriteRepository.TYPE_PHRASEBOOK,
                ilokano,
                english,
                tagalog,
                isSaved -> {

                    if (!isSameBoundPhrase(
                            holder,
                            ilokano,
                            english,
                            tagalog
                    )) {
                        return;
                    }

                    setHeartState(holder, isSaved, ilokano);
                }
        );

        AccessibilityPreferences.applyToViewTree(
                holder.itemView.getContext(),
                holder.itemView
        );
    }

    // ---------------------------------------------------------
    // FAVORITES
    // ---------------------------------------------------------

    private void toggleFavorite(
            PhraseViewHolder holder,
            String ilokano,
            String english,
            String tagalog
    ) {

        FavoriteRepository repository =
                getFavoriteRepository(holder.itemView);

        repository.isFavorite(
                FavoriteRepository.TYPE_PHRASEBOOK,
                ilokano,
                english,
                tagalog,
                isSaved -> {

                    if (!isSameBoundPhrase(
                            holder,
                            ilokano,
                            english,
                            tagalog
                    )) {
                        return;
                    }

                    if (isSaved) {

                        removeFavorite(
                                holder,
                                repository,
                                ilokano,
                                english,
                                tagalog
                        );

                    } else {

                        repository.saveFavorite(
                                FavoriteRepository.TYPE_PHRASEBOOK,
                                ilokano,
                                english,
                                tagalog,
                                (success, message) -> {

                                    if (!isSameBoundPhrase(
                                            holder,
                                            ilokano,
                                            english,
                                            tagalog
                                    )) {
                                        return;
                                    }

                                    if (success) {

                                        setHeartState(
                                                holder,
                                                true,
                                                ilokano
                                        );
                                    }

                                    Toast.makeText(
                                            holder.itemView.getContext(),
                                            message,
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        );
                    }
                }
        );
    }

    private void removeFavorite(
            PhraseViewHolder holder,
            FavoriteRepository repository,
            String ilokano,
            String english,
            String tagalog
    ) {

        repository.getFavoritesByType(
                FavoriteRepository.TYPE_PHRASEBOOK,
                favorites -> {

                    if (!isSameBoundPhrase(
                            holder,
                            ilokano,
                            english,
                            tagalog
                    )) {
                        return;
                    }

                    if (favorites == null) {

                        Toast.makeText(
                                holder.itemView.getContext(),
                                "Could not load Favorites.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    FavoriteEntity match = null;

                    for (FavoriteEntity favorite : favorites) {

                        if (ilokano.equals(favorite.getIlokanoText())
                                && english.equals(favorite.getEnglishText())
                                && tagalog.equals(favorite.getTagalogText())) {

                            match = favorite;
                            break;
                        }
                    }

                    if (match == null) {

                        setHeartState(holder, false, ilokano);

                        Toast.makeText(
                                holder.itemView.getContext(),
                                "Favorite was not found.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    repository.removeFavorite(
                            match.getId(),
                            (success, message) -> {

                                if (!isSameBoundPhrase(
                                        holder,
                                        ilokano,
                                        english,
                                        tagalog
                                )) {
                                    return;
                                }

                                if (success) {

                                    setHeartState(
                                            holder,
                                            false,
                                            ilokano
                                    );
                                }

                                Toast.makeText(
                                        holder.itemView.getContext(),
                                        message,
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                    );
                }
        );
    }

    private void setHeartState(
            PhraseViewHolder holder,
            boolean isSaved,
            String ilokano
    ) {

        holder.buttonFavorite.setText(
                isSaved ? HEART_FILLED : HEART_EMPTY
        );

        holder.buttonFavorite.setTextColor(
                isSaved
                        ? Color.parseColor("#06B8A8")
                        : Color.LTGRAY
        );

        holder.buttonFavorite.setContentDescription(
                isSaved
                        ? "Remove " + ilokano + " from Favorites"
                        : "Add " + ilokano + " to Favorites"
        );
    }

    private boolean isSameBoundPhrase(
            PhraseViewHolder holder,
            String ilokano,
            String english,
            String tagalog
    ) {

        return ilokano.equals(holder.boundIlokano)
                && english.equals(holder.boundEnglish)
                && tagalog.equals(holder.boundTagalog);
    }

    // ---------------------------------------------------------
    // PAGINATION FOOTER
    // ---------------------------------------------------------

    private void bindFooter(FooterViewHolder holder) {

        if (isLoading) {

            holder.footerProgress.setVisibility(View.VISIBLE);
            holder.footerText.setVisibility(View.GONE);
            holder.footerText.setOnClickListener(null);

        } else {

            holder.footerProgress.setVisibility(View.GONE);

            if (hasMore && !items.isEmpty()) {

                holder.footerText.setVisibility(View.VISIBLE);
                holder.footerText.setText("Load more phrases");

                holder.footerText.setOnClickListener(v -> {

                    if (loadMoreListener != null && !isLoading) {
                        loadMoreListener.run();
                    }
                });

            } else {

                holder.footerText.setVisibility(View.GONE);
                holder.footerText.setOnClickListener(null);
            }
        }

        AccessibilityPreferences.applyToViewTree(
                holder.itemView.getContext(),
                holder.itemView
        );
    }

    // ---------------------------------------------------------
    // ADAPTER DATA
    // ---------------------------------------------------------

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    public void filterList(List<Phrase> filteredList) {

        List<Phrase> safeList = filteredList != null
                ? new ArrayList<>(filteredList)
                : new ArrayList<>();

        runWhenRecyclerViewIsReady(() -> {

            List<Phrase> oldList = new ArrayList<>(items);

            DiffUtil.DiffResult diffResult =
                    DiffUtil.calculateDiff(
                            new DiffUtil.Callback() {

                                @Override
                                public int getOldListSize() {
                                    return oldList.size();
                                }

                                @Override
                                public int getNewListSize() {
                                    return safeList.size();
                                }

                                @Override
                                public boolean areItemsTheSame(
                                        int oldItemPosition,
                                        int newItemPosition
                                ) {

                                    Phrase oldPhrase =
                                            oldList.get(oldItemPosition);

                                    Phrase newPhrase =
                                            safeList.get(newItemPosition);

                                    return getPhraseIdentity(oldPhrase)
                                            .equals(getPhraseIdentity(newPhrase));
                                }

                                @Override
                                public boolean areContentsTheSame(
                                        int oldItemPosition,
                                        int newItemPosition
                                ) {

                                    Phrase oldPhrase =
                                            oldList.get(oldItemPosition);

                                    Phrase newPhrase =
                                            safeList.get(newItemPosition);

                                    return arePhraseContentsEqual(
                                            oldPhrase,
                                            newPhrase
                                    );
                                }
                            },
                            true
                    );

            items = safeList;

            diffResult.dispatchUpdatesTo(this);

            // Refresh only the footer after the list changes.
            int footerPosition = items.size();

            notifyItemChanged(footerPosition);
        });
    }
    private String getPhraseIdentity(Phrase phrase) {

        if (phrase == null) {
            return "";
        }

        String id = String.valueOf(phrase.getId());

        if (!id.isEmpty()
                && !"null".equals(id)
                && !"0".equals(id)) {

            return "ID:" + id;
        }

        // Fallback for phrases without a valid ID.
        return safeText(phrase.getIlokanoWord())
                + "\u001F"
                + safeText(phrase.getEnglishTranslation())
                + "\u001F"
                + safeText(phrase.getTagalogTranslation())
                + "\u001F"
                + safeText(phrase.getCategory());
    }

    private boolean arePhraseContentsEqual(
            Phrase oldPhrase,
            Phrase newPhrase
    ) {

        if (oldPhrase == null || newPhrase == null) {
            return oldPhrase == newPhrase;
        }

        return safeText(oldPhrase.getIlokanoWord())
                .equals(safeText(newPhrase.getIlokanoWord()))

                && safeText(oldPhrase.getEnglishTranslation())
                .equals(safeText(newPhrase.getEnglishTranslation()))

                && safeText(oldPhrase.getTagalogTranslation())
                .equals(safeText(newPhrase.getTagalogTranslation()))

                && safeText(oldPhrase.getPartOfSpeech())
                .equals(safeText(newPhrase.getPartOfSpeech()))

                && safeText(oldPhrase.getCategory())
                .equals(safeText(newPhrase.getCategory()))

                && safeText(oldPhrase.getEnglishSentence())
                .equals(safeText(newPhrase.getEnglishSentence()))

                && safeText(oldPhrase.getIlokanoSentence())
                .equals(safeText(newPhrase.getIlokanoSentence()));
    }
    public void addItems(List<Phrase> newItems) {

        if (newItems == null || newItems.isEmpty()) {
            return;
        }

        List<Phrase> safeItems = new ArrayList<>(newItems);

        runWhenRecyclerViewIsReady(() -> {

            int startPosition = items.size();

            items.addAll(safeItems);

            notifyItemRangeInserted(
                    startPosition,
                    safeItems.size()
            );
        });
    }

    // ---------------------------------------------------------
    // SAFE TEXT
    // ---------------------------------------------------------

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    // ---------------------------------------------------------
    // SEARCH HIGHLIGHTING
    // ---------------------------------------------------------

    private SpannableString getHighlightedText(
            String text,
            String query
    ) {

        String safeValue = safeText(text);

        SpannableString spannable =
                new SpannableString(safeValue);

        if (query == null
                || query.trim().isEmpty()
                || safeValue.isEmpty()) {

            return spannable;
        }

        String lowerText = safeValue.toLowerCase(Locale.ROOT);
        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);

        int start = 0;

        while (true) {

            int index = lowerText.indexOf(lowerQuery, start);

            if (index < 0) {
                break;
            }

            int end = index + lowerQuery.length();

            spannable.setSpan(
                    new GlassHighlightSpan(),
                    index,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            start = end;
        }

        return spannable;
    }

    // ---------------------------------------------------------
    // FROSTED GLASS SPAN
    // ---------------------------------------------------------

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

        private GlassHighlightSpan() {

            float density = android.content.res.Resources.getSystem()
                    .getDisplayMetrics().density;

            horizontalPadding = 4f * density;
            verticalPadding = 2f * density;
            cornerRadius = 5f * density;
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

            float textWidth = paint.measureText(text, start, end);

            Paint.FontMetricsInt metrics =
                    paint.getFontMetricsInt();

            float rectTop =
                    y + metrics.ascent - verticalPadding;

            float rectBottom =
                    y + metrics.descent + verticalPadding;

            RectF background = new RectF(
                    x,
                    rectTop,
                    x + textWidth + (horizontalPadding * 2),
                    rectBottom
            );

            Paint backgroundPaint = new Paint(paint);
            backgroundPaint.setAntiAlias(true);
            backgroundPaint.setColor(GLASS_COLOR);
            backgroundPaint.setStyle(Paint.Style.FILL);

            canvas.drawRoundRect(
                    background,
                    cornerRadius,
                    cornerRadius,
                    backgroundPaint
            );

            Paint borderPaint = new Paint(paint);
            borderPaint.setAntiAlias(true);
            borderPaint.setColor(BORDER_COLOR);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(
                    Math.max(1f, paint.getTextSize() * 0.045f)
            );

            canvas.drawRoundRect(
                    background,
                    cornerRadius,
                    cornerRadius,
                    borderPaint
            );

            Paint textPaint = new Paint(paint);
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

    // ---------------------------------------------------------
    // VIEW HOLDERS
    // ---------------------------------------------------------

    private static class PhraseViewHolder
            extends RecyclerView.ViewHolder {

        TextView textViewIlokano;
        TextView textViewSelected;
        TextView textViewPartOfSpeech;
        TextView buttonFavorite;

        ImageButton buttonPlayAudio;

        String boundIlokano = "";
        String boundEnglish = "";
        String boundTagalog = "";

        PhraseViewHolder(@NonNull View itemView) {

            super(itemView);

            textViewIlokano = itemView.findViewById(
                    R.id.textViewIlokano
            );

            textViewSelected = itemView.findViewById(
                    R.id.textViewSelectedTranslation
            );

            textViewPartOfSpeech = itemView.findViewById(
                    R.id.textViewPartOfSpeech
            );

            buttonFavorite = itemView.findViewById(
                    R.id.buttonFavorite
            );

            buttonPlayAudio = itemView.findViewById(
                    R.id.buttonPlayAudio
            );
        }
    }

    private static class FooterViewHolder
            extends RecyclerView.ViewHolder {

        ProgressBar footerProgress;
        TextView footerText;

        FooterViewHolder(@NonNull View itemView) {

            super(itemView);

            footerProgress = itemView.findViewById(
                    R.id.footerProgress
            );

            footerText = itemView.findViewById(
                    R.id.footerText
            );
        }
    }
}

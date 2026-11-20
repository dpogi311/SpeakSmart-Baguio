package com.example.speaksmartbaguio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.utils.AccessibilityPreferences;

import java.util.List;
import java.util.Locale;

public class PhraseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    private List<Phrase> items;
    private OnPhraseClickListener clickListener;
    private String searchQuery = "";
    private String selectedLanguage = "English";

    private boolean isLoading = false;
    private boolean hasMore = true;
    private int lastAnimatedPosition = -1;
    private Runnable loadMoreListener;

    public interface OnPhraseClickListener {
        void onPlayClicked(Phrase phrase);
    }

    public PhraseAdapter(
            List<Phrase> items,
            OnPhraseClickListener listener,
            String selectedLanguage
    ) {
        this.items = items;
        this.clickListener = listener;
        this.selectedLanguage = selectedLanguage != null
                ? selectedLanguage
                : "English";
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query != null
                ? query.toLowerCase(Locale.ROOT)
                : "";
        notifyDataSetChanged();
    }

    public void setSelectedLanguage(String language) {
        this.selectedLanguage = language != null
                ? language
                : "English";
        notifyDataSetChanged();
    }

    public void setPaginationState(boolean isLoading, boolean hasMore) {
        this.isLoading = isLoading;
        this.hasMore = hasMore;

        int footerPosition = getItemCount() - 1;
        if (footerPosition >= 0) {
            notifyItemChanged(footerPosition);
        }
    }

    public void setOnLoadMoreListener(Runnable listener) {
        this.loadMoreListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == getPhraseCount()) {
            return TYPE_FOOTER;
        }

        return TYPE_ITEM;
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

        return new PhraseViewHolder(view);
    }

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

    @Override
    public void onViewDetachedFromWindow(
            @NonNull RecyclerView.ViewHolder holder
    ) {
        super.onViewDetachedFromWindow(holder);
        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationY(0f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
    }

    private void animateItemEntrance(View itemView, int position) {
        if (position > lastAnimatedPosition) {
            lastAnimatedPosition = position;
            itemView.animate().cancel();
            itemView.setAlpha(0f);
            itemView.setTranslationY(dpToPx(itemView.getContext(), 14f));
            itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            itemView.setAlpha(1f);
            itemView.setTranslationY(0f);
        }
    }

    private void animateTapBounce(View view, float downScale, Runnable action) {
        view.animate().cancel();
        view.animate()
                .scaleX(downScale)
                .scaleY(downScale)
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

    private float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    private void bindPhrase(PhraseViewHolder holder, int position) {
        if (items == null || position < 0 || position >= items.size()) {
            return;
        }

        Phrase phrase = items.get(position);

        String ilokano = phrase.getIlokanoWord() != null
                ? phrase.getIlokanoWord()
                : "";

        String targetText = "Tagalog".equals(selectedLanguage)
                ? phrase.getTagalogTranslation()
                : phrase.getEnglishTranslation();

        targetText = targetText != null ? targetText : "";

        String partOfSpeech = phrase.getPartOfSpeech() != null
                ? phrase.getPartOfSpeech()
                : "";

        holder.textViewIlokano.setText(
                getHighlightedText(ilokano, searchQuery)
        );

        holder.textViewSelectedTranslation.setText(
                getHighlightedText(targetText, searchQuery)
        );

        if (!partOfSpeech.isEmpty()) {
            holder.textViewPartOfSpeech.setVisibility(View.VISIBLE);
            holder.textViewPartOfSpeech.setText(
                    getHighlightedText(partOfSpeech, searchQuery)
            );
        } else {
            holder.textViewPartOfSpeech.setVisibility(View.GONE);
        }

        String category = phrase.getCategory() != null ? phrase.getCategory().trim() : "";
        if (holder.textViewCategory != null) {
            if (!category.isEmpty()) {
                holder.textViewCategory.setVisibility(View.VISIBLE);
                holder.textViewCategory.setText(category);
            } else {
                holder.textViewCategory.setVisibility(View.GONE);
            }
        }

        // Open the phrase detail page.
        holder.itemView.setOnClickListener(v ->
                animateTapBounce(v, 0.97f, () -> {
                    Context context = v.getContext();
                    Intent intent = new Intent(
                            context,
                            PhraseDetailActivity.class
                    );

                    intent.putExtra("id", phrase.getId());
                    intent.putExtra("ilokano", phrase.getIlokanoWord());
                    intent.putExtra("english", phrase.getEnglishTranslation());
                    intent.putExtra("tagalog", phrase.getTagalogTranslation());
                    intent.putExtra("pos", phrase.getPartOfSpeech());
                    intent.putExtra("category", phrase.getCategory());
                    intent.putExtra("sentence", phrase.getIlokanoSentence());

                    context.startActivity(intent);
                    if (context instanceof Activity) {
                        ((Activity) context).overridePendingTransition(
                                R.anim.slide_in_right,
                                R.anim.slide_out_left
                        );
                    }
                })
        );

        // Preserve the existing audio callback.
        holder.buttonPlay.setOnClickListener(v ->
                animateTapBounce(v, 0.86f, () -> {
                    if (clickListener != null) {
                        clickListener.onPlayClicked(phrase);
                    }
                })
        );

        // Apply saved font-size, contrast, and layout preferences
        // to the phrase card and all of its child views.
        AccessibilityPreferences.applyToViewTree(
                holder.itemView.getContext(),
                holder.itemView
        );

        animateItemEntrance(holder.itemView, position);
    }

    private void bindFooter(FooterViewHolder holder) {
        // Reset listeners first to prevent recycled footer behavior.
        holder.itemView.setOnClickListener(null);

        if (isLoading) {
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.footerText.setVisibility(View.GONE);

        } else if (hasMore && getPhraseCount() > 0) {
            holder.progressBar.setVisibility(View.GONE);
            holder.footerText.setVisibility(View.VISIBLE);
            holder.footerText.setText("Load more");
            holder.footerText.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, 0, 0
            );

            holder.itemView.setOnClickListener(v ->
                    animateTapBounce(holder.footerText, 0.94f, () -> {
                        if (loadMoreListener != null && !isLoading && hasMore) {
                            loadMoreListener.run();
                        }
                    })
            );

        } else {
            holder.progressBar.setVisibility(View.GONE);
            holder.footerText.setVisibility(View.GONE);
        }

        // Apply accessibility settings to the pagination footer.
        AccessibilityPreferences.applyToViewTree(
                holder.itemView.getContext(),
                holder.itemView
        );
    }

    private int getPhraseCount() {
        return items != null ? items.size() : 0;
    }

    @Override
    public int getItemCount() {
        // Keep one footer item for pagination.
        return getPhraseCount() + 1;
    }

    public void filterList(List<Phrase> filteredList) {
        lastAnimatedPosition = -1;
        items = filteredList;
        notifyDataSetChanged();
    }

    public void addItems(List<Phrase> newItems) {
        if (items == null || newItems == null || newItems.isEmpty()) {
            return;
        }

        int start = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    private Spannable getHighlightedText(String text, String query) {
        Spannable spannable = new SpannableString(
                text != null ? text : ""
        );

        if (query != null && !query.isEmpty()) {
            String lowerText = text.toLowerCase(Locale.ROOT);
            int start = lowerText.indexOf(query);

            while (start >= 0) {
                int end = start + query.length();

                spannable.setSpan(
                        new BackgroundColorSpan(Color.YELLOW),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                start = lowerText.indexOf(query, end);
            }
        }

        return spannable;
    }

    static class PhraseViewHolder extends RecyclerView.ViewHolder {

        TextView textViewIlokano;
        TextView textViewSelectedTranslation;
        TextView textViewPartOfSpeech;
        TextView textViewCategory;
        ImageView buttonPlay;

        PhraseViewHolder(@NonNull View itemView) {
            super(itemView);

            textViewIlokano = itemView.findViewById(
                    R.id.textViewIlokano
            );

            textViewSelectedTranslation = itemView.findViewById(
                    R.id.textViewSelectedTranslation
            );

            textViewPartOfSpeech = itemView.findViewById(
                    R.id.textViewPartOfSpeech
            );

            textViewCategory = itemView.findViewById(
                    R.id.textViewCategory
            );

            buttonPlay = itemView.findViewById(
                    R.id.buttonPlayAudio
            );
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {

        ProgressBar progressBar;
        TextView footerText;

        FooterViewHolder(@NonNull View itemView) {
            super(itemView);

            progressBar = itemView.findViewById(
                    R.id.footerProgress
            );

            footerText = itemView.findViewById(
                    R.id.footerText
            );
        }
    }
}
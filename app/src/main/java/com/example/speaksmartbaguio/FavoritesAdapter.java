package com.example.speaksmartbaguio;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.entity.FavoriteEntity;
import com.example.speaksmartbaguio.repository.FavoriteRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class FavoritesAdapter
        extends RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder> {

    public interface OnFavoriteRemovedListener {
        void onFavoriteRemoved();
    }

    public interface OnFavoriteClickListener {
        void onFavoriteClick(FavoriteEntity favorite);
    }

    public interface OnFavoriteSpeakListener {
        void onFavoriteSpeak(FavoriteEntity favorite);
    }

    private final List<FavoriteEntity> favorites = new ArrayList<>();
    private final FavoriteRepository repository;
    private final OnFavoriteRemovedListener removeListener;
    @Nullable
    private final OnFavoriteClickListener clickListener;
    @Nullable
    private final OnFavoriteSpeakListener speakListener;

    private RecyclerView attachedRecyclerView;
    private int lastAnimatedPosition = -1;

    public FavoritesAdapter(
            FavoriteRepository repository,
            OnFavoriteRemovedListener removeListener
    ) {
        this(repository, removeListener, null, null);
    }

    public FavoritesAdapter(
            FavoriteRepository repository,
            OnFavoriteRemovedListener removeListener,
            @Nullable OnFavoriteClickListener clickListener,
            @Nullable OnFavoriteSpeakListener speakListener
    ) {
        this.repository = repository;
        this.removeListener = removeListener;
        this.clickListener = clickListener;
        this.speakListener = speakListener;
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

    @Override
    public void onViewDetachedFromWindow(
            @NonNull FavoriteViewHolder holder
    ) {
        super.onViewDetachedFromWindow(holder);
        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
    }

    public void setFavorites(List<FavoriteEntity> newFavorites) {
        lastAnimatedPosition = -1;
        favorites.clear();

        if (newFavorites != null) {
            favorites.addAll(newFavorites);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_favorite_card,
                parent,
                false
        );

        return new FavoriteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull FavoriteViewHolder holder,
            int position
    ) {
        FavoriteEntity favorite = favorites.get(position);

        String ilokano = safeText(favorite.getIlokanoText());
        String english = safeText(favorite.getEnglishText());
        String tagalog = safeText(favorite.getTagalogText());

        boolean isDictionary =
                FavoriteRepository.TYPE_DICTIONARY.equals(
                        favorite.getType()
                );

        holder.textFavoriteType.setText(
                isDictionary ? "DICTIONARY" : "PHRASEBOOK"
        );

        holder.textFavoriteIlokano.setText(ilokano);

        holder.textFavoriteEnglish.setText(
                "English: " + (english.isEmpty() ? "—" : english)
        );

        holder.textFavoriteTagalog.setText(
                "Tagalog: " + (tagalog.isEmpty() ? "—" : tagalog)
        );

        holder.buttonRemoveFavorite.setContentDescription(
                "Remove " + ilokano + " from Favorites"
        );

        holder.buttonSpeakFavorite.setContentDescription(
                "Play pronunciation for " + ilokano
        );

        holder.buttonSpeakFavorite.setOnClickListener(v -> {
            v.animate().cancel();
            v.animate()
                    .scaleX(0.86f)
                    .scaleY(0.86f)
                    .setDuration(70L)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100L)
                            .setInterpolator(new OvershootInterpolator(1.3f))
                            .start())
                    .start();
            if (speakListener != null) {
                speakListener.onFavoriteSpeak(favorite);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onFavoriteClick(favorite);
            }
        });

        holder.buttonRemoveFavorite.setOnClickListener(v -> {
            holder.buttonRemoveFavorite.setEnabled(false);

            v.animate().cancel();
            v.animate()
                    .scaleX(0.86f)
                    .scaleY(0.86f)
                    .setDuration(70L)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100L)
                            .setInterpolator(new OvershootInterpolator(1.3f))
                            .start())
                    .start();

            repository.removeFavorite(
                    favorite.getId(),
                    (success, message) -> {
                        // The repository callback may run asynchronously.
                        holder.buttonRemoveFavorite.setEnabled(true);

                        String displayMessage =
                                message != null && !message.trim().isEmpty()
                                        ? message
                                        : (success
                                        ? "Removed from favorites"
                                        : "Could not remove favorite");

                        showModernNotification(v, displayMessage);

                        if (success && removeListener != null) {
                            holder.itemView.animate().cancel();
                            holder.itemView.animate()
                                    .alpha(0f)
                                    .translationX(dpToPx(holder.itemView.getContext(), 32f))
                                    .scaleX(0.95f)
                                    .scaleY(0.95f)
                                    .setDuration(180L)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .withEndAction(() -> {
                                        holder.itemView.setAlpha(1f);
                                        holder.itemView.setTranslationX(0f);
                                        holder.itemView.setScaleX(1f);
                                        holder.itemView.setScaleY(1f);
                                        removeListener.onFavoriteRemoved();
                                    })
                                    .start();
                        }
                    }
            );
        });

        // Apply saved font-size, contrast, and layout preferences
        // to this dynamically created favorite card.
        AccessibilityPreferences.applyToViewTree(
                holder.itemView.getContext(),
                holder.itemView
        );

        animateItemEntrance(holder.itemView, position);
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

    private float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    private void showModernNotification(View anchorView, String message) {

        View targetView = attachedRecyclerView != null
                ? attachedRecyclerView
                : anchorView;

        if (targetView == null) {
            return;
        }

        try {
            Snackbar snackbar = Snackbar.make(
                    targetView,
                    message,
                    Snackbar.LENGTH_SHORT
            );

            snackbar.setBackgroundTint(Color.rgb(25, 35, 40));
            snackbar.setTextColor(Color.WHITE);

            TextView snackText = snackbar.getView().findViewById(
                    com.google.android.material.R.id.snackbar_text
            );
            if (snackText != null) {
                Typeface bodyFont = ResourcesCompat.getFont(
                        targetView.getContext(),
                        R.font.font_body
                );
                if (bodyFont != null) {
                    snackText.setTypeface(bodyFont);
                }
            }

            snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE);
            snackbar.show();

        } catch (Exception e) {
            Toast.makeText(
                    targetView.getContext(),
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public int getItemCount() {
        return favorites.size();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    static class FavoriteViewHolder extends RecyclerView.ViewHolder {

        TextView textFavoriteType;
        TextView textFavoriteIlokano;
        TextView textFavoriteEnglish;
        TextView textFavoriteTagalog;
        ImageButton buttonSpeakFavorite;
        TextView buttonRemoveFavorite;

        FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);

            textFavoriteType = itemView.findViewById(
                    R.id.textFavoriteType
            );

            textFavoriteIlokano = itemView.findViewById(
                    R.id.textFavoriteIlokano
            );

            textFavoriteEnglish = itemView.findViewById(
                    R.id.textFavoriteEnglish
            );

            textFavoriteTagalog = itemView.findViewById(
                    R.id.textFavoriteTagalog
            );

            buttonSpeakFavorite = itemView.findViewById(
                    R.id.buttonSpeakFavorite
            );

            buttonRemoveFavorite = itemView.findViewById(
                    R.id.buttonRemoveFavorite
            );
        }
    }
}

package com.example.speaksmartbaguio;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.databinding.ItemDictionaryHeaderBinding;

public class DictionaryHeaderAdapter
        extends RecyclerView.Adapter<DictionaryHeaderAdapter.HeaderViewHolder> {

    public interface OnHeaderBoundListener {
        void onHeaderBound(ItemDictionaryHeaderBinding binding);
    }

    private final OnHeaderBoundListener listener;

    public DictionaryHeaderAdapter(
            OnHeaderBoundListener listener
    ) {
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return 1L;
    }

    @NonNull
    @Override
    public HeaderViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ItemDictionaryHeaderBinding binding =
                ItemDictionaryHeaderBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                );

        return new HeaderViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull HeaderViewHolder holder,
            int position
    ) {
        if (listener != null) {
            listener.onHeaderBound(holder.binding);
        }
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    static class HeaderViewHolder
            extends RecyclerView.ViewHolder {

        final ItemDictionaryHeaderBinding binding;

        HeaderViewHolder(
                @NonNull ItemDictionaryHeaderBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

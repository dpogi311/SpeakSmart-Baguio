
package com.example.speaksmartbaguio;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.databinding.ItemPhrasebookHeaderBinding;

public class PhrasebookHeaderAdapter
        extends RecyclerView.Adapter<PhrasebookHeaderAdapter.HeaderViewHolder> {

    public interface OnHeaderBoundListener {
        void onHeaderBound(ItemPhrasebookHeaderBinding binding);
    }

    private final OnHeaderBoundListener listener;

    public PhrasebookHeaderAdapter(OnHeaderBoundListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public HeaderViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ItemPhrasebookHeaderBinding binding =
                ItemPhrasebookHeaderBinding.inflate(
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

        final ItemPhrasebookHeaderBinding binding;

        HeaderViewHolder(ItemPhrasebookHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

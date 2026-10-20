package com.arxiv.client.ui.adapters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import com.arxiv.client.data.model.PaperItem;
import java.util.List;
import java.util.Objects;

/**
 * DiffUtil Callback to calculate granular list updates for PaperAdapter (60fps performance).
 * Eliminates notifyDataSetChanged() jank and ensures smooth scroll rendering on AVD.
 */
public class PaperDiffCallback extends DiffUtil.Callback {

    private final List<PaperItem> oldList;
    private final List<PaperItem> newList;

    public PaperDiffCallback(@NonNull List<PaperItem> oldList, @NonNull List<PaperItem> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        PaperItem oldItem = oldList.get(oldItemPosition);
        PaperItem newItem = newList.get(newItemPosition);
        if (oldItem == null || newItem == null) return false;
        return Objects.equals(oldItem.getId(), newItem.getId());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        PaperItem oldItem = oldList.get(oldItemPosition);
        PaperItem newItem = newList.get(newItemPosition);
        if (oldItem == null || newItem == null) return false;

        return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                && Objects.equals(oldItem.getAuthors(), newItem.getAuthors())
                && Objects.equals(oldItem.getSummary(), newItem.getSummary())
                && Objects.equals(oldItem.getPrimaryCategory(), newItem.getPrimaryCategory())
                && Objects.equals(oldItem.getPublished(), newItem.getPublished())
                && oldItem.isBookmarked() == newItem.isBookmarked()
                && Objects.equals(oldItem.getNote(), newItem.getNote());
    }
}

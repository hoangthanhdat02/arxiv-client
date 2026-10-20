package com.arxiv.client.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arxiv.client.R;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.ui.adapters.PaperAdapter;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class LibraryFragment extends Fragment {

    private RecyclerView recyclerLibrary;
    private LinearLayout layoutEmpty;
    private ChipGroup chipGroupCategory;
    private PaperAdapter paperAdapter;
    private ArxivDbHelper dbHelper;
    private String currentCategory = "ALL";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        recyclerLibrary = view.findViewById(R.id.recycler_library);
        layoutEmpty = view.findViewById(R.id.layout_empty_library);
        chipGroupCategory = view.findViewById(R.id.chip_group_category);

        dbHelper = ArxivDbHelper.getInstance(requireContext());
        paperAdapter = new PaperAdapter(requireContext());

        recyclerLibrary.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerLibrary.setAdapter(paperAdapter);

        setupPaperActions();
        setupCategoryFilter();
        setupSwipeToDelete();

        loadSavedBookmarks();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSavedBookmarks();
    }

    // MainActivity switches tabs with hide()/show(), which doesn't trigger onResume()
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && dbHelper != null) {
            loadSavedBookmarks();
        }
    }

    private void setupPaperActions() {
        paperAdapter.setOnPaperActionListener(new PaperAdapter.OnPaperActionListener() {
            @Override
            public void onBookmarkToggled(PaperItem paper, boolean isSaved, int position) {
                if (!isSaved) {
                    paperAdapter.removePaperAt(position);
                    updateEmptyState();
                }
            }

            @Override
            public void onNoteSaved(PaperItem paper, int position) {
                // Adapter already persisted the note and refreshed the card
            }
        });
    }

    private void setupCategoryFilter() {
        if (chipGroupCategory == null) return;

        chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_ai)) {
                currentCategory = "cs.AI";
            } else if (checkedIds.contains(R.id.chip_lg)) {
                currentCategory = "cs.LG";
            } else {
                currentCategory = "ALL";
            }
            loadSavedBookmarks();
        });
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                List<PaperItem> list = paperAdapter.getPapers();
                if (position >= 0 && position < list.size()) {
                    PaperItem removedPaper = list.get(position);

                    // Remove from SQLite & UI
                    dbHelper.removeBookmark(removedPaper.getId());
                    paperAdapter.removePaperAt(position);
                    updateEmptyState();

                    // Snackbar with UNDO action
                    Snackbar.make(recyclerLibrary, "Đã xóa khỏi Thư viện: " + removedPaper.getTitle(), Snackbar.LENGTH_LONG)
                            .setAction("HOÀN TÁC", v -> {
                                dbHelper.saveBookmark(removedPaper, removedPaper.getNote());
                                paperAdapter.insertPaperAt(position, removedPaper);
                                updateEmptyState();
                            })
                            .show();
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerLibrary);
    }

    private void loadSavedBookmarks() {
        if (!isAdded()) return;
        // Sync any downloaded PDF files on disk into database
        dbHelper.syncDownloadedFiles(requireContext());

        List<PaperItem> savedPapers = dbHelper.getBookmarksByCategory(currentCategory);
        paperAdapter.setPapers(savedPapers);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (paperAdapter.getItemCount() == 0) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerLibrary.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerLibrary.setVisibility(View.VISIBLE);
        }
    }
}

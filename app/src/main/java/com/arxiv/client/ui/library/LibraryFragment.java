package com.arxiv.client.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.arxiv.client.R;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.ui.adapters.PaperAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class LibraryFragment extends Fragment {

    private RecyclerView recyclerLibrary;
    private LinearLayout layoutEmpty;
    private TextView tvLibrarySubtitle;
    private SwipeRefreshLayout swipeRefreshLibrary;
    private MaterialButton btnBrowseExplore;
    private PaperAdapter paperAdapter;
    private ArxivDbHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        recyclerLibrary = view.findViewById(R.id.recycler_library);
        layoutEmpty = view.findViewById(R.id.layout_empty_library);
        tvLibrarySubtitle = view.findViewById(R.id.tv_library_subtitle);
        swipeRefreshLibrary = view.findViewById(R.id.swipe_refresh_library);
        btnBrowseExplore = view.findViewById(R.id.btn_browse_explore);

        dbHelper = ArxivDbHelper.getInstance(requireContext());
        paperAdapter = new PaperAdapter(requireContext());

        recyclerLibrary.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerLibrary.setAdapter(paperAdapter);

        // When a user un-bookmarks an item directly in the library, refresh the list immediately
        paperAdapter.setOnBookmarkChangeListener((paper, isBookmarked) -> loadSavedBookmarks());

        // Pull to refresh support
        swipeRefreshLibrary.setColorSchemeResources(R.color.primary);
        swipeRefreshLibrary.setOnRefreshListener(() -> {
            loadSavedBookmarks();
            swipeRefreshLibrary.setRefreshing(false);
        });

        // "Explore Papers" button navigates directly to Explore feed tab
        if (btnBrowseExplore != null) {
            btnBrowseExplore.setOnClickListener(v -> {
                if (getActivity() != null) {
                    BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
                    if (bottomNav != null) {
                        bottomNav.setSelectedItemId(R.id.nav_home);
                    }
                }
            });
        }

        loadSavedBookmarks();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSavedBookmarks();
    }

    /**
     * CRITICAL FIX: When switching between tabs using FragmentTransaction hide/show,
     * onResume is NOT invoked. onHiddenChanged is invoked instead.
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadSavedBookmarks();
        }
    }

    private void loadSavedBookmarks() {
        if (dbHelper == null) return;
        List<PaperItem> savedPapers = dbHelper.getAllBookmarks();
        if (savedPapers == null || savedPapers.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerLibrary.setVisibility(View.GONE);
            if (tvLibrarySubtitle != null) {
                tvLibrarySubtitle.setText("Offline SQLite storage - Read anytime in Airplane Mode");
            }
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerLibrary.setVisibility(View.VISIBLE);
            paperAdapter.setPapers(savedPapers);
            if (tvLibrarySubtitle != null) {
                tvLibrarySubtitle.setText(savedPapers.size() + (savedPapers.size() == 1 ? " paper saved for offline reading" : " papers saved for offline reading"));
            }
        }
    }
}

package com.arxiv.client.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.arxiv.client.R;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.ui.adapters.PaperAdapter;
import java.util.List;

public class LibraryFragment extends Fragment {

    private RecyclerView recyclerLibrary;
    private LinearLayout layoutEmpty;
    private PaperAdapter paperAdapter;
    private ArxivDbHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        recyclerLibrary = view.findViewById(R.id.recycler_library);
        layoutEmpty = view.findViewById(R.id.layout_empty_library);

        dbHelper = ArxivDbHelper.getInstance(requireContext());
        paperAdapter = new PaperAdapter(requireContext());

        recyclerLibrary.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerLibrary.setAdapter(paperAdapter);

        loadSavedBookmarks();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSavedBookmarks();
    }

    private void loadSavedBookmarks() {
        List<PaperItem> savedPapers = dbHelper.getAllBookmarks();
        if (savedPapers == null || savedPapers.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerLibrary.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerLibrary.setVisibility(View.VISIBLE);
            paperAdapter.setPapers(savedPapers);
        }
    }
}

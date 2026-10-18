package com.arxiv.client.ui.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.arxiv.client.R;
import com.arxiv.client.data.api.ApiClient;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.data.model.PaperResponse;
import com.arxiv.client.ui.adapters.PaperAdapter;
import com.arxiv.client.ui.base.DebounceTextWatcher;
import com.arxiv.client.ui.dialogs.FilterBottomSheetDialog;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchFragment extends Fragment implements FilterBottomSheetDialog.FilterListener {

    private EditText etSearch;
    private ImageButton btnFilter;
    private ProgressBar progressBar;
    private LinearLayout layoutEmpty;
    private RecyclerView recyclerSearchResults;
    private PaperAdapter paperAdapter;

    private String currentQuery = "";
    private String selectedCategory = "";
    private String selectedSort = "relevance";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        etSearch = view.findViewById(R.id.et_search);
        btnFilter = view.findViewById(R.id.btn_filter);
        progressBar = view.findViewById(R.id.progress_bar);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        recyclerSearchResults = view.findViewById(R.id.recycler_search_results);

        paperAdapter = new PaperAdapter(requireContext());
        recyclerSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerSearchResults.setAdapter(paperAdapter);

        // Debounce Text Watcher (500ms) to avoid server spam
        etSearch.addTextChangedListener(new DebounceTextWatcher(500) {
            @Override
            public void onDebouncedTextChange(String text) {
                currentQuery = text;
                performSearch();
            }
        });

        // Filter Dialog
        btnFilter.setOnClickListener(v -> {
            FilterBottomSheetDialog dialog = new FilterBottomSheetDialog(selectedCategory, selectedSort, this);
            dialog.show(getParentFragmentManager(), "FilterBottomSheetDialog");
        });

        // Default initial search
        currentQuery = "transformer";
        etSearch.setText(currentQuery);
        performSearch();

        return view;
    }

    private void performSearch() {
        if (currentQuery.isEmpty()) {
            paperAdapter.setPapers(null);
            layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        ApiClient.getApiService().searchPapers(currentQuery, selectedCategory, selectedSort, 0, 15).enqueue(new Callback<PaperResponse>() {
            @Override
            public void onResponse(Call<PaperResponse> call, Response<PaperResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<PaperItem> papers = response.body().getData();
                    paperAdapter.setPapers(papers);
                    if (papers == null || papers.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                    } else {
                        layoutEmpty.setVisibility(View.GONE);
                    }
                } else {
                    layoutEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<PaperResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onFilterApplied(String category, String sortBy) {
        this.selectedCategory = category;
        this.selectedSort = sortBy;
        performSearch();
    }
}

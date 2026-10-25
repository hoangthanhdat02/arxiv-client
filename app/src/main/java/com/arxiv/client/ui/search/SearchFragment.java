package com.arxiv.client.ui.search;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import com.google.android.material.chip.Chip;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Search & Filter module (Thành viên 4 - Đức).
 * Handles keyword search with 500ms debounce, category/sort filtering via
 * BottomSheet,
 * infinite scroll pagination, and network error recovery.
 */
public class SearchFragment extends Fragment implements FilterBottomSheetDialog.FilterListener {

    private static final int PAGE_SIZE = 15;

    // UI references
    private EditText etSearch;
    private ImageButton btnClear;
    private ImageButton btnFilter;
    private ProgressBar progressBar;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutError;
    private LinearLayout layoutActiveFilters;
    private TextView tvResultCount;
    private TextView tvErrorMessage;
    private Chip chipActiveCategory;
    private Chip chipActiveSort;
    private RecyclerView recyclerSearchResults;
    private PaperAdapter paperAdapter;
    private DebounceTextWatcher debounceTextWatcher;

    // Search state
    private String currentQuery = "";
    private String selectedCategory = "";
    private String selectedSort = "relevance";
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMorePages = true;

    // Track the in-flight Retrofit call so we can cancel superseded requests
    private Call<PaperResponse> currentCall;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        bindViews(view);
        setupRecyclerView();
        setupSearchInput();
        setupFilterButton();
        setupRetryButton(view);
        setupActiveFilterChips();

        return view;
    }

    // ──────────────────────────────────────────────
    // View binding & setup
    // ──────────────────────────────────────────────

    private void bindViews(View view) {
        etSearch = view.findViewById(R.id.et_search);
        btnClear = view.findViewById(R.id.btn_clear);
        btnFilter = view.findViewById(R.id.btn_filter);
        progressBar = view.findViewById(R.id.progress_bar);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        layoutError = view.findViewById(R.id.layout_error);
        layoutActiveFilters = view.findViewById(R.id.layout_active_filters);
        tvResultCount = view.findViewById(R.id.tv_result_count);
        tvErrorMessage = view.findViewById(R.id.tv_error_message);
        chipActiveCategory = view.findViewById(R.id.chip_active_category);
        chipActiveSort = view.findViewById(R.id.chip_active_sort);
        recyclerSearchResults = view.findViewById(R.id.recycler_search_results);
    }

    private void setupRecyclerView() {
        paperAdapter = new PaperAdapter(requireContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerSearchResults.setLayoutManager(layoutManager);
        recyclerSearchResults.setAdapter(paperAdapter);

        // Infinite scroll: load next page when near bottom
        recyclerSearchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0)
                    return; // only on scroll down
                int visibleCount = layoutManager.getChildCount();
                int totalCount = layoutManager.getItemCount();
                int firstVisible = layoutManager.findFirstVisibleItemPosition();
                if (!isLoading && hasMorePages && (visibleCount + firstVisible) >= totalCount - 3) {
                    loadMoreResults();
                }
            }
        });
    }

    private void setupSearchInput() {
        // Debounce: wait 500ms after user stops typing before sending request
        debounceTextWatcher = new DebounceTextWatcher(500) {
            @Override
            public void onDebouncedTextChange(String text) {
                currentQuery = text != null ? text.trim() : "";
                resetAndSearch();
            }
        };
        etSearch.addTextChangedListener(debounceTextWatcher);

        // Show/hide clear button as user types (non-debounced, instant feedback)
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        // Enter key on virtual keyboard triggers immediate search (cancels pending
        // debounce to avoid duplicate request)
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                if (debounceTextWatcher != null) {
                    debounceTextWatcher.cancel();
                }
                currentQuery = etSearch.getText().toString().trim();
                resetAndSearch();
                return true;
            }
            return false;
        });

        // Clear button: cancel pending debounce, wipe text, reset results
        btnClear.setOnClickListener(v -> {
            if (debounceTextWatcher != null) {
                debounceTextWatcher.cancel();
            }
            etSearch.setText("");
            currentQuery = "";
            resetAndSearch();
        });
    }

    private void setupFilterButton() {
        btnFilter.setOnClickListener(v -> {
            FilterBottomSheetDialog dialog = new FilterBottomSheetDialog(selectedCategory, selectedSort, this);
            dialog.show(getParentFragmentManager(), "FilterBottomSheetDialog");
        });
    }

    private void setupRetryButton(View view) {
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> resetAndSearch());
    }

    private void setupActiveFilterChips() {
        // Tapping X on active filter chip removes that filter and re-searches
        chipActiveCategory.setOnCloseIconClickListener(v -> {
            selectedCategory = "";
            updateActiveFilterBar();
            resetAndSearch();
        });
        chipActiveSort.setOnCloseIconClickListener(v -> {
            selectedSort = "relevance";
            updateActiveFilterBar();
            resetAndSearch();
        });
    }

    // ──────────────────────────────────────────────
    // Filter callback from BottomSheet
    // ──────────────────────────────────────────────

    @Override
    public void onFilterApplied(String category, String sortBy) {
        this.selectedCategory = category;
        this.selectedSort = sortBy;
        updateActiveFilterBar();
        resetAndSearch();
    }

    // ──────────────────────────────────────────────
    // Search execution
    // ──────────────────────────────────────────────

    private void resetAndSearch() {
        currentPage = 0;
        hasMorePages = true;
        performSearch(true);
    }

    private void loadMoreResults() {
        performSearch(false);
    }

    /**
     * @param isNewSearch true = replace existing results; false = append
     *                    (pagination)
     */
    private void performSearch(boolean isNewSearch) {
        boolean hasQuery = !currentQuery.trim().isEmpty();
        boolean hasFilter = (selectedCategory != null && !selectedCategory.isEmpty())
                || (!"relevance".equals(selectedSort));

        if (!hasQuery && !hasFilter) {
            showState(State.EMPTY);
            tvResultCount.setVisibility(View.GONE);
            if (isNewSearch)
                paperAdapter.setPapers(null);
            return;
        }

        // Cancel any in-flight request to avoid stale responses overwriting fresh ones
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }

        isLoading = true;
        if (isNewSearch) {
            showState(State.LOADING);
        }

        final int targetPage = isNewSearch ? 0 : currentPage + 1;

        currentCall = ApiClient.getApiService().searchPapers(
                currentQuery, selectedCategory, selectedSort, targetPage, PAGE_SIZE);

        currentCall.enqueue(new Callback<PaperResponse>() {
            @Override
            public void onResponse(Call<PaperResponse> call, Response<PaperResponse> response) {
                isLoading = false;
                if (call.isCanceled())
                    return;

                if (response.isSuccessful() && response.body() != null) {
                    currentPage = targetPage;
                    PaperResponse body = response.body();
                    List<PaperItem> papers = body.getData();

                    if (isNewSearch) {
                        paperAdapter.setPapers(papers);
                    } else {
                        paperAdapter.addPapers(papers);
                    }

                    // Detect end of results
                    if (papers == null || papers.size() < PAGE_SIZE) {
                        hasMorePages = false;
                    }

                    if (paperAdapter.getItemCount() == 0) {
                        showState(State.EMPTY);
                        tvResultCount.setVisibility(View.GONE);
                    } else {
                        showState(State.RESULTS);
                        updateResultCount(body.getTotalResults());
                    }
                } else {
                    if (isNewSearch)
                        showState(State.ERROR);
                }
            }

            @Override
            public void onFailure(Call<PaperResponse> call, Throwable t) {
                isLoading = false;
                if (call.isCanceled() || !isAdded())
                    return;
                if (isNewSearch) {
                    showState(State.ERROR);
                    if (tvErrorMessage != null) {
                        tvErrorMessage.setText(R.string.error_network_connection);
                    }
                }
            }
        });
    }

    // ──────────────────────────────────────────────
    // UI state management
    // ──────────────────────────────────────────────

    private enum State {
        LOADING, EMPTY, ERROR, RESULTS
    }

    private void showState(State state) {
        progressBar.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        layoutEmpty.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
        layoutError.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);
        recyclerSearchResults.setVisibility(state == State.RESULTS ? View.VISIBLE : View.GONE);
    }

    private void updateResultCount(int totalResults) {
        if (totalResults > 0) {
            tvResultCount.setVisibility(View.VISIBLE);
            tvResultCount.setText(String.format(java.util.Locale.US, "Found %d papers", totalResults));
        } else {
            tvResultCount.setVisibility(View.GONE);
        }
    }

    private void updateActiveFilterBar() {
        boolean hasCategory = selectedCategory != null && !selectedCategory.isEmpty();
        boolean hasNonDefaultSort = !"relevance".equals(selectedSort);

        if (!hasCategory && !hasNonDefaultSort) {
            layoutActiveFilters.setVisibility(View.GONE);
            return;
        }

        layoutActiveFilters.setVisibility(View.VISIBLE);

        if (hasCategory) {
            chipActiveCategory.setVisibility(View.VISIBLE);
            chipActiveCategory.setText(selectedCategory);
        } else {
            chipActiveCategory.setVisibility(View.GONE);
        }

        if (hasNonDefaultSort) {
            chipActiveSort.setVisibility(View.VISIBLE);
            chipActiveSort.setText("submittedDate".equals(selectedSort) ? "Latest" : "Relevance");
        } else {
            chipActiveSort.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (debounceTextWatcher != null) {
            debounceTextWatcher.cancel();
        }
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }
}

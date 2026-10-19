package com.arxiv.client.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.arxiv.client.R;
import com.arxiv.client.data.api.ApiClient;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.data.model.PaperResponse;
import com.arxiv.client.databinding.FragmentHomeBinding;
import com.arxiv.client.ui.adapters.PaperAdapter;
import com.google.android.material.tabs.TabLayout;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Home Feed & Discover Module for Latest arXiv Research Papers (Member 3 - Leduy36raumaphaduongtau).
 *
 * Core Features:
 * 1. Comprehensive ViewBinding architecture eliminating findViewById.
 * 2. Real-time category filtering (cs.AI vs cs.LG) with automatic request cancellation to prevent Race Conditions.
 * 3. Smooth Shimmer Skeleton loading on cold start.
 * 4. Pull-to-refresh (SwipeRefreshLayout) synchronized with the arXiv Express Gateway.
 * 5. High-performance infinite scroll pagination with prefetching.
 * 6. State Machine: Shimmer Loading -> Content List -> Error State with Retry Button.
 */
public class HomeFragment extends Fragment {

    private static final int PAGE_LIMIT = 10;

    private FragmentHomeBinding binding;
    private PaperAdapter paperAdapter;

    private String currentCategory = "cs.AI";
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMorePages = true;

    // Track active network request to safely cancel when switching categories
    private Call<PaperResponse> currentCall;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupTabs();
        setupSwipeRefresh();
        setupRetryButton();

        // Initial load with Shimmer skeleton
        loadPapers(true);
    }

    private void setupRecyclerView() {
        paperAdapter = new PaperAdapter(requireContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        binding.recyclerPapers.setLayoutManager(layoutManager);
        binding.recyclerPapers.setAdapter(paperAdapter);

        // Infinite scroll pagination listener (60fps optimized)
        binding.recyclerPapers.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isLoading && hasMorePages) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();

                    // Trigger next page when 2 items away from bottom
                    if ((visibleItemCount + firstVisiblePosition) >= totalItemCount - 2
                            && firstVisiblePosition >= 0) {
                        loadMorePapers();
                    }
                }
            }
        });
    }

    private void setupTabs() {
        binding.tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Cancel any pending network call to prevent race condition
                cancelCurrentCall();

                currentCategory = (tab.getPosition() == 0) ? "cs.AI" : "cs.LG";
                currentPage = 0;
                hasMorePages = true;

                loadPapers(true);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            cancelCurrentCall();
            currentPage = 0;
            hasMorePages = true;
            loadPapers(false);
        });
    }

    private void setupRetryButton() {
        binding.btnRetry.setOnClickListener(v -> {
            cancelCurrentCall();
            currentPage = 0;
            hasMorePages = true;
            loadPapers(true);
        });
    }

    private void cancelCurrentCall() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            currentCall = null;
        }
    }

    private void loadPapers(boolean showShimmer) {
        if (isLoading) return;
        isLoading = true;

        if (showShimmer) {
            binding.shimmerViewContainer.setVisibility(View.VISIBLE);
            binding.shimmerViewContainer.startShimmer();
            binding.recyclerPapers.setVisibility(View.GONE);
            binding.layoutError.setVisibility(View.GONE);
            binding.layoutEmpty.setVisibility(View.GONE);
        }

        currentCall = ApiClient.getApiService().getPapers(currentCategory, currentPage, PAGE_LIMIT);
        currentCall.enqueue(new Callback<PaperResponse>() {
            @Override
            public void onResponse(Call<PaperResponse> call, Response<PaperResponse> response) {
                if (!isAdded()) return;
                isLoading = false;
                binding.swipeRefresh.setRefreshing(false);
                binding.shimmerViewContainer.stopShimmer();
                binding.shimmerViewContainer.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    List<PaperItem> papers = response.body().getData();
                    if (papers == null || papers.isEmpty()) {
                        hasMorePages = false;
                        binding.recyclerPapers.setVisibility(View.GONE);
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                        binding.layoutError.setVisibility(View.GONE);
                    } else {
                        binding.recyclerPapers.setVisibility(View.VISIBLE);
                        binding.layoutEmpty.setVisibility(View.GONE);
                        binding.layoutError.setVisibility(View.GONE);

                        paperAdapter.setPapers(papers);

                        if (papers.size() < PAGE_LIMIT) {
                            hasMorePages = false;
                        }
                    }
                } else {
                    showErrorState();
                }
            }

            @Override
            public void onFailure(Call<PaperResponse> call, Throwable t) {
                if (call.isCanceled() || !isAdded()) return;
                isLoading = false;
                binding.swipeRefresh.setRefreshing(false);
                binding.shimmerViewContainer.stopShimmer();
                binding.shimmerViewContainer.setVisibility(View.GONE);

                showErrorState();
            }
        });
    }

    private void loadMorePapers() {
        if (isLoading || !hasMorePages) return;
        isLoading = true;
        int nextPage = currentPage + 1;

        currentCall = ApiClient.getApiService().getPapers(currentCategory, nextPage, PAGE_LIMIT);
        currentCall.enqueue(new Callback<PaperResponse>() {
            @Override
            public void onResponse(Call<PaperResponse> call, Response<PaperResponse> response) {
                if (!isAdded()) return;
                isLoading = false;

                if (response.isSuccessful() && response.body() != null) {
                    List<PaperItem> morePapers = response.body().getData();
                    if (morePapers != null && !morePapers.isEmpty()) {
                        currentPage = nextPage;
                        paperAdapter.addPapers(morePapers);

                        if (morePapers.size() < PAGE_LIMIT) {
                            hasMorePages = false;
                        }
                    } else {
                        hasMorePages = false;
                    }
                }
            }

            @Override
            public void onFailure(Call<PaperResponse> call, Throwable t) {
                if (call.isCanceled() || !isAdded()) return;
                isLoading = false;
            }
        });
    }

    private void showErrorState() {
        if (paperAdapter.getItemCount() == 0) {
            binding.recyclerPapers.setVisibility(View.GONE);
            binding.layoutError.setVisibility(View.VISIBLE);
            binding.layoutEmpty.setVisibility(View.GONE);
        } else {
            Toast.makeText(getContext(), R.string.error_network_title, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        cancelCurrentCall();
        super.onDestroyView();
        binding = null;
    }
}

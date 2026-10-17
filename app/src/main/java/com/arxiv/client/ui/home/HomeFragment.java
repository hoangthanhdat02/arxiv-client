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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.arxiv.client.R;
import com.arxiv.client.data.api.ApiClient;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.data.model.PaperResponse;
import com.arxiv.client.ui.adapters.PaperAdapter;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.tabs.TabLayout;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private TabLayout tabCategory;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerPapers;
    private ShimmerFrameLayout shimmerView;
    private PaperAdapter paperAdapter;

    private String currentCategory = "cs.AI";
    private int currentPage = 0;
    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tabCategory = view.findViewById(R.id.tab_category);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        recyclerPapers = view.findViewById(R.id.recycler_papers);
        shimmerView = view.findViewById(R.id.shimmer_view_container);

        setupRecyclerView();
        setupTabs();
        setupSwipeRefresh();

        loadPapers(true);

        return view;
    }

    private void setupRecyclerView() {
        paperAdapter = new PaperAdapter(requireContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerPapers.setLayoutManager(layoutManager);
        recyclerPapers.setAdapter(paperAdapter);

        // Infinite scroll pagination
        recyclerPapers.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) { // Scrolling down
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int pastVisibleItems = layoutManager.findFirstVisibleItemPosition();

                    if (!isLoading && (visibleItemCount + pastVisibleItems) >= totalItemCount - 2) {
                        loadMorePapers();
                    }
                }
            }
        });
    }

    private void setupTabs() {
        tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentCategory = (tab.getPosition() == 0) ? "cs.AI" : "cs.LG";
                currentPage = 0;
                loadPapers(true);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(() -> {
            currentPage = 0;
            loadPapers(false);
        });
    }

    private void loadPapers(boolean showShimmer) {
        if (isLoading) return;
        isLoading = true;

        if (showShimmer) {
            shimmerView.setVisibility(View.VISIBLE);
            shimmerView.startShimmer();
            recyclerPapers.setVisibility(View.GONE);
        }

        ApiClient.getApiService().getPapers(currentCategory, currentPage, 10).enqueue(new Callback<PaperResponse>() {
            @Override
            public void onResponse(Call<PaperResponse> call, Response<PaperResponse> response) {
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);
                recyclerPapers.setVisibility(View.VISIBLE);

                if (response.isSuccessful() && response.body() != null) {
                    List<PaperItem> papers = response.body().getData();
                    paperAdapter.setPapers(papers);
                } else {
                    Toast.makeText(getContext(), "Không thể kết nối máy chủ ExpressJS (10.0.2.2:3001)", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PaperResponse> call, Throwable t) {
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);
                recyclerPapers.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "Lỗi mạng: Đang chạy ở chế độ cục bộ hoặc máy chủ tắt", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMorePapers() {
        isLoading = true;
        currentPage++;

        ApiClient.getApiService().getPapers(currentCategory, currentPage, 10).enqueue(new Callback<PaperResponse>() {
            @Override
            public void onResponse(Call<PaperResponse> call, Response<PaperResponse> response) {
                isLoading = false;
                if (response.isSuccessful() && response.body() != null) {
                    List<PaperItem> morePapers = response.body().getData();
                    if (morePapers != null && !morePapers.isEmpty()) {
                        paperAdapter.addPapers(morePapers);
                    }
                }
            }

            @Override
            public void onFailure(Call<PaperResponse> call, Throwable t) {
                isLoading = false;
            }
        });
    }
}

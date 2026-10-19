package com.arxiv.client.ui.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.arxiv.client.R;
import com.arxiv.client.data.api.ApiClient;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.download.DownloadService;
import com.arxiv.client.data.model.BibtexResponse;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.databinding.ItemPaperCardBinding;
import com.arxiv.client.ui.home.PaperDetailBottomSheet;
import com.arxiv.client.ui.reader.PdfReaderActivity;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * High-performance RecyclerView Adapter for arXiv Paper cards (Member 3 - Leduy36raumaphaduongtau).
 * Built with ViewBinding, DiffUtil 60fps animations, Material Design 3, and ergonomic touch targets.
 */
public class PaperAdapter extends RecyclerView.Adapter<PaperAdapter.PaperViewHolder> {

    public interface OnPaperClickListener {
        void onPaperClick(PaperItem paper);
    }

    private final Context context;
    private final List<PaperItem> papers = new ArrayList<>();
    private final ArxivDbHelper dbHelper;
    private OnPaperClickListener onPaperClickListener;

    public PaperAdapter(@NonNull Context context) {
        this.context = context;
        this.dbHelper = ArxivDbHelper.getInstance(context);
    }

    public void setOnPaperClickListener(OnPaperClickListener listener) {
        this.onPaperClickListener = listener;
    }

    /**
     * Replaces paper list with granular DiffUtil updates to eliminate jank.
     */
    public void setPapers(List<PaperItem> newPapers) {
        List<PaperItem> oldList = new ArrayList<>(this.papers);
        List<PaperItem> targetList = (newPapers != null) ? new ArrayList<>(newPapers) : new ArrayList<>();

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PaperDiffCallback(oldList, targetList));
        this.papers.clear();
        this.papers.addAll(targetList);
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Appends additional pages smoothly for infinite scroll pagination.
     */
    public void addPapers(List<PaperItem> morePapers) {
        if (morePapers != null && !morePapers.isEmpty()) {
            int start = this.papers.size();
            this.papers.addAll(morePapers);
            notifyItemRangeInserted(start, morePapers.size());
        }
    }

    @NonNull
    @Override
    public PaperViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPaperCardBinding binding = ItemPaperCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PaperViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PaperViewHolder holder, int position) {
        PaperItem paper = papers.get(position);
        holder.bind(paper);
    }

    @Override
    public int getItemCount() {
        return papers.size();
    }

    public class PaperViewHolder extends RecyclerView.ViewHolder {
        private final ItemPaperCardBinding binding;

        public PaperViewHolder(@NonNull ItemPaperCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(PaperItem paper) {
            // Category badge & published date
            binding.tvCategory.setText(paper.getPrimaryCategory());
            binding.tvDate.setText(paper.getFormattedDate());

            // Core academic info
            binding.tvTitle.setText(paper.getTitle());
            binding.tvAuthors.setText(paper.getAuthorsString());
            binding.tvSummary.setText(paper.getSummary());

            // Synchronize SQLite bookmark state
            boolean isBookmarked = dbHelper.isBookmarked(paper.getId());
            binding.btnBookmark.setImageResource(isBookmarked
                    ? R.drawable.ic_bookmark_filled
                    : R.drawable.ic_bookmark_border);

            // Synchronize offline PDF download state
            boolean isDownloaded = DownloadService.isPdfDownloaded(context, paper.getId());
            binding.btnReadPdf.setText(isDownloaded ? R.string.btn_open_offline : R.string.btn_read_pdf);

            // Card click: Open PaperDetailBottomSheet (or notify listener)
            binding.getRoot().setOnClickListener(v -> {
                if (onPaperClickListener != null) {
                    onPaperClickListener.onPaperClick(paper);
                } else if (context instanceof FragmentActivity) {
                    PaperDetailBottomSheet bottomSheet = PaperDetailBottomSheet.newInstance(paper);
                    bottomSheet.show(((FragmentActivity) context).getSupportFragmentManager(), "PaperDetailBottomSheet");
                }
            });

            // Action: Direct Read PDF in Native Reader (TV5 module)
            binding.btnReadPdf.setOnClickListener(v -> {
                Intent intent = new Intent(context, PdfReaderActivity.class);
                intent.putExtra("paper", paper);
                context.startActivity(intent);
            });

            // Action: Copy BibTeX citation
            binding.btnBibtex.setOnClickListener(v -> {
                Toast.makeText(context, R.string.fetching_bibtex, Toast.LENGTH_SHORT).show();
                ApiClient.getApiService().getBibtex(paper.getId()).enqueue(new Callback<BibtexResponse>() {
                    @Override
                    public void onResponse(Call<BibtexResponse> call, Response<BibtexResponse> response) {
                        String bibtexText;
                        if (response.isSuccessful() && response.body() != null && response.body().getBibtex() != null) {
                            bibtexText = response.body().getBibtex();
                        } else {
                            bibtexText = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "},\n  url={" + paper.getArxivUrl() + "}\n}";
                        }
                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            ClipData clip = ClipData.newPlainText("BibTeX Citation", bibtexText);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(context, R.string.copied_bibtex, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BibtexResponse> call, Throwable t) {
                        String bibtexText = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "}\n}";
                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            ClipData clip = ClipData.newPlainText("BibTeX Citation", bibtexText);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(context, R.string.copied_bibtex, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            });

            // Action: Toggle Bookmark in SQLite
            binding.btnBookmark.setOnClickListener(v -> {
                boolean currentlySaved = dbHelper.isBookmarked(paper.getId());
                if (currentlySaved) {
                    dbHelper.removeBookmark(paper.getId());
                    binding.btnBookmark.setImageResource(R.drawable.ic_bookmark_border);
                    Toast.makeText(context, R.string.bookmark_removed, Toast.LENGTH_SHORT).show();
                } else {
                    dbHelper.saveBookmark(paper, "");
                    binding.btnBookmark.setImageResource(R.drawable.ic_bookmark_filled);
                    Toast.makeText(context, R.string.bookmark_saved, Toast.LENGTH_SHORT).show();
                }
            });

            // Action: Share paper link via Android Native Share Sheet
            binding.btnShare.setOnClickListener(v -> {
                String shareText = paper.getTitle() + "\n" + paper.getArxivUrl();
                new ShareCompat.IntentBuilder(context)
                        .setType("text/plain")
                        .setChooserTitle(context.getString(R.string.share_title))
                        .setText(shareText)
                        .startChooser();
            });
        }
    }
}

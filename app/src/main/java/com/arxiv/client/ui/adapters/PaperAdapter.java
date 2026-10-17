package com.arxiv.client.ui.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ShareCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.arxiv.client.R;
import com.arxiv.client.data.api.ApiClient;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.download.DownloadService;
import com.arxiv.client.data.model.BibtexResponse;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.ui.reader.PdfReaderActivity;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaperAdapter extends RecyclerView.Adapter<PaperAdapter.PaperViewHolder> {

    public interface OnBookmarkChangeListener {
        void onBookmarkChanged(PaperItem paper, boolean isBookmarked);
    }

    private final Context context;
    private final List<PaperItem> papers = new ArrayList<>();
    private final ArxivDbHelper dbHelper;
    private OnBookmarkChangeListener bookmarkChangeListener;

    public void setOnBookmarkChangeListener(OnBookmarkChangeListener listener) {
        this.bookmarkChangeListener = listener;
    }

    public PaperAdapter(Context context) {
        this.context = context;
        this.dbHelper = ArxivDbHelper.getInstance(context);
    }

    public void setPapers(List<PaperItem> newPapers) {
        this.papers.clear();
        if (newPapers != null) {
            this.papers.addAll(newPapers);
        }
        notifyDataSetChanged();
    }

    public void addPapers(List<PaperItem> morePapers) {
        if (morePapers != null && !morePapers.isEmpty()) {
            int start = this.papers.size();
            this.papers.addAll(morePapers);
            notifyItemRangeInserted(start, morePapers.size());
        }
    }

    private boolean heroEnabled = true;

    public void setHeroEnabled(boolean heroEnabled) {
        this.heroEnabled = heroEnabled;
    }

    private static final int VIEW_TYPE_HERO = 0;
    private static final int VIEW_TYPE_STANDARD = 1;

    @Override
    public int getItemViewType(int position) {
        return (heroEnabled && position == 0) ? VIEW_TYPE_HERO : VIEW_TYPE_STANDARD;
    }

    @NonNull
    @Override
    public PaperViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == VIEW_TYPE_HERO) ? R.layout.item_paper_hero : R.layout.item_paper_card;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new PaperViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaperViewHolder holder, int position) {
        PaperItem paper = papers.get(position);

        String category = paper.getPrimaryCategory();
        if (!"cs.LG".equalsIgnoreCase(category)) {
            category = "cs.AI";
        }
        holder.tvCategory.setText(category);
        if ("cs.LG".equalsIgnoreCase(category)) {
            holder.tvCategory.setTextColor(context.getColor(R.color.badge_lg));
            holder.tvCategory.setBackgroundResource(R.drawable.badge_offline_bg);
        } else {
            holder.tvCategory.setTextColor(context.getColor(R.color.badge_ai));
            holder.tvCategory.setBackgroundResource(R.drawable.badge_bg);
        }
        holder.tvDate.setText(paper.getFormattedDate());
        holder.tvTitle.setText(paper.getTitle());
        holder.tvAuthors.setText(paper.getAuthorsString());
        holder.tvSummary.setText(paper.getSummary());

        // BBC Section Divider for the first item after the Hero card
        if (holder.layoutSectionDivider != null) {
            holder.layoutSectionDivider.setVisibility((heroEnabled && position == 1) ? View.VISIBLE : View.GONE);
        }

        // Offline Ready badge indicator
        boolean isDownloaded = DownloadService.isPdfDownloaded(context, paper.getId());
        holder.tvCardOfflineBadge.setVisibility(isDownloaded ? View.VISIBLE : View.GONE);

        // Check SQLite bookmark status
        boolean isBookmarked = dbHelper.isBookmarked(paper.getId());
        holder.btnBookmark.setImageResource(isBookmarked
                ? R.drawable.ic_bookmark_filled
                : R.drawable.ic_bookmark_border);

        // Action: Read PDF in-app
        View.OnClickListener openPdfListener = v -> {
            Intent intent = new Intent(context, PdfReaderActivity.class);
            intent.putExtra("paper", paper);
            context.startActivity(intent);
        };
        holder.btnReadPdf.setOnClickListener(openPdfListener);
        holder.itemView.setOnClickListener(openPdfListener);

        // Action: Copy BibTeX
        holder.btnBibtex.setOnClickListener(v -> {
            Toast.makeText(context, "Fetching BibTeX citation...", Toast.LENGTH_SHORT).show();
            ApiClient.getApiService().getBibtex(paper.getId()).enqueue(new Callback<BibtexResponse>() {
                @Override
                public void onResponse(Call<BibtexResponse> call, Response<BibtexResponse> response) {
                    String bibtexText = "";
                    if (response.isSuccessful() && response.body() != null) {
                        bibtexText = response.body().getBibtex();
                    } else {
                        // Fallback format
                        bibtexText = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "},\n  url={" + paper.getArxivUrl() + "}\n}";
                    }
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("BibTeX Citation", bibtexText);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "BibTeX copied to Clipboard!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(Call<BibtexResponse> call, Throwable t) {
                    String bibtexText = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "}\n}";
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("BibTeX Citation", bibtexText);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "BibTeX copied to Clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Action: Bookmark toggle
        holder.btnBookmark.setOnClickListener(v -> {
            boolean currentlySaved = dbHelper.isBookmarked(paper.getId());
            if (currentlySaved) {
                dbHelper.removeBookmark(paper.getId());
                holder.btnBookmark.setImageResource(R.drawable.ic_bookmark_border);
                Toast.makeText(context, "Removed from Saved Library", Toast.LENGTH_SHORT).show();
                if (bookmarkChangeListener != null) {
                    bookmarkChangeListener.onBookmarkChanged(paper, false);
                }
            } else {
                dbHelper.saveBookmark(paper, "");
                holder.btnBookmark.setImageResource(R.drawable.ic_bookmark_filled);
                Toast.makeText(context, "Saved to Offline Library!", Toast.LENGTH_SHORT).show();
                if (bookmarkChangeListener != null) {
                    bookmarkChangeListener.onBookmarkChanged(paper, true);
                }
            }
        });

        // Action: Share
        holder.btnShare.setOnClickListener(v -> {
            String shareText = paper.getTitle() + "\n" + paper.getArxivUrl();
            new ShareCompat.IntentBuilder(context)
                    .setType("text/plain")
                    .setChooserTitle("Share arXiv paper")
                    .setText(shareText)
                    .startChooser();
        });
    }

    @Override
    public int getItemCount() {
        return papers.size();
    }

    public static class PaperViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvCardOfflineBadge, tvDate, tvTitle, tvAuthors, tvSummary;
        MaterialButton btnReadPdf, btnBibtex;
        ImageButton btnBookmark, btnShare;
        View layoutSectionDivider;

        public PaperViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutSectionDivider = itemView.findViewById(R.id.layout_section_divider);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvCardOfflineBadge = itemView.findViewById(R.id.tv_card_offline_badge);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvAuthors = itemView.findViewById(R.id.tv_authors);
            tvSummary = itemView.findViewById(R.id.tv_summary);
            btnReadPdf = itemView.findViewById(R.id.btn_read_pdf);
            btnBibtex = itemView.findViewById(R.id.btn_bibtex);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
            btnShare = itemView.findViewById(R.id.btn_share);
        }
    }
}

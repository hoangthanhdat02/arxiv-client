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

    private final Context context;
    private final List<PaperItem> papers = new ArrayList<>();
    private final ArxivDbHelper dbHelper;

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

    @NonNull
    @Override
    public PaperViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_paper_card, parent, false);
        return new PaperViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaperViewHolder holder, int position) {
        PaperItem paper = papers.get(position);

        holder.tvCategory.setText(paper.getPrimaryCategory());
        holder.tvDate.setText(paper.getFormattedDate());
        holder.tvTitle.setText(paper.getTitle());
        holder.tvAuthors.setText(paper.getAuthorsString());
        holder.tvSummary.setText(paper.getSummary());

        // Check SQLite bookmark status
        boolean isBookmarked = dbHelper.isBookmarked(paper.getId());
        holder.btnBookmark.setImageResource(isBookmarked
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);

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
            Toast.makeText(context, "Đang lấy trích dẫn BibTeX...", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(context, "Đã sao chép BibTeX vào Clipboard máy ảo!", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFailure(Call<BibtexResponse> call, Throwable t) {
                    String bibtexText = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "}\n}";
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("BibTeX Citation", bibtexText);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Đã sao chép BibTeX vào Clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Action: Bookmark toggle
        holder.btnBookmark.setOnClickListener(v -> {
            boolean currentlySaved = dbHelper.isBookmarked(paper.getId());
            if (currentlySaved) {
                dbHelper.removeBookmark(paper.getId());
                holder.btnBookmark.setImageResource(android.R.drawable.btn_star_big_off);
                Toast.makeText(context, "Đã xóa khỏi Thư viện", Toast.LENGTH_SHORT).show();
            } else {
                dbHelper.saveBookmark(paper, "");
                holder.btnBookmark.setImageResource(android.R.drawable.btn_star_big_on);
                Toast.makeText(context, "Đã lưu vào Thư viện ngoại tuyến!", Toast.LENGTH_SHORT).show();
            }
        });

        // Action: Share
        holder.btnShare.setOnClickListener(v -> {
            String shareText = paper.getTitle() + "\n" + paper.getArxivUrl();
            new ShareCompat.IntentBuilder(context)
                    .setType("text/plain")
                    .setChooserTitle("Chia sẻ bài báo arXiv")
                    .setText(shareText)
                    .startChooser();
        });
    }

    @Override
    public int getItemCount() {
        return papers.size();
    }

    public static class PaperViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvDate, tvTitle, tvAuthors, tvSummary;
        MaterialButton btnReadPdf, btnBibtex;
        ImageButton btnBookmark, btnShare;

        public PaperViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_category);
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

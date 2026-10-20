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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaperAdapter extends RecyclerView.Adapter<PaperAdapter.PaperViewHolder> {

    public interface OnPaperActionListener {
        void onBookmarkToggled(PaperItem paper, boolean isSaved, int position);
        void onNoteClicked(PaperItem paper, int position);
    }

    private final Context context;
    private final List<PaperItem> papers = new ArrayList<>();
    private final ArxivDbHelper dbHelper;
    private OnPaperActionListener actionListener;

    public PaperAdapter(Context context) {
        this.context = context;
        this.dbHelper = ArxivDbHelper.getInstance(context);
    }

    public void setOnPaperActionListener(OnPaperActionListener listener) {
        this.actionListener = listener;
    }

    public List<PaperItem> getPapers() {
        return papers;
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

    public void removePaperAt(int position) {
        if (position >= 0 && position < papers.size()) {
            papers.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void insertPaperAt(int position, PaperItem paper) {
        if (paper != null && position >= 0 && position <= papers.size()) {
            papers.add(position, paper);
            notifyItemInserted(position);
        }
    }

    public void updatePaperAt(int position, PaperItem paper) {
        if (paper != null && position >= 0 && position < papers.size()) {
            papers.set(position, paper);
            notifyItemChanged(position);
        }
    }

    public void showEditNoteDialog(PaperItem paper, int position) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_note, null);
        TextView tvPaperName = dialogView.findViewById(R.id.tv_dialog_paper_name);
        TextInputEditText etNote = dialogView.findViewById(R.id.et_paper_note);

        tvPaperName.setText(paper.getTitle());
        String currentNote = (paper.getNote() != null && !paper.getNote().isEmpty())
                ? paper.getNote()
                : dbHelper.getNote(paper.getId());
        etNote.setText(currentNote);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setPositiveButton("Lưu ghi chú", (dialog, which) -> {
                    String updatedNote = etNote.getText() != null ? etNote.getText().toString().trim() : "";
                    paper.setNote(updatedNote);
                    if (dbHelper.isBookmarked(paper.getId())) {
                        dbHelper.updateNote(paper.getId(), updatedNote);
                    } else {
                        // Tự động lưu vào SQLite bookmark để ghi chú không bị mất
                        paper.setBookmarked(true);
                        dbHelper.saveBookmark(paper, updatedNote);
                    }
                    notifyItemChanged(position);
                    Toast.makeText(context, "Đã lưu ghi chú nghiên cứu!", Toast.LENGTH_SHORT).show();
                    if (actionListener != null) {
                        actionListener.onNoteClicked(paper, position);
                    }
                })
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss());

        if (currentNote != null && !currentNote.trim().isEmpty()) {
            builder.setNeutralButton("Xóa ghi chú", (dialog, which) -> {
                paper.setNote("");
                dbHelper.updateNote(paper.getId(), "");
                notifyItemChanged(position);
                Toast.makeText(context, "Đã xóa ghi chú", Toast.LENGTH_SHORT).show();
                if (actionListener != null) {
                    actionListener.onNoteClicked(paper, position);
                }
            });
        }

        builder.show();
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

        // Kiểm tra xem bài báo đã có ghi chú lưu trong SQLite chưa
        if (paper.getNote() == null || paper.getNote().isEmpty()) {
            String savedNote = dbHelper.getNote(paper.getId());
            if (savedNote != null && !savedNote.isEmpty()) {
                paper.setNote(savedNote);
            }
        }

        // Hiển thị ghi chú cá nhân
        if (paper.getNote() != null && !paper.getNote().trim().isEmpty()) {
            holder.layoutNoteContainer.setVisibility(View.VISIBLE);
            holder.tvPaperNote.setText(paper.getNote());
        } else {
            holder.layoutNoteContainer.setVisibility(View.GONE);
        }

        // Action: Bấm nút Ghi chú (Hoạt động trên MỌI màn hình: Home, Search, Library)
        View.OnClickListener noteClickListener = v -> {
            showEditNoteDialog(paper, holder.getAdapterPosition());
        };
        if (holder.btnNote != null) {
            holder.btnNote.setOnClickListener(noteClickListener);
        }
        holder.layoutNoteContainer.setOnClickListener(noteClickListener);

        // Check SQLite bookmark status
        boolean isBookmarked = dbHelper.isBookmarked(paper.getId());
        paper.setBookmarked(isBookmarked);
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

        // Action: Copy BibTeX (Offline-First Priority for Airplane Mode)
        holder.btnBibtex.setOnClickListener(v -> {
            // Check SQLite first for instant offline copy
            String localBibtex = paper.getBibtex();
            if (localBibtex == null || localBibtex.trim().isEmpty()) {
                localBibtex = dbHelper.getBibtex(paper.getId());
            }

            if (localBibtex != null && !localBibtex.trim().isEmpty()) {
                copyToClipboard("BibTeX Citation", localBibtex);
                Toast.makeText(context, "Đã sao chép BibTeX vào Clipboard máy ảo!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Fallback: If not cached in SQLite yet, request API with local fallback
            Toast.makeText(context, "Đang lấy trích dẫn BibTeX...", Toast.LENGTH_SHORT).show();
            ApiClient.getApiService().getBibtex(paper.getId()).enqueue(new Callback<BibtexResponse>() {
                @Override
                public void onResponse(Call<BibtexResponse> call, Response<BibtexResponse> response) {
                    if (response.isSuccessful()) {
                        applyServerCitation(paper, response.body());
                    }
                    String bibtexText = paper.generateDefaultBibtex();

                    copyToClipboard("BibTeX Citation", bibtexText);
                    Toast.makeText(context, "Đã sao chép BibTeX vào Clipboard máy ảo!", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFailure(Call<BibtexResponse> call, Throwable t) {
                    String bibtexText = paper.generateDefaultBibtex();
                    copyToClipboard("BibTeX Citation", bibtexText);
                    Toast.makeText(context, "Đã sao chép BibTeX vào Clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Action: Bookmark toggle
        holder.btnBookmark.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            boolean currentlySaved = dbHelper.isBookmarked(paper.getId());
            if (currentlySaved) {
                dbHelper.removeBookmark(paper.getId());
                paper.setBookmarked(false);
                holder.btnBookmark.setImageResource(android.R.drawable.btn_star_big_off);
                Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show();
                if (actionListener != null) {
                    actionListener.onBookmarkToggled(paper, false, currentPos);
                }
            } else {
                dbHelper.saveBookmark(paper, paper.getNote());
                paper.setBookmarked(true);
                syncServerCitation(paper);
                holder.btnBookmark.setImageResource(android.R.drawable.btn_star_big_on);
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show();
                if (actionListener != null) {
                    actionListener.onBookmarkToggled(paper, true, currentPos);
                }
            }
        });

        // Action: Share
        holder.btnShare.setOnClickListener(v -> {
            String shareText = paper.getTitle() + "\n"
                    + paper.getAuthorsString() + "\n\n"
                    + "arXiv: " + paper.getArxivUrl() + "\n"
                    + "PDF: " + paper.getPdfUrl();
            new ShareCompat.IntentBuilder(context)
                    .setType("text/plain")
                    .setChooserTitle("Chia sẻ bài báo arXiv")
                    .setSubject(paper.getTitle())
                    .setText(shareText)
                    .startChooser();
        });
    }

    // Silently replace the locally generated citation of a freshly saved bookmark with the server one,
    // so the next copy in Airplane Mode returns the server-quality BibTeX (with DOI)
    private void syncServerCitation(PaperItem paper) {
        ApiClient.getApiService().getBibtex(paper.getId()).enqueue(new Callback<BibtexResponse>() {
            @Override
            public void onResponse(Call<BibtexResponse> call, Response<BibtexResponse> response) {
                if (response.isSuccessful()) {
                    applyServerCitation(paper, response.body());
                }
            }

            @Override
            public void onFailure(Call<BibtexResponse> call, Throwable t) {
                // Offline: keep the locally generated citation
            }
        });
    }

    private void applyServerCitation(PaperItem paper, BibtexResponse body) {
        if (body == null || body.getBibtex() == null || body.getBibtex().isEmpty()) return;
        paper.setBibtex(body.getBibtex());
        paper.setApa(body.getApa());
        if (dbHelper.isBookmarked(paper.getId())) {
            dbHelper.updateCitation(paper.getId(), body.getBibtex(), body.getApa());
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
        }
    }

    @Override
    public int getItemCount() {
        return papers.size();
    }

    public static class PaperViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvDate, tvTitle, tvAuthors, tvSummary, tvPaperNote;
        View layoutNoteContainer;
        MaterialButton btnReadPdf, btnBibtex;
        ImageButton btnBookmark, btnShare, btnNote;

        public PaperViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvAuthors = itemView.findViewById(R.id.tv_authors);
            tvSummary = itemView.findViewById(R.id.tv_summary);
            tvPaperNote = itemView.findViewById(R.id.tv_paper_note);
            layoutNoteContainer = itemView.findViewById(R.id.layout_note_container);

            btnReadPdf = itemView.findViewById(R.id.btn_read_pdf);
            btnBibtex = itemView.findViewById(R.id.btn_bibtex);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnNote = itemView.findViewById(R.id.btn_note);
        }
    }
}

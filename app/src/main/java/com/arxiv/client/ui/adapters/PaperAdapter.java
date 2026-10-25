package com.arxiv.client.ui.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * High-performance RecyclerView Adapter for arXiv Paper cards (Member 3 - Leduy36raumaphaduongtau).
 * Built with ViewBinding, DiffUtil 60fps animations, Material Design 3, and ergonomic touch targets.
 * Offline library actions (notes, cached BibTeX, swipe support) by Member 7.
 */
public class PaperAdapter extends RecyclerView.Adapter<PaperAdapter.PaperViewHolder> {

    public interface OnPaperClickListener {
        void onPaperClick(PaperItem paper);
    }

    public interface OnPaperActionListener {
        void onBookmarkToggled(PaperItem paper, boolean isSaved, int position);
        void onNoteSaved(PaperItem paper, int position);
    }

    private final Context context;
    private final List<PaperItem> papers = new ArrayList<>();
    private final ArxivDbHelper dbHelper;
    private OnPaperClickListener onPaperClickListener;
    private OnPaperActionListener actionListener;

    public PaperAdapter(@NonNull Context context) {
        this.context = context;
        this.dbHelper = ArxivDbHelper.getInstance(context);
    }

    public void setOnPaperClickListener(OnPaperClickListener listener) {
        this.onPaperClickListener = listener;
    }

    public void setOnPaperActionListener(OnPaperActionListener listener) {
        this.actionListener = listener;
    }

    public List<PaperItem> getPapers() {
        return papers;
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

    public void showEditNoteDialog(PaperItem paper, int position) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_note, null);
        TextView tvPaperName = dialogView.findViewById(R.id.tv_dialog_paper_name);
        TextInputEditText etNote = dialogView.findViewById(R.id.et_paper_note);

        tvPaperName.setText(paper.getTitle());
        String currentNote = !paper.getNote().isEmpty() ? paper.getNote() : dbHelper.getNote(paper.getId());
        etNote.setText(currentNote);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setPositiveButton("Save Note", (dialog, which) -> {
                    String updatedNote = etNote.getText() != null ? etNote.getText().toString().trim() : "";
                    paper.setNote(updatedNote);
                    if (dbHelper.isBookmarked(paper.getId())) {
                        dbHelper.updateNote(paper.getId(), updatedNote);
                    } else {
                        // Auto-bookmark so the note isn't lost
                        paper.setBookmarked(true);
                        dbHelper.saveBookmark(paper, updatedNote);
                    }
                    notifyItemChanged(position);
                    Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show();
                    if (actionListener != null) {
                        actionListener.onNoteSaved(paper, position);
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        if (!currentNote.trim().isEmpty()) {
            builder.setNeutralButton("Delete Note", (dialog, which) -> {
                paper.setNote("");
                dbHelper.updateNote(paper.getId(), "");
                notifyItemChanged(position);
                if (actionListener != null) {
                    actionListener.onNoteSaved(paper, position);
                }
            });
        }

        builder.show();
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
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(context, R.string.copied_bibtex, Toast.LENGTH_SHORT).show();
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
            paper.setBookmarked(isBookmarked);
            binding.btnBookmark.setImageResource(isBookmarked
                    ? R.drawable.ic_bookmark_filled
                    : R.drawable.ic_bookmark_border);

            // Personal note stored in SQLite
            if (paper.getNote().isEmpty() && isBookmarked) {
                paper.setNote(dbHelper.getNote(paper.getId()));
            }
            if (!paper.getNote().trim().isEmpty()) {
                binding.layoutNoteContainer.setVisibility(View.VISIBLE);
                binding.tvPaperNote.setText(paper.getNote());
            } else {
                binding.layoutNoteContainer.setVisibility(View.GONE);
            }
            View.OnClickListener noteClickListener = v -> showEditNoteDialog(paper, getBindingAdapterPosition());
            binding.btnNote.setOnClickListener(noteClickListener);
            binding.layoutNoteContainer.setOnClickListener(noteClickListener);

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

            // Action: Copy BibTeX citation (SQLite first so it works in Airplane Mode)
            binding.btnBibtex.setOnClickListener(v -> {
                String localBibtex = !paper.getBibtex().isEmpty() ? paper.getBibtex() : dbHelper.getBibtex(paper.getId());
                if (!localBibtex.trim().isEmpty()) {
                    copyToClipboard("BibTeX Citation", localBibtex);
                    return;
                }

                Toast.makeText(context, R.string.fetching_bibtex, Toast.LENGTH_SHORT).show();
                ApiClient.getApiService().getBibtex(paper.getId()).enqueue(new Callback<BibtexResponse>() {
                    @Override
                    public void onResponse(Call<BibtexResponse> call, Response<BibtexResponse> response) {
                        if (response.isSuccessful()) {
                            applyServerCitation(paper, response.body());
                        }
                        copyToClipboard("BibTeX Citation", paper.generateDefaultBibtex());
                    }

                    @Override
                    public void onFailure(Call<BibtexResponse> call, Throwable t) {
                        copyToClipboard("BibTeX Citation", paper.generateDefaultBibtex());
                    }
                });
            });

            // Action: Toggle Bookmark in SQLite
            binding.btnBookmark.setOnClickListener(v -> {
                int currentPos = getBindingAdapterPosition();
                boolean currentlySaved = dbHelper.isBookmarked(paper.getId());
                if (currentlySaved) {
                    dbHelper.removeBookmark(paper.getId());
                    paper.setBookmarked(false);
                    binding.btnBookmark.setImageResource(R.drawable.ic_bookmark_border);
                    Toast.makeText(context, R.string.bookmark_removed, Toast.LENGTH_SHORT).show();
                } else {
                    dbHelper.saveBookmark(paper, paper.getNote());
                    paper.setBookmarked(true);
                    syncServerCitation(paper);
                    binding.btnBookmark.setImageResource(R.drawable.ic_bookmark_filled);
                    Toast.makeText(context, R.string.bookmark_saved, Toast.LENGTH_SHORT).show();
                }
                if (actionListener != null) {
                    actionListener.onBookmarkToggled(paper, !currentlySaved, currentPos);
                }
            });

            // Action: Share paper link via Android Native Share Sheet
            binding.btnShare.setOnClickListener(v -> {
                String shareText = paper.getTitle() + "\n"
                        + paper.getAuthorsString() + "\n\n"
                        + "arXiv: " + paper.getArxivUrl() + "\n"
                        + "PDF: " + paper.getPdfUrl();
                new ShareCompat.IntentBuilder(context)
                        .setType("text/plain")
                        .setChooserTitle(context.getString(R.string.share_title))
                        .setSubject(paper.getTitle())
                        .setText(shareText)
                        .startChooser();
            });
        }
    }
}

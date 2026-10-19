package com.arxiv.client.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ShareCompat;
import com.arxiv.client.R;
import com.arxiv.client.data.api.ApiClient;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.download.DownloadService;
import com.arxiv.client.data.model.BibtexResponse;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.databinding.DialogPaperDetailBinding;
import com.arxiv.client.ui.reader.PdfReaderActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Modal BottomSheet displaying full academic paper abstract, metadata, and quick actions.
 * Implements Mockup 3 ("Paper Details") from the course specification.
 */
public class PaperDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PAPER = "arg_paper";

    private DialogPaperDetailBinding binding;
    private PaperItem paper;
    private ArxivDbHelper dbHelper;

    public static PaperDetailBottomSheet newInstance(PaperItem paper) {
        PaperDetailBottomSheet fragment = new PaperDetailBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PAPER, paper);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogPaperDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            paper = (PaperItem) getArguments().getSerializable(ARG_PAPER);
        }

        if (paper == null) {
            dismiss();
            return;
        }

        dbHelper = ArxivDbHelper.getInstance(requireContext());
        bindData();
        setupActions();
    }

    private void bindData() {
        binding.tvDetailCategory.setText(paper.getPrimaryCategory());
        binding.tvDetailDate.setText(paper.getFormattedDate());
        binding.tvDetailTitle.setText(paper.getTitle());
        binding.tvDetailAuthors.setText(paper.getAuthorsString());
        binding.tvDetailAbstract.setText(paper.getSummary());

        // Update bookmark icon state
        boolean isBookmarked = dbHelper.isBookmarked(paper.getId());
        binding.btnDetailBookmark.setImageResource(isBookmarked
                ? R.drawable.ic_bookmark_filled
                : R.drawable.ic_bookmark_border);

        // Update PDF button text according to local download status
        boolean isDownloaded = DownloadService.isPdfDownloaded(requireContext(), paper.getId());
        binding.btnDetailReadPdf.setText(isDownloaded ? R.string.btn_open_offline : R.string.btn_read_pdf);
    }

    private void setupActions() {
        // Action: Read PDF Native in-app
        binding.btnDetailReadPdf.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PdfReaderActivity.class);
            intent.putExtra("paper", paper);
            startActivity(intent);
            dismiss();
        });

        // Action: Copy BibTeX citation
        binding.btnDetailBibtex.setOnClickListener(v -> {
            Toast.makeText(requireContext(), R.string.fetching_bibtex, Toast.LENGTH_SHORT).show();
            ApiClient.getApiService().getBibtex(paper.getId()).enqueue(new Callback<BibtexResponse>() {
                @Override
                public void onResponse(Call<BibtexResponse> call, Response<BibtexResponse> response) {
                    if (!isAdded()) return;
                    String bibtexText;
                    if (response.isSuccessful() && response.body() != null && response.body().getBibtex() != null) {
                        bibtexText = response.body().getBibtex();
                    } else {
                        bibtexText = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "},\n  url={" + paper.getArxivUrl() + "}\n}";
                    }
                    copyToClipboard(bibtexText);
                }

                @Override
                public void onFailure(Call<BibtexResponse> call, Throwable t) {
                    if (!isAdded()) return;
                    String bibtexText = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "}\n}";
                    copyToClipboard(bibtexText);
                }
            });
        });

        // Action: Toggle SQLite Bookmark
        binding.btnDetailBookmark.setOnClickListener(v -> {
            boolean currentlySaved = dbHelper.isBookmarked(paper.getId());
            if (currentlySaved) {
                dbHelper.removeBookmark(paper.getId());
                binding.btnDetailBookmark.setImageResource(R.drawable.ic_bookmark_border);
                Toast.makeText(requireContext(), R.string.bookmark_removed, Toast.LENGTH_SHORT).show();
            } else {
                dbHelper.saveBookmark(paper, "");
                binding.btnDetailBookmark.setImageResource(R.drawable.ic_bookmark_filled);
                Toast.makeText(requireContext(), R.string.bookmark_saved, Toast.LENGTH_SHORT).show();
            }
        });

        // Action: Share via Android Native Intent
        binding.btnDetailShare.setOnClickListener(v -> {
            String shareText = paper.getTitle() + "\n" + paper.getArxivUrl();
            new ShareCompat.IntentBuilder(requireContext())
                    .setType("text/plain")
                    .setChooserTitle(getString(R.string.share_title))
                    .setText(shareText)
                    .startChooser();
        });
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("BibTeX Citation", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), R.string.copied_bibtex, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

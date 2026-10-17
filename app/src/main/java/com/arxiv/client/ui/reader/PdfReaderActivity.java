package com.arxiv.client.ui.reader;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.arxiv.client.R;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.download.DownloadService;
import com.arxiv.client.data.model.PaperItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.io.File;

public class PdfReaderActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvReaderTitle;
    private TextView tvPageIndicator;
    private WebView webPdfViewer;
    private ProgressBar pdfProgressBar;
    private SeekBar seekBarPages;
    private MaterialButton btnDownloadOffline;

    private PaperItem paper;
    private ArxivDbHelper dbHelper;
    private int totalPages = 15;
    private int currentPage = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        dbHelper = ArxivDbHelper.getInstance(this);
        paper = (PaperItem) getIntent().getSerializableExtra("paper");

        initViews();
        setupToolbar();
        setupPdfViewer();
        setupDownloadButton();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvReaderTitle = findViewById(R.id.tv_reader_title);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);
        webPdfViewer = findViewById(R.id.web_pdf_viewer);
        pdfProgressBar = findViewById(R.id.pdf_progress_bar);
        seekBarPages = findViewById(R.id.seek_bar_pages);
        btnDownloadOffline = findViewById(R.id.btn_download_offline);

        if (paper != null) {
            tvReaderTitle.setText(paper.getTitle());
            currentPage = dbHelper.getLastReadPage(paper.getId());
        }

        updatePageIndicator();

        seekBarPages.setMax(totalPages);
        seekBarPages.setProgress(currentPage);
        seekBarPages.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentPage = Math.max(1, progress);
                    updatePageIndicator();
                    if (paper != null) {
                        dbHelper.saveReadProgress(paper.getId(), currentPage);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupPdfViewer() {
        WebSettings settings = webPdfViewer.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webPdfViewer.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    pdfProgressBar.setVisibility(View.GONE);
                } else {
                    pdfProgressBar.setVisibility(View.VISIBLE);
                }
            }
        });

        webPdfViewer.setWebViewClient(new WebViewClient());

        // Check if PDF is already downloaded offline
        if (paper != null && DownloadService.isPdfDownloaded(this, paper.getId())) {
            File localFile = DownloadService.getLocalPdfFile(this, paper.getId());
            webPdfViewer.loadUrl("file://" + localFile.getAbsolutePath());
            btnDownloadOffline.setText("Đã tải Offline");
            btnDownloadOffline.setEnabled(false);
        } else if (paper != null) {
            String pdfUrl = paper.getPdfUrl();
            // Google Docs Viewer wrapper for smooth in-app rendering
            String viewerUrl = "https://docs.google.com/gview?embedded=true&url=" + pdfUrl;
            webPdfViewer.loadUrl(viewerUrl);
        }
    }

    private void setupDownloadButton() {
        if (paper == null) return;

        btnDownloadOffline.setOnClickListener(v -> {
            btnDownloadOffline.setText("Đang tải...");
            btnDownloadOffline.setEnabled(false);

            DownloadService.downloadPdf(this, paper.getPdfUrl(), paper.getId(), new DownloadService.DownloadCallback() {
                @Override
                public void onProgress(int progressPercent) {
                    btnDownloadOffline.setText(progressPercent + "%");
                }

                @Override
                public void onSuccess(File downloadedFile) {
                    btnDownloadOffline.setText("Đã lưu Offline");
                    btnDownloadOffline.setEnabled(false);
                    dbHelper.updateLocalPdfPath(paper.getId(), downloadedFile.getAbsolutePath());
                    Toast.makeText(PdfReaderActivity.this, "Đã lưu PDF vào bộ nhớ máy ảo! Có thể đọc khi bật Chế độ máy bay.", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String errorMessage) {
                    btnDownloadOffline.setText("Tải lại");
                    btnDownloadOffline.setEnabled(true);
                    Toast.makeText(PdfReaderActivity.this, "Tải lỗi: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void updatePageIndicator() {
        tvPageIndicator.setText(String.format("Trang %d/%d", currentPage, totalPages));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (paper != null) {
            dbHelper.saveReadProgress(paper.getId(), currentPage);
        }
    }
}

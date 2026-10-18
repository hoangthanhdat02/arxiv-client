package com.arxiv.client.ui.reader;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private ImageView imgPdfPage;
    private ProgressBar pdfProgressBar;
    private SeekBar seekBarPages;
    private MaterialButton btnDownloadOffline;

    // Download progress views
    private LinearLayout layoutDownloadProgress;
    private ProgressBar progressBarDownload;
    private TextView tvDownloadPercent;
    private TextView tvDownloadStatus;

    private PaperItem paper;
    private ArxivDbHelper dbHelper;
    private int totalPages = 15;
    private int currentPage = 1;

    // Native PDF Renderer
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPageObject;
    private ParcelFileDescriptor fileDescriptor;

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
        imgPdfPage = findViewById(R.id.img_pdf_page);
        pdfProgressBar = findViewById(R.id.pdf_progress_bar);
        seekBarPages = findViewById(R.id.seek_bar_pages);
        btnDownloadOffline = findViewById(R.id.btn_download_offline);

        layoutDownloadProgress = findViewById(R.id.layout_download_progress);
        progressBarDownload = findViewById(R.id.progress_bar_download);
        tvDownloadPercent = findViewById(R.id.tv_download_percent);
        tvDownloadStatus = findViewById(R.id.tv_download_status);

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
                    if (paper != null && DownloadService.isPdfDownloaded(PdfReaderActivity.this, paper.getId())) {
                        renderLocalPdfPage(currentPage);
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

        loadPdfContent();
    }

    private void loadPdfContent() {
        if (paper == null) return;

        if (DownloadService.isPdfDownloaded(this, paper.getId())) {
            renderLocalPdfPage(currentPage);
        } else {
            imgPdfPage.setVisibility(View.GONE);
            webPdfViewer.setVisibility(View.VISIBLE);
            String pdfUrl = paper.getPdfUrl();
            String viewerUrl = "https://docs.google.com/gview?embedded=true&url=" + pdfUrl;
            webPdfViewer.loadUrl(viewerUrl);
        }
    }

    private void renderLocalPdfPage(int pageIndex) {
        if (paper == null) return;
        File pdfFile = DownloadService.getLocalPdfFile(this, paper.getId());
        if (!pdfFile.exists()) return;

        try {
            if (pdfRenderer == null) {
                fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(fileDescriptor);
                totalPages = Math.max(1, pdfRenderer.getPageCount());
                seekBarPages.setMax(totalPages);
            }

            if (currentPageObject != null) {
                currentPageObject.close();
            }

            int targetIndex = Math.min(Math.max(0, pageIndex - 1), totalPages - 1);
            currentPageObject = pdfRenderer.openPage(targetIndex);

            // Create bitmap for display
            Bitmap bitmap = Bitmap.createBitmap(
                    currentPageObject.getWidth() * 2,
                    currentPageObject.getHeight() * 2,
                    Bitmap.Config.ARGB_8888
            );
            currentPageObject.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            webPdfViewer.setVisibility(View.GONE);
            pdfProgressBar.setVisibility(View.GONE);
            imgPdfPage.setImageBitmap(bitmap);
            imgPdfPage.setVisibility(View.VISIBLE);

            updatePageIndicator();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupDownloadButton() {
        if (paper == null) return;

        updateButtonState();

        btnDownloadOffline.setOnClickListener(v -> {
            if (DownloadService.isPdfDownloaded(this, paper.getId())) {
                renderLocalPdfPage(currentPage);
                Toast.makeText(this, "Đang đọc bản Offline từ bộ nhớ an toàn...", Toast.LENGTH_SHORT).show();
            } else {
                startPdfDownload();
            }
        });
    }

    private void updateButtonState() {
        if (paper == null) return;

        if (DownloadService.isPdfDownloaded(this, paper.getId())) {
            btnDownloadOffline.setText("Mở file Offline");
            btnDownloadOffline.setEnabled(true);
        } else {
            btnDownloadOffline.setText("Tải PDF về máy");
            btnDownloadOffline.setEnabled(true);
        }
    }

    private void startPdfDownload() {
        btnDownloadOffline.setText("Đang tải...");
        btnDownloadOffline.setEnabled(false);

        layoutDownloadProgress.setVisibility(View.VISIBLE);
        progressBarDownload.setProgress(0);
        tvDownloadPercent.setText("0%");
        tvDownloadStatus.setText("Đang tải PDF về bộ nhớ an toàn...");

        DownloadService.downloadPdf(this, paper.getPdfUrl(), paper.getId(), paper.getTitle(), new DownloadService.DownloadCallback() {
            @Override
            public void onProgress(int progressPercent) {
                progressBarDownload.setProgress(progressPercent);
                tvDownloadPercent.setText(progressPercent + "%");
                btnDownloadOffline.setText("Đang tải " + progressPercent + "%");
            }

            @Override
            public void onSuccess(File downloadedFile) {
                layoutDownloadProgress.setVisibility(View.GONE);
                btnDownloadOffline.setText("Mở file Offline");
                btnDownloadOffline.setEnabled(true);

                // Save to SQLite DB
                paper.setLocalPdfPath(downloadedFile.getAbsolutePath());
                dbHelper.saveBookmark(paper, "Đã tải Offline");
                dbHelper.updateLocalPdfPath(paper.getId(), downloadedFile.getAbsolutePath());

                // Render offline page
                renderLocalPdfPage(1);

                Toast.makeText(PdfReaderActivity.this, "Tải PDF thành công! Đang hiển thị bản Offline.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                layoutDownloadProgress.setVisibility(View.GONE);
                btnDownloadOffline.setText("Tải PDF về máy");
                btnDownloadOffline.setEnabled(true);
                Toast.makeText(PdfReaderActivity.this, "Tải thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePageIndicator() {
        tvPageIndicator.setText(String.format(java.util.Locale.getDefault(), "Trang %d/%d", currentPage, totalPages));
    }

    private void closePdfRenderer() {
        try {
            if (currentPageObject != null) {
                currentPageObject.close();
                currentPageObject = null;
            }
            if (pdfRenderer != null) {
                pdfRenderer.close();
                pdfRenderer = null;
            }
            if (fileDescriptor != null) {
                fileDescriptor.close();
                fileDescriptor = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (paper != null) {
            dbHelper.saveReadProgress(paper.getId(), currentPage);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closePdfRenderer();
    }
}

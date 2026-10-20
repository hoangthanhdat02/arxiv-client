package com.arxiv.client.ui.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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

    private ScrollView scrollPdfPage;
    private ImageView ivPdfPage;
    private ScrollView layoutOfflineAbstract;
    private TextView tvOfflineSummary;
    private TextView tvOfflineAuthors;

    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPdfPage;
    private ParcelFileDescriptor parcelFileDescriptor;

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

        scrollPdfPage = findViewById(R.id.scroll_pdf_page);
        ivPdfPage = findViewById(R.id.iv_pdf_page);
        layoutOfflineAbstract = findViewById(R.id.layout_offline_abstract);
        tvOfflineSummary = findViewById(R.id.tv_offline_summary);
        tvOfflineAuthors = findViewById(R.id.tv_offline_authors);

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
                    if (pdfRenderer != null) {
                        renderPage(currentPage - 1);
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
        if (paper == null) return;

        // Trường hợp 1: File PDF ĐÃ ĐƯỢC TẢI VỀ MÁY ẢO -> Dùng Native PdfRenderer cực nét và mượt mà
        if (DownloadService.isPdfDownloaded(this, paper.getId())) {
            File localFile = DownloadService.getLocalPdfFile(this, paper.getId());
            openPdfRenderer(localFile);
            btnDownloadOffline.setText("Đã lưu Offline");
            btnDownloadOffline.setEnabled(false);
            return;
        }

        // Trường hợp 2: Chưa tải file PDF nhưng CÓ INTERNET -> Dùng Google Docs Viewer
        if (isNetworkConnected()) {
            scrollPdfPage.setVisibility(View.GONE);
            layoutOfflineAbstract.setVisibility(View.GONE);
            webPdfViewer.setVisibility(View.VISIBLE);

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

            String pdfUrl = paper.getPdfUrl();
            String viewerUrl = "https://docs.google.com/gview?embedded=true&url=" + pdfUrl;
            webPdfViewer.loadUrl(viewerUrl);
            return;
        }

        // Trường hợp 3: ĐANG BẬT CHẾ ĐỘ MÁY BAY (Airplane Mode) & Chưa tải PDF
        // -> Hiển thị toàn bộ tóm tắt nghiên cứu & tác giả từ SQLite cục bộ (Không để lỗi net::ERR_INTERNET_DISCONNECTED)
        webPdfViewer.setVisibility(View.GONE);
        scrollPdfPage.setVisibility(View.GONE);
        pdfProgressBar.setVisibility(View.GONE);
        layoutOfflineAbstract.setVisibility(View.VISIBLE);

        tvOfflineAuthors.setText(paper.getAuthorsString() + " • " + paper.getFormattedDate() + " • " + paper.getPrimaryCategory());
        tvOfflineSummary.setText(paper.getSummary());
        tvPageIndicator.setText("Tóm tắt SQLite");
    }

    private void openPdfRenderer(File file) {
        try {
            webPdfViewer.setVisibility(View.GONE);
            layoutOfflineAbstract.setVisibility(View.GONE);
            pdfProgressBar.setVisibility(View.GONE);
            scrollPdfPage.setVisibility(View.VISIBLE);

            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
            }
            if (pdfRenderer != null) {
                pdfRenderer.close();
            }

            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);
            totalPages = Math.max(1, pdfRenderer.getPageCount());
            seekBarPages.setMax(totalPages);

            renderPage(currentPage - 1);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi đọc PDF Native: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void renderPage(int index) {
        if (pdfRenderer == null || index < 0 || index >= totalPages) return;
        try {
            if (currentPdfPage != null) {
                currentPdfPage.close();
            }
            currentPdfPage = pdfRenderer.openPage(index);
            Bitmap bitmap = Bitmap.createBitmap(currentPdfPage.getWidth() * 2, currentPdfPage.getHeight() * 2, Bitmap.Config.ARGB_8888);
            currentPdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            ivPdfPage.setImageBitmap(bitmap);
            currentPage = index + 1;
            updatePageIndicator();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
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
                    // Ngay khi tải xong, chuyển sang Native PdfRenderer ngay
                    openPdfRenderer(downloadedFile);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (currentPdfPage != null) currentPdfPage.close();
            if (pdfRenderer != null) pdfRenderer.close();
            if (parcelFileDescriptor != null) parcelFileDescriptor.close();
        } catch (Exception ignored) {}
    }
}

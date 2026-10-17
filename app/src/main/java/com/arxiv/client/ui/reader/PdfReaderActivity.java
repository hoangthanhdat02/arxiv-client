package com.arxiv.client.ui.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.arxiv.client.R;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.download.DownloadService;
import com.arxiv.client.data.model.PaperItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * PdfReaderActivity: In-App Native PDF Reader module.
 * Authored by Member 5 (Ngo).
 *
 * Implements:
 * 1. Native Android PdfRenderer for zero-dependency local rendering (100% offline-ready).
 * 2. Multi-touch Pinch-to-zoom, double-tap zoom, and quick toolbar zoom controls for AVD mouse.
 * 3. Bidirectional SeekBar slider for quick jumping across pages.
 * 4. Automatic reading progress persistence using SharedPreferences & SQLite.
 * 5. Seamless offline/online detection and background streaming.
 */
public class PdfReaderActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "arxiv_pdf_reader_prefs";
    private static final String KEY_PAGE_PREFIX = "last_page_";

    // View Components
    private MaterialToolbar toolbar;
    private ImageButton btnZoomIn;
    private ImageButton btnZoomOut;
    private ImageButton btnZoomReset;
    private TextView tvReaderTitle;
    private TextView tvOfflineBadge;
    private TextView tvPageIndicator;
    private ViewPager2 viewPagerPdf;
    private View layoutLoading;
    private ProgressBar pdfProgressBar;
    private TextView tvLoadingStatus;
    private View layoutError;
    private TextView tvErrorMessage;
    private MaterialButton btnRetry;
    private ImageButton btnPrevPage;
    private SeekBar seekBarPages;
    private ImageButton btnNextPage;
    private MaterialButton btnDownloadOffline;

    // Data & Native Renderer State
    private PaperItem paper;
    private ArxivDbHelper dbHelper;
    private SharedPreferences prefs;
    private ParcelFileDescriptor fileDescriptor;
    private PdfRenderer pdfRenderer;
    private PdfPagerAdapter pagerAdapter;

    private int totalPages = 1;
    private int currentPage = 1;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient = new OkHttpClient();
    private okhttp3.Call activeDownloadCall;

    /** Initializes reader state and controls, then loads the paper supplied by the intent. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        dbHelper = ArxivDbHelper.getInstance(this);
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        paper = (PaperItem) getIntent().getSerializableExtra("paper");

        initViews();
        setupToolbar();
        setupZoomControls();
        setupPageNavigation();
        setupDownloadButton();

        loadPdfDocument();
    }

    /** Binds reader views, restores the saved page, and connects the retry action. */
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        btnZoomIn = findViewById(R.id.btn_zoom_in);
        btnZoomOut = findViewById(R.id.btn_zoom_out);
        btnZoomReset = findViewById(R.id.btn_zoom_reset);
        tvReaderTitle = findViewById(R.id.tv_reader_title);
        tvOfflineBadge = findViewById(R.id.tv_offline_badge);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);
        viewPagerPdf = findViewById(R.id.view_pager_pdf);
        layoutLoading = findViewById(R.id.layout_loading);
        pdfProgressBar = findViewById(R.id.pdf_progress_bar);
        tvLoadingStatus = findViewById(R.id.tv_loading_status);
        layoutError = findViewById(R.id.layout_error);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        btnRetry = findViewById(R.id.btn_retry);
        btnPrevPage = findViewById(R.id.btn_prev_page);
        seekBarPages = findViewById(R.id.seek_bar_pages);
        btnNextPage = findViewById(R.id.btn_next_page);
        btnDownloadOffline = findViewById(R.id.btn_download_offline);

        if (paper != null) {
            tvReaderTitle.setText(paper.getTitle());
            currentPage = getSavedLastPage(paper.getId());
        }

        btnRetry.setOnClickListener(v -> loadPdfDocument());
    }

    /** Makes the toolbar navigation action close the reader. */
    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    /** Connects toolbar zoom actions to the currently bound page image. */
    private void setupZoomControls() {
        btnZoomIn.setOnClickListener(v -> {
            ZoomableImageView currentView = getCurrentPageImageView();
            if (currentView != null) currentView.zoomIn();
        });

        btnZoomOut.setOnClickListener(v -> {
            ZoomableImageView currentView = getCurrentPageImageView();
            if (currentView != null) currentView.zoomOut();
        });

        btnZoomReset.setOnClickListener(v -> {
            ZoomableImageView currentView = getCurrentPageImageView();
            if (currentView != null) currentView.resetZoom();
        });
    }

    /** Returns the current page image, or {@code null} if it is not bound yet. */
    private ZoomableImageView getCurrentPageImageView() {
        if (pagerAdapter != null && viewPagerPdf != null) {
            return pagerAdapter.getActivePageImageView(viewPagerPdf.getCurrentItem());
        }
        return null;
    }

    /** Connects page buttons and the slider, and saves progress when the pager changes pages. */
    private void setupPageNavigation() {
        // Prev & Next Buttons
        btnPrevPage.setOnClickListener(v -> {
            if (viewPagerPdf != null && currentPage > 1) {
                viewPagerPdf.setCurrentItem(currentPage - 2, true);
            }
        });

        btnNextPage.setOnClickListener(v -> {
            if (viewPagerPdf != null && currentPage < totalPages) {
                viewPagerPdf.setCurrentItem(currentPage, true);
            }
        });

        // SeekBar slider for jumping directly across pages
        seekBarPages.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            /** Converts user slider changes from one-based pages to zero-based pager indices. */
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && viewPagerPdf != null) {
                    int targetPage = Math.max(1, progress);
                    viewPagerPdf.setCurrentItem(targetPage - 1, true);
                }
            }

            /** Leaves page navigation to progress changes when slider dragging begins. */
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            /** Requires no additional action when slider dragging ends. */
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // ViewPager2 page change listener
        viewPagerPdf.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            /** Updates the one-based page state, slider, and saved reading progress. */
            @Override
            public void onPageSelected(int position) {
                currentPage = position + 1;
                updatePageIndicator();
                seekBarPages.setProgress(currentPage);

                if (paper != null) {
                    persistReadProgress(paper.getId(), currentPage);
                }
            }
        });
    }

    /** Shows existing offline availability or connects the download action and its callbacks. */
    private void setupDownloadButton() {
        if (paper == null) return;

        if (DownloadService.isPdfDownloaded(this, paper.getId())) {
            btnDownloadOffline.setText("Đã lưu Offline");
            btnDownloadOffline.setEnabled(false);
            tvOfflineBadge.setVisibility(View.VISIBLE);
            return;
        }

        btnDownloadOffline.setOnClickListener(v -> {
            btnDownloadOffline.setText("Đang tải...");
            btnDownloadOffline.setEnabled(false);

            DownloadService.downloadPdf(this, paper.getPdfUrl(), paper.getId(), new DownloadService.DownloadCallback() {
                /** Displays the download completion percentage on the button. */
                @Override
                public void onProgress(int progressPercent) {
                    btnDownloadOffline.setText(progressPercent + "%");
                }

                /** Marks the paper as available offline and records its local file path. */
                @Override
                public void onSuccess(File downloadedFile) {
                    btnDownloadOffline.setText("Đã lưu Offline");
                    btnDownloadOffline.setEnabled(false);
                    tvOfflineBadge.setVisibility(View.VISIBLE);
                    dbHelper.updateLocalPdfPath(paper.getId(), downloadedFile.getAbsolutePath());
                    Toast.makeText(PdfReaderActivity.this, "Đã lưu vào bộ nhớ máy ảo! Có thể đọc khi bật Chế độ máy bay.", Toast.LENGTH_LONG).show();
                }

                /** Re-enables the download action and displays the failure message. */
                @Override
                public void onError(String errorMessage) {
                    btnDownloadOffline.setText("Tải Offline");
                    btnDownloadOffline.setEnabled(true);
                    Toast.makeText(PdfReaderActivity.this, "Lỗi tải tệp: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Resolves the PDF file from local storage or downloads it to cache.
     * Then initializes the native PdfRenderer.
     */
    private void loadPdfDocument() {
        if (paper == null) {
            showError("Không tìm thấy thông tin bài báo.");
            return;
        }

        showLoading("Đang chuẩn bị tài liệu PDF...");

        // 1. Check if downloaded permanently in app storage (Offline-first)
        if (DownloadService.isPdfDownloaded(this, paper.getId())) {
            File localFile = DownloadService.getLocalPdfFile(this, paper.getId());
            tvOfflineBadge.setVisibility(View.VISIBLE);
            btnDownloadOffline.setText("Đã lưu Offline");
            btnDownloadOffline.setEnabled(false);
            initRendererWithFile(localFile);
            return;
        }

        // 2. Check if already cached in temporary cache directory
        File cacheFile = new File(getCacheDir(), "temp_" + paper.getId().replace("/", "_") + ".pdf");
        if (cacheFile.exists() && cacheFile.length() > 0) {
            initRendererWithFile(cacheFile);
            return;
        }

        // 3. Otherwise stream/download from arXiv online
        downloadToCacheAndRender(paper.getPdfUrl(), cacheFile);
    }

    /**
     * Downloads a PDF to a temporary file on a background thread, then publishes it
     * to the cache. Opens it if the activity is still acti    /**
     * Downloads a paper's PDF to a temporary file, moves it to destination, and displays it.
     *
     * @param url source PDF URL
     * @param destinationFile cache path published after the download completes
     */
    private void downloadToCacheAndRender(String url, File destinationFile) {
        showLoading("Đang tải tệp PDF từ arXiv...");

        new Thread(() -> {
            File temporaryFile = null;
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "arXiv-Mobile-Client/1.0")
                        .build();

                activeDownloadCall = httpClient.newCall(request);
                temporaryFile = File.createTempFile("pdf_", ".tmp", destinationFile.getParentFile());
                try (Response response = activeDownloadCall.execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            showError("Không thể tải tài liệu từ máy chủ (Mã lỗi: " + response.code() + ").\nNếu đang bật Chế độ máy bay, vui lòng mở các bài báo đã tải offline.");
                        });
                        return;
                    }

                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(temporaryFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                        fos.flush();
                    }
                }

                if (!temporaryFile.renameTo(destinationFile)) {
                    throw new IOException("Could not move PDF download to cache");
                }

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    initRendererWithFile(destinationFile);
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showError("Không thể kết nối máy chủ.\nNếu đang bật Chế độ máy bay, vui lòng mở các bài báo trong tab Đã lưu.");
                });
            } finally {
                activeDownloadCall = null;
                if (temporaryFile != null && temporaryFile.exists()) {
                    temporaryFile.delete();
                }
            }
        }).start();
    }

    /**
     * Opens a local PDF, replaces the renderer and adapter, and restores the saved page
     * within the document's bounds. Displays an error if initialization fails.
     *
     * @param pdfFile local PDF file to open for reading
     */
    private void initRendererWithFile(File pdfFile) {
        try {
            closeRenderer();

            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            totalPages = Math.max(1, pdfRenderer.getPageCount());

            // Initialize ViewPager2 Adapter
            pagerAdapter = new PdfPagerAdapter(this, pdfRenderer, totalPages);
            viewPagerPdf.setAdapter(pagerAdapter);

            // Configure SeekBar range
            seekBarPages.setMax(totalPages);

            // Restore saved page
            int targetPage = Math.min(Math.max(1, currentPage), totalPages);
            currentPage = targetPage;
            viewPagerPdf.setCurrentItem(targetPage - 1, false);
            seekBarPages.setProgress(targetPage);

            updatePageIndicator();
            showContent();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi khởi tạo trình đọc PDF native: " + e.getMessage());
        }
    }

    /** Displays the current one-based page number and document page count. */
    private void updatePageIndicator() {
        tvPageIndicator.setText(String.format("Trang %d/%d", currentPage, totalPages));
    }

    /**
     * Saves reading progress to preferences and, when available, SQLite.
     *
     * @param paperId identifier used to associate progress with a paper
     * @param page one-based page number to save
     */
    private void persistReadProgress(String paperId, int page) {
        // 1. Save to SharedPreferences (Specific requirement of Member 5)
        prefs.edit().putInt(KEY_PAGE_PREFIX + paperId, page).apply();

        // 2. Sync to SQLite database (Member 7 collaboration)
        if (dbHelper != null) {
            dbHelper.saveReadProgress(paperId, page);
        }
    }

    /**
     * Looks up reading progress in preferences, falling back to SQLite or page one.
     *
     * @param paperId identifier of the paper whose progress is requested
     * @return the saved one-based page number, or one if no progress is available
     */
    private int getSavedLastPage(String paperId) {
        // First check SharedPreferences
        int savedPage = prefs.getInt(KEY_PAGE_PREFIX + paperId, -1);
        if (savedPage > 0) {
            return savedPage;
        }

        // Fallback to SQLite
        if (dbHelper != null) {
            return dbHelper.getLastReadPage(paperId);
        }

        return 1;
    }

    /** Shows the loading overlay with the supplied status and hides the document and error. */
    private void showLoading(String message) {
        layoutLoading.setVisibility(View.VISIBLE);
        tvLoadingStatus.setText(message);
        layoutError.setVisibility(View.GONE);
        viewPagerPdf.setVisibility(View.INVISIBLE);
    }

    /** Shows the document pager and hides the loading and error overlays. */
    private void showContent() {
        layoutLoading.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        viewPagerPdf.setVisibility(View.VISIBLE);
    }

    /** Shows the error overlay with the supplied message and hides the document and loader. */
    private void showError(String message) {
        layoutLoading.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(message);
        viewPagerPdf.setVisibility(View.INVISIBLE);
    }

    /** Cleans up the adapter, closes the renderer and file descriptor, and logs failures. */
    private void closeRenderer() {
        if (activeDownloadCall != null) {
            try {
                activeDownloadCall.cancel();
            } catch (Exception ignored) {
            }
            activeDownloadCall = null;
        }

        try {
            if (pagerAdapter != null) {
                pagerAdapter.cleanUp();
                pagerAdapter = null;
            }
            if (pdfRenderer != null) {
                synchronized (pdfRenderer) {
                    pdfRenderer.close();
                }
                pdfRenderer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (fileDescriptor != null) {
                fileDescriptor.close();
                fileDescriptor = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Saves the paper's current reading position when the activity pauses. */
    @Override
    protected void onPause() {
        super.onPause();
        if (paper != null) {
            persistReadProgress(paper.getId(), currentPage);
        }
    }

    /** Releases PDF rendering resources before the activity is destroyed. */
    @Override
    protected void onDestroy() {
        closeRenderer();
        super.onDestroy();
    }
}

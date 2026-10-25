package com.arxiv.client.ui.reader;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.arxiv.client.R;
import com.arxiv.client.data.api.ApiClient;
import com.arxiv.client.data.db.ArxivDbHelper;
import com.arxiv.client.data.download.DownloadService;
import com.arxiv.client.data.model.BibtexResponse;
import com.arxiv.client.data.model.PaperItem;
import com.arxiv.client.data.model.PaperResponse;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Trình đọc PDF Native trong App sử dụng Android PdfRenderer (Thành viên 5 - Ngo).
 * Hoàn toàn KHÔNG sử dụng WebView theo đúng yêu cầu phân hệ kỹ thuật.
 *
 * Tính năng điều khiển & hiệu năng tối ưu:
 * 1. SeekBar phản hồi tức thì (Real-time): Kéo đến trang nào nạp và hiển thị ngay trang đó.
 * 2. Kéo lướt trang mượt mà từng trang một: Dù kéo mạnh hay nhanh đến đâu cũng chỉ lật chính xác 1 trang, có hiệu ứng chuyển cảnh mềm mại.
 * 3. Background Asynchronous Rendering (ExecutorService): Kết xuất trang ngầm, không bao giờ gây đơ UI thread.
 * 4. LruCache & Prefetching: Lưu đệm các trang đã đọc và tải trước trang lân cận để lật trang 0ms.
 * 5. Smooth Animated Zoom: Phóng to/thu nhỏ mượt mà với ValueAnimator (220ms).
 */
public class PdfReaderActivity extends AppCompatActivity {

    private static final String PREF_NAME = "arxiv_prefs";
    private static final String PREF_KEY_PAGE_PREFIX = "pdf_last_page_";
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 4.0f;

    // View references
    private MaterialToolbar toolbar;
    private TextView tvReaderTitle;
    private TextView tvPageIndicator;
    private TextView tvZoomLevel;
    private ImageView imgPdfPage;
    private ProgressBar pdfProgressBar;
    private SeekBar seekBarPages;
    private MaterialButton btnDownloadOffline;
    private MaterialButton btnPrevPage;
    private MaterialButton btnNextPage;
    private MaterialButton btnZoomIn;
    private MaterialButton btnZoomOut;
    private MaterialButton btnZoomReset;
    private MaterialButton btnBibtex;

    // Download & Error layouts
    private LinearLayout layoutDownloadProgress;
    private ProgressBar progressBarDownload;
    private TextView tvDownloadPercent;
    private TextView tvDownloadStatus;
    private LinearLayout layoutPdfError;
    private TextView tvPdfError;
    private MaterialButton btnRetryPdf;

    // Data & state
    private PaperItem paper;
    private ArxivDbHelper dbHelper;
    private SharedPreferences prefs;

    private int totalPages = 1;
    private int currentPage = 1;
    private boolean isDownloading = false;
    private boolean isDraggingSeekBar = false;

    // Native PdfRenderer objects
    private ParcelFileDescriptor fileDescriptor;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPageObject;
    private Bitmap currentBitmap;

    // Threading & Memory Caching for Silky Smooth Rendering
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private LruCache<Integer, Bitmap> pageCache;
    private volatile int currentRenderToken = 0;
    private ValueAnimator zoomAnimator;

    // Matrix Pan & Zoom State
    private final Matrix matrix = new Matrix();
    private float baseScale = 1.0f;
    private float currentScale = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        dbHelper = ArxivDbHelper.getInstance(this);
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        initPageCache();
        initViews();
        setupToolbar();
        setupGestureInteractions();
        setupNavigationControls();
        setupZoomControls();
        setupApiActions();

        // Load paper data: either from caller Intent or via Backend REST API
        loadPaperFromIntentOrApi();
    }

    private void initPageCache() {
        int maxMemoryKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSizeKb = Math.max(1024, maxMemoryKb / 8);

        pageCache = new LruCache<Integer, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvReaderTitle = findViewById(R.id.tv_reader_title);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);
        tvZoomLevel = findViewById(R.id.tv_zoom_level);
        imgPdfPage = findViewById(R.id.img_pdf_page);
        pdfProgressBar = findViewById(R.id.pdf_progress_bar);
        seekBarPages = findViewById(R.id.seek_bar_pages);
        btnDownloadOffline = findViewById(R.id.btn_download_offline);
        btnPrevPage = findViewById(R.id.btn_prev_page);
        btnNextPage = findViewById(R.id.btn_next_page);
        btnBibtex = findViewById(R.id.btn_bibtex);

        btnZoomIn = findViewById(R.id.btn_zoom_in);
        btnZoomOut = findViewById(R.id.btn_zoom_out);
        btnZoomReset = findViewById(R.id.btn_zoom_reset);

        layoutDownloadProgress = findViewById(R.id.layout_download_progress);
        progressBarDownload = findViewById(R.id.progress_bar_download);
        tvDownloadPercent = findViewById(R.id.tv_download_percent);
        tvDownloadStatus = findViewById(R.id.tv_download_status);

        layoutPdfError = findViewById(R.id.layout_pdf_error);
        tvPdfError = findViewById(R.id.tv_pdf_error);
        btnRetryPdf = findViewById(R.id.btn_retry_pdf);

        updatePageIndicator();
        updateZoomLevelIndicator();
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────────────────────────
    // Real Backend API Connection (ExpressJS Gateway at 10.0.2.2:3001)
    // ─────────────────────────────────────────────────────────────

    private void setupApiActions() {
        if (btnBibtex != null) {
            btnBibtex.setOnClickListener(v -> fetchAndCopyBibtexFromApi());
        }
    }

    private void loadPaperFromIntentOrApi() {
        Object paperExtra = getIntent().getSerializableExtra("paper");
        if (paperExtra instanceof PaperItem) {
            paper = (PaperItem) paperExtra;
            onPaperLoaded();
            return;
        }

        String paperIdExtra = getIntent().getStringExtra("paper_id");
        if (paperIdExtra == null) paperIdExtra = getIntent().getStringExtra("paperId");
        if (paperIdExtra == null) paperIdExtra = getIntent().getStringExtra("id");

        if (paperIdExtra == null && getIntent().getData() != null) {
            String path = getIntent().getData().getPath();
            if (path != null && path.contains("abs/")) {
                paperIdExtra = path.substring(path.indexOf("abs/") + 4);
            } else if (path != null && path.contains("pdf/")) {
                paperIdExtra = path.substring(path.indexOf("pdf/") + 4).replace(".pdf", "");
            } else if (path != null && path.length() > 1) {
                paperIdExtra = path.substring(1);
            }
        }

        if (paperIdExtra != null && !paperIdExtra.trim().isEmpty()) {
            fetchPaperByIdFromApi(paperIdExtra.trim());
        } else {
            fetchLatestPaperFromApi();
        }
    }

    private void fetchPaperByIdFromApi(String paperId) {
        pdfProgressBar.setVisibility(View.VISIBLE);
        tvReaderTitle.setText("Loading paper data from ExpressJS Gateway (10.0.2.2:3001)...");

        String url = ApiClient.DEFAULT_BASE_URL + "papers/" + paperId;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    pdfProgressBar.setVisibility(View.GONE);
                    showError("Cannot connect to ExpressJS Gateway server:\n" + e.getMessage() + "\n(Please verify backend server is running on port 3001)");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> {
                        pdfProgressBar.setVisibility(View.GONE);
                        showError("ExpressJS server returned HTTP error: " + response.code());
                    });
                    return;
                }

                String jsonStr = response.body().string();
                try {
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();
                    if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull()) {
                        JsonObject dataObj = jsonObject.getAsJsonObject("data");
                        PaperItem fetchedPaper = new Gson().fromJson(dataObj, PaperItem.class);
                        runOnUiThread(() -> {
                            paper = fetchedPaper;
                            onPaperLoaded();
                        });
                    } else {
                        runOnUiThread(() -> showError("Paper not found with ID: " + paperId));
                    }
                } catch (Exception ex) {
                    runOnUiThread(() -> showError("Error parsing paper JSON: " + ex.getMessage()));
                }
            }
        });
    }

    private void fetchLatestPaperFromApi() {
        pdfProgressBar.setVisibility(View.VISIBLE);
        tvReaderTitle.setText("Fetching latest paper from arXiv via Gateway...");

        ApiClient.getApiService().getPapers("cs.AI", 0, 1).enqueue(new retrofit2.Callback<PaperResponse>() {
            @Override
            public void onResponse(retrofit2.Call<PaperResponse> call, retrofit2.Response<PaperResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && !response.body().getData().isEmpty()) {
                    paper = response.body().getData().get(0);
                    onPaperLoaded();
                } else {
                    pdfProgressBar.setVisibility(View.GONE);
                    showError("No paper data received from ExpressJS Gateway (10.0.2.2:3001).");
                }
            }

            @Override
            public void onFailure(retrofit2.Call<PaperResponse> call, Throwable t) {
                pdfProgressBar.setVisibility(View.GONE);
                showError("ExpressJS Gateway connection error:\n" + t.getMessage());
            }
        });
    }

    private void onPaperLoaded() {
        if (paper == null) return;
        tvReaderTitle.setText(paper.getTitle());
        currentPage = loadSavedPage();
        resolveAndLoadPdf();
    }

    private void fetchAndCopyBibtexFromApi() {
        if (paper == null || paper.getId().isEmpty()) {
            Toast.makeText(this, "No paper information available for BibTeX", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Fetching BibTeX citation from Backend Gateway...", Toast.LENGTH_SHORT).show();

        ApiClient.getApiService().getBibtex(paper.getId()).enqueue(new retrofit2.Callback<BibtexResponse>() {
            @Override
            public void onResponse(retrofit2.Call<BibtexResponse> call, retrofit2.Response<BibtexResponse> response) {
                String bibtexText = null;
                if (response.isSuccessful() && response.body() != null && response.body().getBibtex() != null) {
                    bibtexText = response.body().getBibtex();
                }
                if (bibtexText == null || bibtexText.isEmpty()) {
                    bibtexText = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "},\n  url={" + paper.getArxivUrl() + "}\n}";
                }

                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("BibTeX Citation", bibtexText);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(PdfReaderActivity.this, "BibTeX citation copied to clipboard!", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(retrofit2.Call<BibtexResponse> call, Throwable t) {
                String fallback = "@misc{arxiv:" + paper.getId() + ",\n  title={" + paper.getTitle() + "},\n  author={" + paper.getAuthorsString() + "}\n}";
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("BibTeX Citation", fallback);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(PdfReaderActivity.this, "BibTeX citation (fallback) copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Single-Page Swiping & Fluid Interactive Dragging
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestureInteractions() {
        imgPdfPage.setScaleType(ImageView.ScaleType.MATRIX);

        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float targetScale = currentScale * factor;

                if (targetScale < MIN_ZOOM) {
                    factor = MIN_ZOOM / currentScale;
                    currentScale = MIN_ZOOM;
                } else if (targetScale > MAX_ZOOM) {
                    factor = MAX_ZOOM / currentScale;
                    currentScale = MAX_ZOOM;
                } else {
                    currentScale = targetScale;
                }

                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                fixTranslation();
                imgPdfPage.setImageMatrix(matrix);
                updateZoomLevelIndicator();
                return true;
            }
        });

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                imgPdfPage.setTranslationX(0f);
                if (currentScale > 1.25f) {
                    smoothZoomTo(1.0f, imgPdfPage.getWidth() / 2f, imgPdfPage.getHeight() / 2f);
                } else {
                    smoothZoomTo(2.4f, e.getX(), e.getY());
                }
                return true;
            }
        });

        imgPdfPage.setOnTouchListener(new View.OnTouchListener() {
            private float touchStartX;
            private float touchStartY;
            private float lastTouchX;
            private float lastTouchY;
            private boolean isMultiTouch = false;
            private boolean hasTurnedPageInThisGesture = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleDetector.onTouchEvent(event);
                gestureDetector.onTouchEvent(event);

                int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getX();
                        touchStartY = event.getY();
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        isMultiTouch = false;
                        hasTurnedPageInThisGesture = false;
                        imgPdfPage.animate().cancel();
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        isMultiTouch = true;
                        imgPdfPage.setTranslationX(0f);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (!scaleDetector.isInProgress() && !isMultiTouch) {
                            if (currentScale > 1.05f) {
                                // Pan image when zoomed in
                                float dx = event.getX() - lastTouchX;
                                float dy = event.getY() - lastTouchY;
                                if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                                    matrix.postTranslate(dx, dy);
                                    fixTranslation();
                                    imgPdfPage.setImageMatrix(matrix);
                                }
                            } else if (!hasTurnedPageInThisGesture) {
                                // Interactive tactile drag feedback when at normal fit (1.0x)
                                float diffX = event.getX() - touchStartX;
                                float diffY = event.getY() - touchStartY;
                                if (Math.abs(diffX) > Math.abs(diffY)) {
                                    float clampedOffset = Math.max(-180f, Math.min(180f, diffX * 0.45f));
                                    imgPdfPage.setTranslationX(clampedOffset);
                                }
                            }
                        }
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!isMultiTouch && currentScale <= 1.08f && !hasTurnedPageInThisGesture) {
                            float diffX = event.getX() - touchStartX;
                            float diffY = event.getY() - touchStartY;
                            float threshold = 65f; // px drag threshold for mouse or finger

                            // STRICT SINGLE-PAGE TURN: Only turn exactly 1 page per gesture
                            if (Math.abs(diffX) > threshold && Math.abs(diffX) > Math.abs(diffY) * 1.2f) {
                                hasTurnedPageInThisGesture = true;
                                if (diffX < 0) {
                                    turnOnePage(1); // Next page (+1)
                                } else {
                                    turnOnePage(-1); // Previous page (-1)
                                }
                            } else {
                                // Insufficient drag: spring smoothly back to center
                                imgPdfPage.animate().translationX(0f).setDuration(120).start();
                            }
                        } else if (currentScale > 1.08f) {
                            imgPdfPage.setTranslationX(0f);
                        }
                        isMultiTouch = false;
                        break;
                }
                return true;
            }
        });
    }

    /**
     * Strictly turns exactly 1 page (+1 or -1) with fluid visual slide animation
     */
    private void turnOnePage(int delta) {
        if (pdfRenderer == null) {
            imgPdfPage.animate().translationX(0f).setDuration(100).start();
            return;
        }

        if (delta > 0 && currentPage >= totalPages) {
            imgPdfPage.animate().translationX(0f).setDuration(150).start();
            Toast.makeText(this, "You are on the last page", Toast.LENGTH_SHORT).show();
            return;
        }

        if (delta < 0 && currentPage <= 1) {
            imgPdfPage.animate().translationX(0f).setDuration(150).start();
            Toast.makeText(this, "You are on the first page", Toast.LENGTH_SHORT).show();
            return;
        }

        final int targetPage = Math.max(1, Math.min(totalPages, currentPage + delta));
        final int width = imgPdfPage.getWidth() > 0 ? imgPdfPage.getWidth() : 720;
        final float slideOutX = (delta > 0) ? -width * 0.3f : width * 0.3f;
        final float slideInStart = (delta > 0) ? width * 0.3f : -width * 0.3f;

        imgPdfPage.animate()
                .translationX(slideOutX)
                .alpha(0.35f)
                .setDuration(110)
                .withEndAction(() -> {
                    imgPdfPage.setTranslationX(slideInStart);
                    renderPage(targetPage);
                    imgPdfPage.animate()
                            .translationX(0f)
                            .alpha(1.0f)
                            .setDuration(140)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private void setupZoomControls() {
        btnZoomIn.setOnClickListener(v -> {
            int w = imgPdfPage.getWidth();
            int h = imgPdfPage.getHeight();
            smoothZoomTo(currentScale * 1.35f, w / 2f, h / 2f);
        });

        btnZoomOut.setOnClickListener(v -> {
            int w = imgPdfPage.getWidth();
            int h = imgPdfPage.getHeight();
            smoothZoomTo(currentScale / 1.35f, w / 2f, h / 2f);
        });

        btnZoomReset.setOnClickListener(v -> {
            int w = imgPdfPage.getWidth();
            int h = imgPdfPage.getHeight();
            smoothZoomTo(1.0f, w / 2f, h / 2f);
        });
    }

    private void smoothZoomTo(float targetScale, float focusX, float focusY) {
        final float clampedTarget = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetScale));
        if (Math.abs(currentScale - clampedTarget) < 0.01f) return;

        if (zoomAnimator != null && zoomAnimator.isRunning()) {
            zoomAnimator.cancel();
        }

        final float startScale = currentScale;

        zoomAnimator = ValueAnimator.ofFloat(startScale, clampedTarget);
        zoomAnimator.setDuration(220);
        zoomAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        zoomAnimator.addUpdateListener(animation -> {
            float animatedScale = (float) animation.getAnimatedValue();
            float factor = animatedScale / currentScale;
            currentScale = animatedScale;

            matrix.postScale(factor, factor, focusX, focusY);
            fixTranslation();
            imgPdfPage.setImageMatrix(matrix);
            updateZoomLevelIndicator();
        });
        zoomAnimator.start();
    }

    private void resetZoomToFit() {
        if (currentBitmap == null) return;
        int viewWidth = imgPdfPage.getWidth();
        int viewHeight = imgPdfPage.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        float scaleX = (float) viewWidth / (float) currentBitmap.getWidth();
        float scaleY = (float) viewHeight / (float) currentBitmap.getHeight();
        baseScale = Math.min(scaleX, scaleY);
        currentScale = 1.0f;

        matrix.reset();
        matrix.postScale(baseScale, baseScale);

        float scaledW = currentBitmap.getWidth() * baseScale;
        float scaledH = currentBitmap.getHeight() * baseScale;
        float dx = (viewWidth - scaledW) / 2f;
        float dy = (viewHeight - scaledH) / 2f;
        matrix.postTranslate(dx, dy);

        imgPdfPage.setImageMatrix(matrix);
        updateZoomLevelIndicator();
    }

    private void fixTranslation() {
        if (currentBitmap == null) return;
        int viewWidth = imgPdfPage.getWidth();
        int viewHeight = imgPdfPage.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        RectF rect = new RectF(0, 0, currentBitmap.getWidth(), currentBitmap.getHeight());
        matrix.mapRect(rect);

        float deltaX = 0;
        float deltaY = 0;

        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left;
        } else {
            if (rect.left > 0) {
                deltaX = -rect.left;
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right;
            }
        }

        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top;
        } else {
            if (rect.top > 0) {
                deltaY = -rect.top;
            } else if (rect.bottom < viewHeight) {
                deltaY = viewHeight - rect.bottom;
            }
        }

        matrix.postTranslate(deltaX, deltaY);
    }

    private void updateZoomLevelIndicator() {
        int percent = Math.round(currentScale * 100);
        tvZoomLevel.setText(String.format(Locale.getDefault(), "%d%%", percent));
    }

    // ─────────────────────────────────────────────────────────────
    // Navigation Controls & Real-Time SeekBar Response
    // ─────────────────────────────────────────────────────────────

    private void setupNavigationControls() {
        btnPrevPage.setOnClickListener(v -> turnOnePage(-1));
        btnNextPage.setOnClickListener(v -> turnOnePage(1));

        // Real-time SeekBar listener: Loads and renders page immediately as you drag!
        seekBarPages.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int lastDispatchedPage = -1;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int target = Math.max(1, Math.min(progress, totalPages));
                    if (target != lastDispatchedPage) {
                        lastDispatchedPage = target;
                        renderPage(target);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isDraggingSeekBar = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isDraggingSeekBar = false;
                int target = Math.max(1, Math.min(seekBar.getProgress(), totalPages));
                renderPage(target);
                saveCurrentPage();
            }
        });

        btnDownloadOffline.setOnClickListener(v -> {
            if (paper == null) return;
            if (DownloadService.isPdfDownloaded(this, paper.getId())) {
                Toast.makeText(this, "Paper is already saved for offline reading.", Toast.LENGTH_SHORT).show();
            } else {
                startAutoDownloadAndOpen(paper.getPdfUrl(), paper.getId());
            }
        });

        btnRetryPdf.setOnClickListener(v -> resolveAndLoadPdf());
    }

    private void updatePageIndicator() {
        tvPageIndicator.setText(String.format(Locale.getDefault(), "Page %d/%d", currentPage, totalPages));
    }

    private void updateButtonState() {
        if (paper == null) return;
        boolean downloaded = DownloadService.isPdfDownloaded(this, paper.getId());
        if (downloaded) {
            btnDownloadOffline.setText("Saved Offline");
            btnDownloadOffline.setEnabled(true);
        } else if (isDownloading) {
            btnDownloadOffline.setText("Downloading...");
            btnDownloadOffline.setEnabled(false);
        } else {
            btnDownloadOffline.setText("Download PDF");
            btnDownloadOffline.setEnabled(true);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PDF Loading & Asynchronous Background Rendering with LruCache
    // ─────────────────────────────────────────────────────────────

    private void resolveAndLoadPdf() {
        if (paper == null) {
            showError("Paper information not found.");
            return;
        }

        layoutPdfError.setVisibility(View.GONE);

        File localFile = DownloadService.getLocalPdfFile(this, paper.getId());
        if (DownloadService.isValidPdfFile(localFile)) {
            openPdfFile(localFile);
        } else {
            String targetPdfUrl = paper.getPdfUrl();
            if (targetPdfUrl == null || targetPdfUrl.trim().isEmpty()) {
                targetPdfUrl = "https://arxiv.org/pdf/" + paper.getId();
            }
            startAutoDownloadAndOpen(targetPdfUrl, paper.getId());
        }
    }

    private void startAutoDownloadAndOpen(String pdfUrl, String paperId) {
        if (isDownloading) return;
        isDownloading = true;

        layoutDownloadProgress.setVisibility(View.VISIBLE);
        progressBarDownload.setProgress(0);
        tvDownloadPercent.setText("0%");
        tvDownloadStatus.setText("Downloading PDF to open in native reader...");
        pdfProgressBar.setVisibility(View.VISIBLE);
        imgPdfPage.setVisibility(View.GONE);
        layoutPdfError.setVisibility(View.GONE);
        updateButtonState();

        DownloadService.downloadPdf(this, pdfUrl, paperId, paper.getTitle(), new DownloadService.DownloadCallback() {
            @Override
            public void onProgress(int progressPercent) {
                runOnUiThread(() -> {
                    progressBarDownload.setProgress(progressPercent);
                    tvDownloadPercent.setText(progressPercent + "%");
                    btnDownloadOffline.setText("Downloading " + progressPercent + "%");
                });
            }

            @Override
            public void onSuccess(File downloadedFile) {
                runOnUiThread(() -> {
                    isDownloading = false;
                    layoutDownloadProgress.setVisibility(View.GONE);
                    pdfProgressBar.setVisibility(View.GONE);

                    paper.setLocalPdfPath(downloadedFile.getAbsolutePath());

                    if (dbHelper.isBookmarked(paper.getId())) {
                        dbHelper.updateLocalPdfPath(paper.getId(), downloadedFile.getAbsolutePath());
                    }

                    openPdfFile(downloadedFile);
                    updateButtonState();
                    Toast.makeText(PdfReaderActivity.this, "Download complete! Opening in native reader.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    isDownloading = false;
                    layoutDownloadProgress.setVisibility(View.GONE);
                    pdfProgressBar.setVisibility(View.GONE);
                    updateButtonState();
                    showError("Failed to download PDF:\n" + errorMessage + "\n(Please check network connection or retry)");
                });
            }
        });
    }

    private void openPdfFile(File file) {
        renderExecutor.execute(() -> {
            try {
                closePdfRendererSync();

                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(fileDescriptor);
                totalPages = Math.max(1, pdfRenderer.getPageCount());

                int savedPage = loadSavedPage();
                currentPage = Math.min(Math.max(1, savedPage), totalPages);

                runOnUiThread(() -> {
                    seekBarPages.setMax(totalPages);
                    updateButtonState();
                    renderPage(currentPage);
                });
            } catch (Exception e) {
                e.printStackTrace();
                closePdfRendererSync();
                runOnUiThread(() -> showError("Cannot initialize PdfRenderer from file:\n" + e.getMessage()));
            }
        });
    }

    /**
     * Renders page with LruCache check and real-time asynchronous background rendering
     */
    private void renderPage(int pageNumber) {
        if (pdfRenderer == null) return;
        final int targetPage = Math.max(1, Math.min(pageNumber, totalPages));
        currentPage = targetPage;

        updatePageIndicator();
        if (!isDraggingSeekBar) {
            seekBarPages.setProgress(currentPage);
        }
        saveCurrentPage();

        // 1. FAST PATH: Check in-memory LruCache for instant 0ms rendering
        Bitmap cached = pageCache.get(targetPage);
        if (cached != null) {
            displayPage(cached);
            triggerPrefetch(targetPage);
            return;
        }

        // 2. SLOW PATH: Rasterize page on background worker thread
        pdfProgressBar.setVisibility(View.VISIBLE);
        final int token = ++currentRenderToken;

        renderExecutor.execute(() -> {
            if (token != currentRenderToken || pdfRenderer == null) return;

            Bitmap renderedBitmap = rasterizePageSync(targetPage);
            if (renderedBitmap != null) {
                pageCache.put(targetPage, renderedBitmap);
            }

            if (token == currentRenderToken) {
                runOnUiThread(() -> {
                    if (renderedBitmap != null) {
                        displayPage(renderedBitmap);
                        triggerPrefetch(targetPage);
                    } else {
                        pdfProgressBar.setVisibility(View.GONE);
                        showError("Unable to render page " + targetPage);
                    }
                });
            }
        });
    }

    private synchronized Bitmap rasterizePageSync(int pageNumber) {
        if (pdfRenderer == null) return null;
        PdfRenderer.Page page = null;
        try {
            if (currentPageObject != null) {
                currentPageObject.close();
                currentPageObject = null;
            }

            page = pdfRenderer.openPage(pageNumber - 1);
            currentPageObject = page;

            int origW = page.getWidth();
            int origH = page.getHeight();

            DisplayMetrics dm = getResources().getDisplayMetrics();
            float renderScale = Math.min(2.2f, Math.max(1.3f, (float) dm.widthPixels / (float) Math.max(1, origW)));
            int bmpW = Math.max(1, Math.round(origW * renderScale));
            int bmpH = Math.max(1, Math.round(origH * renderScale));

            Bitmap bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } catch (OutOfMemoryError oom) {
            System.gc();
            try {
                if (page != null) {
                    Bitmap lowMemBitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.RGB_565);
                    Canvas canvas = new Canvas(lowMemBitmap);
                    canvas.drawColor(Color.WHITE);
                    page.render(lowMemBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return lowMemBitmap;
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void triggerPrefetch(int centerPage) {
        final int next = centerPage + 1;
        final int prev = centerPage - 1;

        renderExecutor.execute(() -> {
            if (pdfRenderer == null) return;
            if (next <= totalPages && pageCache.get(next) == null) {
                Bitmap nextBmp = rasterizePageSync(next);
                if (nextBmp != null) {
                    pageCache.put(next, nextBmp);
                }
            }
            if (prev >= 1 && pageCache.get(prev) == null) {
                Bitmap prevBmp = rasterizePageSync(prev);
                if (prevBmp != null) {
                    pageCache.put(prev, prevBmp);
                }
            }
        });
    }

    private void displayPage(Bitmap bitmap) {
        currentBitmap = bitmap;
        imgPdfPage.setImageBitmap(bitmap);
        resetZoomToFit();

        imgPdfPage.animate().cancel();
        imgPdfPage.setTranslationX(0f);

        if (isDraggingSeekBar) {
            imgPdfPage.setAlpha(1.0f);
        } else {
            imgPdfPage.setAlpha(0.8f);
            imgPdfPage.animate().alpha(1.0f).setDuration(100).start();
        }

        pdfProgressBar.setVisibility(View.GONE);
        layoutPdfError.setVisibility(View.GONE);
        imgPdfPage.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        layoutPdfError.setVisibility(View.VISIBLE);
        tvPdfError.setText(message);
        imgPdfPage.setVisibility(View.GONE);
        pdfProgressBar.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────
    // Persistence: SharedPreferences & SQLite Progress Sync
    // ─────────────────────────────────────────────────────────────

    private void saveCurrentPage() {
        if (paper == null || paper.getId().isEmpty()) return;

        prefs.edit().putInt(PREF_KEY_PAGE_PREFIX + paper.getId(), currentPage).apply();

        if (dbHelper != null) {
            dbHelper.saveReadProgress(paper.getId(), currentPage);
        }
    }

    private int loadSavedPage() {
        if (paper == null || paper.getId().isEmpty()) return 1;

        int saved = prefs.getInt(PREF_KEY_PAGE_PREFIX + paper.getId(), -1);
        if (saved > 0) return saved;

        if (dbHelper != null) {
            saved = dbHelper.getLastReadPage(paper.getId());
            if (saved > 0) return saved;
        }

        return 1;
    }

    private synchronized void closePdfRendererSync() {
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
        saveCurrentPage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveCurrentPage();

        if (zoomAnimator != null && zoomAnimator.isRunning()) {
            zoomAnimator.cancel();
        }
        if (pageCache != null) {
            pageCache.evictAll();
        }

        renderExecutor.execute(this::closePdfRendererSync);
        renderExecutor.shutdown();
    }
}

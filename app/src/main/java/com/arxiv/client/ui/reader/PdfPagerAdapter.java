package com.arxiv.client.ui.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.arxiv.client.R;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance adapter for rendering PDF pages in ViewPager2 using native PdfRenderer.
 * Includes LRU memory caching and background worker thread to ensure smooth 60fps scrolling.
 */
public class PdfPagerAdapter extends RecyclerView.Adapter<PdfPagerAdapter.PageViewHolder> {

    private final Context context;
    private final PdfRenderer pdfRenderer;
    private final int totalPages;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();

    // In-memory cache for rendered page bitmaps (up to 30 pages)
    private final LruCache<Integer, Bitmap> bitmapCache;

    // Track active holders to deliver zoom actions
    private final Map<Integer, PageViewHolder> activeHolders = new HashMap<>();

    public PdfPagerAdapter(Context context, PdfRenderer pdfRenderer, int totalPages) {
        this.context = context;
        this.pdfRenderer = pdfRenderer;
        this.totalPages = totalPages;

        // Use 25% of available memory for PDF page bitmap cache
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 4;
        bitmapCache = new LruCache<Integer, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.boundPosition = position;
        activeHolders.put(position, holder);

        Bitmap cached = bitmapCache.get(position);
        if (cached != null) {
            holder.progressBar.setVisibility(View.GONE);
            holder.zoomableImageView.setImageBitmap(cached);
            holder.zoomableImageView.fitToScreen();
            return;
        }

        holder.zoomableImageView.setImageBitmap(null);
        holder.progressBar.setVisibility(View.VISIBLE);

        renderExecutor.execute(() -> {
            Bitmap renderedBitmap = null;
            synchronized (pdfRenderer) {
                if (position >= 0 && position < totalPages) {
                    try {
                        PdfRenderer.Page page = pdfRenderer.openPage(position);

                        try {
                            // High DPI rendering scale (2.0x) for sharp mathematical formulas
                            float density = context.getResources().getDisplayMetrics().density;
                            float renderScale = Math.max(1.5f, density);

                            int targetWidth = (int) (page.getWidth() * renderScale);
                            int targetHeight = (int) (page.getHeight() * renderScale);

                            renderedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                            renderedBitmap.eraseColor(Color.WHITE);

                            page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        } finally {
                            page.close();
                        }

                        bitmapCache.put(position, renderedBitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            final Bitmap finalBitmap = renderedBitmap;
            mainHandler.post(() -> {
                if (holder.boundPosition == position) {
                    holder.progressBar.setVisibility(View.GONE);
                    if (finalBitmap != null) {
                        holder.zoomableImageView.setImageBitmap(finalBitmap);
                        holder.zoomableImageView.fitToScreen();
                    }
                }
            });
        });
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        super.onViewRecycled(holder);
        activeHolders.remove(holder.boundPosition);
        holder.zoomableImageView.resetZoom();
        holder.zoomableImageView.setImageBitmap(null);
    }

    @Override
    public int getItemCount() {
        return totalPages;
    }

    public ZoomableImageView getActivePageImageView(int position) {
        PageViewHolder holder = activeHolders.get(position);
        return holder != null ? holder.zoomableImageView : null;
    }

    public void cleanUp() {
        activeHolders.clear();
        bitmapCache.evictAll();
        renderExecutor.shutdownNow();
    }

    public static class PageViewHolder extends RecyclerView.ViewHolder {
        final ZoomableImageView zoomableImageView;
        final ProgressBar progressBar;
        int boundPosition = -1;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            zoomableImageView = itemView.findViewById(R.id.zoomable_image_page);
            progressBar = itemView.findViewById(R.id.page_loading_progress);
        }
    }
}

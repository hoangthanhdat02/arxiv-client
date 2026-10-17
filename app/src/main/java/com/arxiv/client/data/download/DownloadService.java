package com.arxiv.client.data.download;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadService {

    public interface DownloadCallback {
        void onProgress(int progressPercent);
        void onSuccess(File downloadedFile);
        void onError(String errorMessage);
    }

    private static final OkHttpClient client = new OkHttpClient();

    public static File getPdfDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "arxiv_pdfs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getLocalPdfFile(Context context, String paperId) {
        String cleanName = paperId.replace("/", "_") + ".pdf";
        return new File(getPdfDirectory(context), cleanName);
    }

    public static boolean isPdfDownloaded(Context context, String paperId) {
        File file = getLocalPdfFile(context, paperId);
        return file.exists() && file.length() > 0;
    }

    public static void downloadPdf(Context context, String pdfUrl, String paperId, DownloadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        File targetFile = getLocalPdfFile(context, paperId);

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(pdfUrl)
                        .header("User-Agent", "arXiv-Mobile-Client/1.0")
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError("Server returned code: " + response.code()));
                    return;
                }

                long totalBytes = response.body().contentLength();
                InputStream is = response.body().byteStream();
                FileOutputStream fos = new FileOutputStream(targetFile);

                byte[] buffer = new byte[8192];
                long readBytes = 0;
                int len;

                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    readBytes += len;
                    if (totalBytes > 0) {
                        int progress = (int) ((readBytes * 100) / totalBytes);
                        mainHandler.post(() -> callback.onProgress(progress));
                    }
                }

                fos.flush();
                fos.close();
                is.close();

                mainHandler.post(() -> callback.onSuccess(targetFile));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    public static long getCacheSize(Context context) {
        File dir = getPdfDirectory(context);
        long size = 0;
        if (dir.exists() && dir.listFiles() != null) {
            for (File f : dir.listFiles()) {
                size += f.length();
            }
        }
        return size;
    }

    public static void clearPdfCache(Context context) {
        File dir = getPdfDirectory(context);
        if (dir.exists() && dir.listFiles() != null) {
            for (File f : dir.listFiles()) {
                f.delete();
            }
        }
    }
}

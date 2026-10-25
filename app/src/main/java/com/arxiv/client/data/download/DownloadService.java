package com.arxiv.client.data.download;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DownloadService {

    private static final String TAG = "DownloadService";

    public interface DownloadCallback {
        void onProgress(int progressPercent);
        void onSuccess(File downloadedFile);
        void onError(String errorMessage);
    }

    private static final OkHttpClient client = new OkHttpClient();
    private static final String CHANNEL_ID = "arxiv_pdf_downloads";

    public static File getPdfDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "arxiv_pdfs");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                Log.w(TAG, "Cannot create arXiv PDFs directory");
            }
        }
        return dir;
    }

    public static File getLocalPdfFile(Context context, String paperId) {
        String cleanName = paperId.replace("/", "_") + ".pdf";
        return new File(getPdfDirectory(context), cleanName);
    }

    public static boolean isValidPdfFile(File file) {
        if (file == null || !file.exists() || file.length() < 4) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            if (read == 4) {
                // PDF magic number is %PDF (0x25, 0x50, 0x44, 0x46)
                return header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean isPdfDownloaded(Context context, String paperId) {
        File file = getLocalPdfFile(context, paperId);
        return file.exists() && file.length() > 0 && isValidPdfFile(file);
    }

    public static void downloadPdf(Context context, String pdfUrl, String paperId, DownloadCallback callback) {
        downloadPdf(context, pdfUrl, paperId, null, callback);
    }

    public static void downloadPdf(Context context, String pdfUrl, String paperId, String paperTitle, DownloadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        File targetFile = getLocalPdfFile(context, paperId);

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(pdfUrl)
                        .header("User-Agent", "arXiv-Mobile-Client/1.0")
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Server returned code: " + response.code()));
                    return;
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    mainHandler.post(() -> callback.onError("Empty response from server"));
                    return;
                }

                long totalBytes = responseBody.contentLength();
                InputStream is = responseBody.byteStream();
                FileOutputStream fos = new FileOutputStream(targetFile);

                byte[] buffer = new byte[8192];
                long readBytes = 0;
                int len;
                int lastReportedProgress = -1;

                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    readBytes += len;
                    if (totalBytes > 0) {
                        int progress = (int) ((readBytes * 100) / totalBytes);
                        if (progress != lastReportedProgress) {
                            lastReportedProgress = progress;
                            mainHandler.post(() -> callback.onProgress(progress));
                        }
                    }
                }

                fos.flush();
                fos.close();
                is.close();

                // Validate downloaded file is a genuine PDF (not HTML error/gateway timeout)
                if (!isValidPdfFile(targetFile)) {
                    if (targetFile.exists()) {
                        boolean deleted = targetFile.delete();
                        if (!deleted) {
                            Log.w(TAG, "Cannot delete invalid PDF file");
                        }
                    }
                    mainHandler.post(() -> callback.onError("Downloaded file is not a valid PDF format (server may have returned HTML error)."));
                    return;
                }

                // Show completion notification
                showDownloadCompleteNotification(context, paperTitle != null ? paperTitle : paperId);

                mainHandler.post(() -> callback.onSuccess(targetFile));
            } catch (Exception e) {
                if (targetFile.exists()) {
                    boolean deleted = targetFile.delete();
                    if (!deleted) {
                        Log.w(TAG, "Cannot delete temporary file");
                    }
                }
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    private static void showDownloadCompleteNotification(Context context, String titleText) {
        try {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "arXiv PDF Downloads",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for completed arXiv PDF downloads");
            notificationManager.createNotificationChannel(channel);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("PDF Download Completed")
                    .setContentText("Saved paper for offline reading: " + titleText)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Error displaying PDF download notification", e);
        }
    }

    public static long getCacheSize(Context context) {
        File dir = getPdfDirectory(context);
        long size = 0;
        File[] files = dir.listFiles();
        if (dir.exists() && files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    size += f.length();
                }
            }
        }
        return size;
    }

    public static void clearPdfCache(Context context) {
        File dir = getPdfDirectory(context);
        File[] files = dir.listFiles();
        if (dir.exists() && files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    boolean deleted = f.delete();
                    if (!deleted) {
                        Log.w(TAG, "Cannot delete file: " + f.getName());
                    }
                }
            }
        }
    }
}

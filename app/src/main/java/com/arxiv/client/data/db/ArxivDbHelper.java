package com.arxiv.client.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.arxiv.client.data.download.DownloadService;
import com.arxiv.client.data.model.PaperItem;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArxivDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "arxiv_client.db";
    private static final int DATABASE_VERSION = 2;

    // Table: Bookmarks
    public static final String TABLE_BOOKMARKS = "bookmarks";
    public static final String COL_ID = "id";
    public static final String COL_TITLE = "title";
    public static final String COL_SUMMARY = "summary";
    public static final String COL_AUTHORS = "authors";
    public static final String COL_PUBLISHED = "published";
    public static final String COL_CATEGORY = "category";
    public static final String COL_PDF_URL = "pdf_url";
    public static final String COL_LOCAL_PDF_PATH = "local_pdf_path";
    public static final String COL_NOTE = "note";
    public static final String COL_BIBTEX = "bibtex";
    public static final String COL_APA = "apa";
    public static final String COL_CREATED_AT = "created_at";

    // Table: Read History
    public static final String TABLE_HISTORY = "read_history";
    public static final String COL_LAST_PAGE = "last_page";
    public static final String COL_READ_TIME = "read_time";

    private static ArxivDbHelper instance;

    public static synchronized ArxivDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ArxivDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    public ArxivDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createBookmarksSql = "CREATE TABLE " + TABLE_BOOKMARKS + " ("
                + COL_ID + " TEXT PRIMARY KEY, "
                + COL_TITLE + " TEXT, "
                + COL_SUMMARY + " TEXT, "
                + COL_AUTHORS + " TEXT, "
                + COL_PUBLISHED + " TEXT, "
                + COL_CATEGORY + " TEXT, "
                + COL_PDF_URL + " TEXT, "
                + COL_LOCAL_PDF_PATH + " TEXT, "
                + COL_NOTE + " TEXT, "
                + COL_BIBTEX + " TEXT, "
                + COL_APA + " TEXT, "
                + COL_CREATED_AT + " INTEGER"
                + ")";

        String createHistorySql = "CREATE TABLE " + TABLE_HISTORY + " ("
                + COL_ID + " TEXT PRIMARY KEY, "
                + COL_LAST_PAGE + " INTEGER, "
                + COL_READ_TIME + " INTEGER"
                + ")";

        db.execSQL(createBookmarksSql);
        db.execSQL(createHistorySql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_BIBTEX + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + COL_APA + " TEXT");
            } catch (Exception ignored) {}
        }
    }

    // CRUD: Add or update bookmark with BibTeX & APA offline persistence
    public boolean saveBookmark(PaperItem paper, String note) {
        if (paper == null) return false;
        String id = paper.getId();
        if (id == null || id.trim().isEmpty()) return false;

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, id);
        cv.put(COL_TITLE, paper.getTitle() != null ? paper.getTitle() : "");
        cv.put(COL_SUMMARY, paper.getSummary() != null ? paper.getSummary() : "");
        cv.put(COL_AUTHORS, paper.getAuthorsString());
        cv.put(COL_PUBLISHED, paper.getPublished() != null ? paper.getPublished() : "");
        cv.put(COL_CATEGORY, paper.getPrimaryCategory() != null ? paper.getPrimaryCategory() : "");
        cv.put(COL_PDF_URL, paper.getPdfUrl() != null ? paper.getPdfUrl() : "");
        cv.put(COL_LOCAL_PDF_PATH, paper.getLocalPdfPath());

        String finalNote = (note != null && !note.isEmpty()) ? note : paper.getNote();
        cv.put(COL_NOTE, finalNote != null ? finalNote : "");

        String finalBibtex = (paper.getBibtex() != null && !paper.getBibtex().isEmpty())
                ? paper.getBibtex()
                : paper.generateDefaultBibtex();
        cv.put(COL_BIBTEX, finalBibtex);

        String finalApa = (paper.getApa() != null && !paper.getApa().isEmpty())
                ? paper.getApa()
                : paper.generateDefaultApa();
        cv.put(COL_APA, finalApa);

        cv.put(COL_CREATED_AT, System.currentTimeMillis());

        long result = db.insertWithOnConflict(TABLE_BOOKMARKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    // CRUD: Remove bookmark
    public boolean removeBookmark(String paperId) {
        if (paperId == null || paperId.trim().isEmpty()) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_BOOKMARKS, COL_ID + "=?", new String[]{paperId});
        return rows > 0;
    }

    // CRUD: Update personal research note
    public boolean updateNote(String paperId, String note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NOTE, note != null ? note : "");
        int rows = db.update(TABLE_BOOKMARKS, cv, COL_ID + "=?", new String[]{paperId});
        return rows > 0;
    }

    // CRUD: Replace locally generated citation with the server-generated one
    public boolean updateCitation(String paperId, String bibtex, String apa) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        if (bibtex != null && !bibtex.isEmpty()) cv.put(COL_BIBTEX, bibtex);
        if (apa != null && !apa.isEmpty()) cv.put(COL_APA, apa);
        if (cv.size() == 0) return false;
        int rows = db.update(TABLE_BOOKMARKS, cv, COL_ID + "=?", new String[]{paperId});
        return rows > 0;
    }

    // Query: Get personal research note
    public String getNote(String paperId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String note = "";
        try (Cursor cursor = db.query(TABLE_BOOKMARKS, new String[]{COL_NOTE}, COL_ID + "=?", new String[]{paperId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                note = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE));
            }
        }
        return note != null ? note : "";
    }

    // Query: Get stored BibTeX string (Instant Offline Copy)
    public String getBibtex(String paperId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String bib = "";
        try (Cursor cursor = db.query(TABLE_BOOKMARKS, new String[]{COL_BIBTEX}, COL_ID + "=?", new String[]{paperId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(COL_BIBTEX);
                if (idx != -1) bib = cursor.getString(idx);
            }
        }
        return bib != null ? bib : "";
    }

    // Check if bookmarked
    public boolean isBookmarked(String paperId) {
        if (paperId == null || paperId.trim().isEmpty()) return false;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKMARKS, new String[]{COL_ID}, COL_ID + "=?", new String[]{paperId}, null, null, null);
        boolean exists = (cursor != null && cursor.moveToFirst());
        if (cursor != null) cursor.close();
        return exists;
    }

    // Sync downloaded PDF status for existing bookmarks in SQLite
    public void syncDownloadedFiles(Context context) {
        try {
            List<PaperItem> bookmarks = getAllBookmarks();
            if (bookmarks == null || bookmarks.isEmpty()) return;

            for (PaperItem paper : bookmarks) {
                if (paper.getId() == null) continue;
                File pdfFile = DownloadService.getLocalPdfFile(context, paper.getId());
                if (pdfFile.exists() && pdfFile.length() > 0) {
                    if (paper.getLocalPdfPath() == null || !paper.getLocalPdfPath().equals(pdfFile.getAbsolutePath())) {
                        updateLocalPdfPath(paper.getId(), pdfFile.getAbsolutePath());
                    }
                } else {
                    if (paper.getLocalPdfPath() != null) {
                        updateLocalPdfPath(paper.getId(), null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get all bookmarked & downloaded papers (Offline Library)
    public List<PaperItem> getAllBookmarks() {
        return getBookmarksByCategory("ALL");
    }

    // Get bookmarks filtered by category (cs.AI, cs.LG, or ALL)
    public List<PaperItem> getBookmarksByCategory(String category) {
        List<PaperItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selection = null;
        String[] selectionArgs = null;
        if (category != null && !category.equalsIgnoreCase("ALL") && !category.isEmpty()) {
            selection = COL_CATEGORY + "=?";
            selectionArgs = new String[]{category};
        }

        try (Cursor cursor = db.query(TABLE_BOOKMARKS, null, selection, selectionArgs, null, null, COL_CREATED_AT + " DESC")) {
            if (cursor.moveToFirst()) {
                do {
                    String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
                    String summary = cursor.getString(cursor.getColumnIndexOrThrow(COL_SUMMARY));
                    String authorsStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHORS));
                    String published = cursor.getString(cursor.getColumnIndexOrThrow(COL_PUBLISHED));
                    String cat = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY));
                    String pdfUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_PDF_URL));
                    String localPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCAL_PDF_PATH));

                    List<String> authors = (authorsStr != null && !authorsStr.isEmpty())
                            ? Arrays.asList(authorsStr.split(", "))
                            : new ArrayList<>();

                    PaperItem item = new PaperItem(id, title, summary, authors, published, cat, pdfUrl);
                    item.setBookmarked(true);
                    item.setLocalPdfPath(localPath);

                    int noteIdx = cursor.getColumnIndex(COL_NOTE);
                    if (noteIdx != -1) item.setNote(cursor.getString(noteIdx));

                    int bibtexIdx = cursor.getColumnIndex(COL_BIBTEX);
                    if (bibtexIdx != -1) item.setBibtex(cursor.getString(bibtexIdx));

                    int apaIdx = cursor.getColumnIndex(COL_APA);
                    if (apaIdx != -1) item.setApa(cursor.getString(apaIdx));

                    list.add(item);
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    // Update local PDF path
    public void updateLocalPdfPath(String paperId, String localPath) {
        if (paperId == null || paperId.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        if (localPath != null) {
            cv.put(COL_LOCAL_PDF_PATH, localPath);
        } else {
            cv.putNull(COL_LOCAL_PDF_PATH);
        }
        db.update(TABLE_BOOKMARKS, cv, COL_ID + "=?", new String[]{paperId});
    }

    // Clear local PDF paths when cache is cleared
    public void clearAllLocalPdfPaths() {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.putNull(COL_LOCAL_PDF_PATH);
        db.update(TABLE_BOOKMARKS, cv, null, null);
    }

    // History: Save last read page
    public void saveReadProgress(String paperId, int page) {
        if (paperId == null || paperId.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, paperId);
        cv.put(COL_LAST_PAGE, page);
        cv.put(COL_READ_TIME, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_HISTORY, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // History: Get last read page
    public int getLastReadPage(String paperId) {
        if (paperId == null || paperId.trim().isEmpty()) return 1;
        SQLiteDatabase db = this.getReadableDatabase();
        int page = 1;
        try (Cursor cursor = db.query(TABLE_HISTORY, new String[]{COL_LAST_PAGE}, COL_ID + "=?", new String[]{paperId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                page = cursor.getInt(cursor.getColumnIndexOrThrow(COL_LAST_PAGE));
            }
        }
        return page;
    }
}

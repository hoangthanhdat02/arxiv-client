package com.arxiv.client.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.arxiv.client.data.model.PaperItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArxivDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "arxiv_client.db";
    private static final int DATABASE_VERSION = 1;

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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }

    // CRUD: Add or update bookmark
    public boolean saveBookmark(PaperItem paper, String note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, paper.getId());
        cv.put(COL_TITLE, paper.getTitle());
        cv.put(COL_SUMMARY, paper.getSummary());
        cv.put(COL_AUTHORS, paper.getAuthorsString());
        cv.put(COL_PUBLISHED, paper.getPublished());
        cv.put(COL_CATEGORY, paper.getPrimaryCategory());
        cv.put(COL_PDF_URL, paper.getPdfUrl());
        cv.put(COL_LOCAL_PDF_PATH, paper.getLocalPdfPath());
        cv.put(COL_NOTE, note != null ? note : "");
        cv.put(COL_CREATED_AT, System.currentTimeMillis());

        long result = db.insertWithOnConflict(TABLE_BOOKMARKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    // CRUD: Remove bookmark
    public boolean removeBookmark(String paperId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_BOOKMARKS, COL_ID + "=?", new String[]{paperId});
        return rows > 0;
    }

    // Check if bookmarked
    public boolean isBookmarked(String paperId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKMARKS, new String[]{COL_ID}, COL_ID + "=?", new String[]{paperId}, null, null, null);
        boolean exists = (cursor != null && cursor.moveToFirst());
        if (cursor != null) cursor.close();
        return exists;
    }

    // Get all bookmarked papers (Offline Mode)
    public List<PaperItem> getAllBookmarks() {
        List<PaperItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKMARKS, null, null, null, null, null, COL_CREATED_AT + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
                String summary = cursor.getString(cursor.getColumnIndexOrThrow(COL_SUMMARY));
                String authorsStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHORS));
                String published = cursor.getString(cursor.getColumnIndexOrThrow(COL_PUBLISHED));
                String category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY));
                String pdfUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_PDF_URL));
                String localPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCAL_PDF_PATH));

                List<String> authors = (authorsStr != null && !authorsStr.isEmpty())
                        ? Arrays.asList(authorsStr.split(", "))
                        : new ArrayList<>();

                PaperItem item = new PaperItem(id, title, summary, authors, published, category, pdfUrl);
                item.setBookmarked(true);
                item.setLocalPdfPath(localPath);
                list.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return list;
    }

    // Update local PDF path
    public void updateLocalPdfPath(String paperId, String localPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOCAL_PDF_PATH, localPath);
        db.update(TABLE_BOOKMARKS, cv, COL_ID + "=?", new String[]{paperId});
    }

    // History: Save last read page
    public void saveReadProgress(String paperId, int page) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, paperId);
        cv.put(COL_LAST_PAGE, page);
        cv.put(COL_READ_TIME, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_HISTORY, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // History: Get last read page
    public int getLastReadPage(String paperId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_HISTORY, new String[]{COL_LAST_PAGE}, COL_ID + "=?", new String[]{paperId}, null, null, null);
        int page = 1;
        if (cursor != null && cursor.moveToFirst()) {
            page = cursor.getInt(cursor.getColumnIndexOrThrow(COL_LAST_PAGE));
            cursor.close();
        }
        return page;
    }
}

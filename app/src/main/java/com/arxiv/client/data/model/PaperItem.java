package com.arxiv.client.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class PaperItem implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("summary")
    private String summary;

    @SerializedName("authors")
    private List<String> authors;

    @SerializedName("published")
    private String published;

    @SerializedName("updated")
    private String updated;

    @SerializedName("categories")
    private List<String> categories;

    @SerializedName("primaryCategory")
    private String primaryCategory;

    @SerializedName("pdfUrl")
    private String pdfUrl;

    @SerializedName("arxivUrl")
    private String arxivUrl;

    private boolean isBookmarked = false;
    private String localPdfPath = null;

    public PaperItem() {}

    public PaperItem(String id, String title, String summary, List<String> authors, String published, String primaryCategory, String pdfUrl) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.authors = authors;
        this.published = published;
        this.primaryCategory = primaryCategory;
        this.pdfUrl = pdfUrl;
    }

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary != null ? summary : ""; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> authors) { this.authors = authors; }

    public String getAuthorsString() {
        if (authors == null || authors.isEmpty()) return "Unknown author";
        return String.join(", ", authors);
    }

    public String getPublished() { return published != null ? published : ""; }
    public void setPublished(String published) { this.published = published; }

    public String getFormattedDate() {
        if (published == null || published.isEmpty()) return "";
        try {
            return published.split("T")[0];
        } catch (Exception e) {
            return published;
        }
    }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }

    public String getPrimaryCategory() {
        if ("cs.LG".equalsIgnoreCase(primaryCategory)) {
            return "cs.LG";
        }
        if (categories != null) {
            for (String cat : categories) {
                if ("cs.LG".equalsIgnoreCase(cat)) return "cs.LG";
            }
        }
        return "cs.AI";
    }

    public String getPdfUrl() { return pdfUrl != null ? pdfUrl : ""; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public String getArxivUrl() { return arxivUrl != null ? arxivUrl : ""; }
    public void setArxivUrl(String arxivUrl) { this.arxivUrl = arxivUrl; }

    public boolean isBookmarked() { return isBookmarked; }
    public void setBookmarked(boolean bookmarked) { isBookmarked = bookmarked; }

    public String getLocalPdfPath() { return localPdfPath; }
    public void setLocalPdfPath(String localPdfPath) { this.localPdfPath = localPdfPath; }
}

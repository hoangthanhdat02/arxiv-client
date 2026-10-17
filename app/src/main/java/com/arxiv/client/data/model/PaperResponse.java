package com.arxiv.client.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PaperResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("category")
    private String category;

    @SerializedName("totalResults")
    private int totalResults;

    @SerializedName("page")
    private int page;

    @SerializedName("limit")
    private int limit;

    @SerializedName("data")
    private List<PaperItem> data;

    public String getStatus() { return status; }
    public String getCategory() { return category; }
    public int getTotalResults() { return totalResults; }
    public int getPage() { return page; }
    public int getLimit() { return limit; }
    public List<PaperItem> getData() { return data; }
}

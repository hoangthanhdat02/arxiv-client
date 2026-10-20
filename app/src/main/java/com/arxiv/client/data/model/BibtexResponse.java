package com.arxiv.client.data.model;

import com.google.gson.annotations.SerializedName;

public class BibtexResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("paperId")
    private String paperId;

    @SerializedName("title")
    private String title;

    @SerializedName("year")
    private int year;

    @SerializedName("doi")
    private String doi;

    @SerializedName("bibtex")
    private String bibtex;

    @SerializedName("apa")
    private String apa;

    public String getStatus() { return status; }
    public String getPaperId() { return paperId; }
    public String getTitle() { return title; }
    public int getYear() { return year; }
    public String getDoi() { return doi; }
    public String getBibtex() { return bibtex; }
    public String getApa() { return apa; }
}

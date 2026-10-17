package com.arxiv.client.data.api;

import com.arxiv.client.data.model.BibtexResponse;
import com.arxiv.client.data.model.PaperResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @GET("papers")
    Call<PaperResponse> getPapers(
            @Query("category") String category,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("search")
    Call<PaperResponse> searchPapers(
            @Query("q") String query,
            @Query("category") String category,
            @Query("sortBy") String sortBy,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("bibtex/{paperId}")
    Call<BibtexResponse> getBibtex(
            @Path("paperId") String paperId
    );
}

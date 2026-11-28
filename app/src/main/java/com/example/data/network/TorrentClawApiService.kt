package com.example.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit API contract for TorrentClaw public v1 endpoints.
 */
interface TorrentClawApiService {

  @GET("popular")
  suspend fun getPopular(
    @Query("type") type: String? = null,
    @Query("page") page: Int? = null,
    @Query("limit") limit: Int? = null,
  ): Response<ResponseBody>

  @GET("recent")
  suspend fun getRecent(
    @Query("type") type: String? = null,
    @Query("page") page: Int? = null,
    @Query("limit") limit: Int? = null,
  ): Response<ResponseBody>

  @GET("search")
  suspend fun search(
    @QueryMap params: Map<String, String>,
  ): Response<ResponseBody>

  @GET("autocomplete")
  suspend fun autocomplete(
    @Query("q") query: String,
    @Query("limit") limit: Int = 8,
  ): Response<ResponseBody>

  @GET("genres")
  suspend fun getGenres(): Response<ResponseBody>

  @GET("collections")
  suspend fun getCollections(
    @Query("page") page: Int? = null,
    @Query("limit") limit: Int? = null,
  ): Response<ResponseBody>

  @GET("collections/{id}")
  suspend fun getCollectionDetail(
    @Path("id") collectionId: String,
  ): Response<ResponseBody>

  @GET("stats")
  suspend fun getStats(): Response<ResponseBody>

  @GET("content/{id}")
  suspend fun getContentDetail(
    @Path("id") id: String,
    @QueryMap params: Map<String, String>,
  ): Response<ResponseBody>

  @GET("content/{id}/torrents")
  suspend fun getContentTorrents(
    @Path("id") id: String,
    @QueryMap params: Map<String, String>,
  ): Response<ResponseBody>

  @GET("content/{id}/credits")
  suspend fun getContentCredits(
    @Path("id") id: String,
  ): Response<ResponseBody>

  @GET("content/{id}/watch-providers")
  suspend fun getContentWatchProviders(
    @Path("id") id: String,
    @Query("country") country: String = "US",
  ): Response<ResponseBody>

  @GET("watch-providers")
  suspend fun getWatchProviders(
    @Query("tmdb_id") tmdbId: String,
    @Query("country") country: String = "US",
  ): Response<ResponseBody>

  @Streaming
  @GET
  suspend fun downloadFile(@Url url: String): Response<ResponseBody>

  @GET
  suspend fun fetchHtmlPage(
    @Url url: String,
    @retrofit2.http.Header("Accept") accept: String = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    @retrofit2.http.Header("User-Agent") userAgent: String =
      "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
  ): Response<ResponseBody>
}

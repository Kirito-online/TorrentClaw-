package com.example.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TorrentClawApi {

  @GET("search")
  suspend fun search(
    @Query("q") query: String? = null,
    @Query("type") type: String? = null,
    @Query("genre") genre: String? = null,
    @Query("year_min") yearMin: Int? = null,
    @Query("year_max") yearMax: Int? = null,
    @Query("min_rating") minRating: Float? = null,
    @Query("quality") quality: String? = null,
    @Query("codec") codec: String? = null,
    @Query("audio") audio: String? = null,
    @Query("hdr") hdr: String? = null,
    @Query("lang") lang: String? = null,
    @Query("season") season: Int? = null,
    @Query("episode") episode: Int? = null,
    @Query("min_seeders") minSeeders: Int? = null,
    @Query("min_score") minScore: Int? = null,
    @Query("sort") sort: String? = null,
    @Query("tmdb_id") tmdbId: Int? = null,
    @Query("imdb_id") imdbId: String? = null,
    @Query("page") page: Int = 1,
    @Query("limit") limit: Int = 25,
  ): Response<ResponseBody>

  @GET("autocomplete")
  suspend fun autocomplete(
    @Query("q") query: String,
    @Query("limit") limit: Int = 8,
  ): Response<ResponseBody>

  @GET("popular")
  suspend fun getPopular(
    @Query("type") type: String? = null,
    @Query("page") page: Int = 1,
    @Query("limit") limit: Int = 20,
  ): Response<ResponseBody>

  @GET("recent")
  suspend fun getRecent(
    @Query("type") type: String? = null,
    @Query("page") page: Int = 1,
    @Query("limit") limit: Int = 20,
  ): Response<ResponseBody>

  @GET("trending")
  suspend fun getTrending(
    @Query("type") type: String? = null,
    @Query("page") page: Int = 1,
    @Query("limit") limit: Int = 20,
  ): Response<ResponseBody>

  @GET("genres") suspend fun getGenres(): Response<ResponseBody>

  @GET("collections")
  suspend fun getCollections(
    @Query("page") page: Int = 1,
    @Query("limit") limit: Int = 20,
  ): Response<ResponseBody>

  @GET("content/{id}") suspend fun getContentDetail(@Path("id") id: String): Response<ResponseBody>

  @GET("content/{id}/credits")
  suspend fun getContentCredits(@Path("id") id: String): Response<ResponseBody>

  @GET("watch-providers")
  suspend fun getWatchProviders(
    @Query("tmdb_id") tmdbId: Int? = null,
    @Query("imdb_id") imdbId: String? = null,
    @Query("country") country: String = "US",
  ): Response<ResponseBody>

  @GET("stats") suspend fun getStats(): Response<ResponseBody>

  @GET("torrent/{info_hash}")
  suspend fun downloadTorrentFile(@Path("info_hash") infoHash: String): Response<ResponseBody>

  @POST("scan") suspend fun submitTrueSpecScan(@Body body: RequestBody): Response<ResponseBody>

  @GET("scan/{info_hash}")
  suspend fun getTrueSpecScanStatus(@Path("info_hash") infoHash: String): Response<ResponseBody>

  @POST("track") suspend fun trackInteraction(@Body body: RequestBody): Response<ResponseBody>

  @GET("search-analytics")
  suspend fun getSearchAnalytics(@Query("period") period: String = "7d"): Response<ResponseBody>
}

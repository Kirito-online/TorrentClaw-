package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_media")
data class SavedMediaEntity(
  @PrimaryKey val id: String,
  val tmdbId: Int?,
  val imdbId: String?,
  val title: String,
  val year: Int?,
  val mediaType: String, // MOVIE | SHOW
  val rating: Double?,
  val posterUrl: String?,
  val backdropUrl: String?,
  val overview: String,
  val genresCsv: String,
  val category: String, // FAVORITE | WATCHLIST | COLLECTION
  val collectionName: String?,
  val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "saved_torrents")
data class SavedTorrentEntity(
  @PrimaryKey val infoHash: String,
  val releaseName: String,
  val mediaTitle: String?,
  val posterUrl: String?,
  val resolution: String,
  val sourceType: String?,
  val videoCodec: String?,
  val audioCodec: String?,
  val hdrFormat: String?,
  val sizeBytes: Long,
  val seeders: Int,
  val leechers: Int,
  val trackerSource: String,
  val qualityScore: Int,
  val trueSpecStatus: String,
  val magnetUrl: String,
  val savedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
  @PrimaryKey val query: String,
  val mediaTypeFilter: String = "ALL",
  val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val infoHash: String,
  val releaseName: String,
  val mediaTitle: String?,
  val posterUrl: String?,
  val resolution: String,
  val qualityScore: Int,
  val trackerSource: String,
  val actionType: String, // MAGNET_OPENED | MAGNET_COPIED | TORRENT_DOWNLOADED | DEBRID_SENT
  val magnetUrl: String,
  val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "content_alerts")
data class ContentAlertEntity(
  @PrimaryKey val mediaId: String,
  val tmdbId: Int?,
  val imdbId: String?,
  val title: String,
  val mediaType: String,
  val posterUrl: String?,
  val enabled: Boolean = true,
  val preferredResolution: String = "1080p",
  val minQualityScore: Int = 70,
  val lastKnownReleaseCount: Int = 0,
  val newMatchesCount: Int = 0,
  val lastCheckedAt: Long = System.currentTimeMillis(),
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cached_discovery")
data class CachedDiscoveryEntity(
  @PrimaryKey val bucketKey: String, // popular_movie, popular_show, recent, trending, collections, genres
  val payloadJson: String,
  val updatedAt: Long = System.currentTimeMillis(),
)

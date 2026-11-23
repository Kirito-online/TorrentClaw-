package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMediaDao {
  @Query("SELECT * FROM saved_media ORDER BY addedAt DESC")
  fun observeAll(): Flow<List<SavedMediaEntity>>

  @Query("SELECT EXISTS(SELECT 1 FROM saved_media WHERE id = :id AND category = :category)")
  fun observeIsSaved(id: String, category: String): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: SavedMediaEntity)

  @Query("DELETE FROM saved_media WHERE id = :id AND category = :category")
  suspend fun delete(id: String, category: String)

  @Query("DELETE FROM saved_media")
  suspend fun clearAll()
}

@Dao
interface SavedTorrentDao {
  @Query("SELECT * FROM saved_torrents ORDER BY savedAt DESC")
  fun observeAll(): Flow<List<SavedTorrentEntity>>

  @Query("SELECT EXISTS(SELECT 1 FROM saved_torrents WHERE infoHash = :infoHash)")
  fun observeIsSaved(infoHash: String): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: SavedTorrentEntity)

  @Query("DELETE FROM saved_torrents WHERE infoHash = :infoHash")
  suspend fun delete(infoHash: String)

  @Query("DELETE FROM saved_torrents")
  suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
  @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 30")
  fun observeRecent(): Flow<List<SearchHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entry: SearchHistoryEntity)

  @Query("DELETE FROM search_history WHERE `query` = :query")
  suspend fun deleteByQuery(query: String)

  @Query("DELETE FROM search_history")
  suspend fun clearAll()
}

@Dao
interface DownloadHistoryDao {
  @Query("SELECT * FROM download_history ORDER BY timestamp DESC LIMIT 100")
  fun observeHistory(): Flow<List<DownloadHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: DownloadHistoryEntity)

  @Query("DELETE FROM download_history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM download_history")
  suspend fun clearAll()
}

@Dao
interface ContentAlertDao {
  @Query("SELECT * FROM content_alerts ORDER BY createdAt DESC")
  fun observeAlerts(): Flow<List<ContentAlertEntity>>

  @Query("SELECT * FROM content_alerts")
  suspend fun getAlertsSnapshot(): List<ContentAlertEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM content_alerts WHERE mediaId = :mediaId)")
  fun observeIsFollowed(mediaId: String): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(alert: ContentAlertEntity)

  @Query("UPDATE content_alerts SET enabled = :enabled WHERE mediaId = :mediaId")
  suspend fun setEnabled(mediaId: String, enabled: Boolean)

  @Query("DELETE FROM content_alerts WHERE mediaId = :mediaId")
  suspend fun delete(mediaId: String)
}

@Dao
interface CachedDiscoveryDao {
  @Query("SELECT * FROM cached_discovery WHERE bucketKey = :key LIMIT 1")
  suspend fun getBucket(key: String): CachedDiscoveryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CachedDiscoveryEntity)

  @Query("DELETE FROM cached_discovery")
  suspend fun clearAll()
}

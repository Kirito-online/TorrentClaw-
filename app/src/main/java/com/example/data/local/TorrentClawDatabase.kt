package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities =
    [
      SavedMediaEntity::class,
      SavedTorrentEntity::class,
      SearchHistoryEntity::class,
      DownloadHistoryEntity::class,
      ContentAlertEntity::class,
      CachedDiscoveryEntity::class,
    ],
  version = 1,
  exportSchema = false,
)
abstract class TorrentClawDatabase : RoomDatabase() {
  abstract fun savedMediaDao(): SavedMediaDao

  abstract fun savedTorrentDao(): SavedTorrentDao

  abstract fun searchHistoryDao(): SearchHistoryDao

  abstract fun downloadHistoryDao(): DownloadHistoryDao

  abstract fun contentAlertDao(): ContentAlertDao

  abstract fun cachedDiscoveryDao(): CachedDiscoveryDao

  companion object {
    @Volatile private var instance: TorrentClawDatabase? = null

    fun getInstance(context: Context): TorrentClawDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                TorrentClawDatabase::class.java,
                "torrentclaw.db",
              )
              .fallbackToDestructiveMigration(true)
              .build()
              .also { instance = it }
        }
    }
  }
}

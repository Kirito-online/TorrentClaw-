package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.DownloadHistoryEntity
import com.example.data.local.SavedMediaEntity
import com.example.data.local.SavedTorrentEntity
import com.example.data.model.MediaType
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecStatus
import com.example.data.model.formatFileSize
import com.example.ui.components.QualityScoreBadge
import com.example.ui.components.TorrentClawEmptyState
import com.example.ui.theme.LocalTorrentClawColors
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(
  savedMedia: List<SavedMediaEntity>,
  savedTorrents: List<SavedTorrentEntity>,
  downloadHistory: List<DownloadHistoryEntity>,
  onOpenMedia: (String) -> Unit,
  onRemoveMedia: (SavedMediaEntity) -> Unit,
  onInspectSavedTorrent: (SavedTorrentEntity) -> Unit,
  onRemoveSavedTorrent: (SavedTorrentEntity) -> Unit,
  onInspectHistoryItem: (DownloadHistoryEntity) -> Unit,
  onOpenHistoryMagnet: (DownloadHistoryEntity) -> Unit,
  onDeleteHistoryItem: (Long) -> Unit,
  onClearHistory: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = listOf("Movies", "TV Shows", "Torrents", "Watchlist", "History")

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Text(
      text = "Library & Activity",
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )

    ScrollableTabRow(
      selectedTabIndex = selectedTab,
      edgePadding = 16.dp,
      containerColor = MaterialTheme.colorScheme.background,
    ) {
      tabs.forEachIndexed { index, title ->
        Tab(
          selected = selectedTab == index,
          onClick = { selectedTab = index },
          text = { Text(title) },
          modifier = Modifier.testTag("library_tab_$index"),
        )
      }
    }

    when (selectedTab) {
      0 -> {
        val movies = savedMedia.filter { it.mediaType == MediaType.MOVIE.name && it.category == "FAVORITE" }
        MediaGridSection(
          items = movies,
          emptyTitle = "No Favorite Movies",
          emptyMessage = "Tap the heart icon on any movie to keep it in your library.",
          onOpenMedia = onOpenMedia,
          onRemove = onRemoveMedia,
        )
      }
      1 -> {
        val shows = savedMedia.filter { it.mediaType == MediaType.SHOW.name && it.category == "FAVORITE" }
        MediaGridSection(
          items = shows,
          emptyTitle = "No Favorite TV Shows",
          emptyMessage = "Save series here for quick season and episode torrent lookups.",
          onOpenMedia = onOpenMedia,
          onRemove = onRemoveMedia,
        )
      }
      2 -> {
        if (savedTorrents.isEmpty()) {
          TorrentClawEmptyState(
            title = "No Saved Torrents",
            message = "Bookmark individual releases from the Torrent Inspector to revisit or send them later.",
          )
        } else {
          LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(savedTorrents, key = { it.infoHash }) { st ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { onInspectSavedTorrent(st) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                QualityScoreBadge(score = st.qualityScore)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(st.releaseName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(
                    "${st.resolution} • ${formatFileSize(st.sizeBytes)} • ▲ ${st.seeders} • ${st.trackerSource}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { onRemoveSavedTorrent(st) }) {
                  Icon(Icons.Rounded.BookmarkRemove, contentDescription = "Remove saved torrent")
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
          }
        }
      }
      3 -> {
        val watchlist = savedMedia.filter { it.category == "WATCHLIST" }
        MediaGridSection(
          items = watchlist,
          emptyTitle = "Watchlist Empty",
          emptyMessage = "Bookmark movies and shows you plan to watch later.",
          onOpenMedia = onOpenMedia,
          onRemove = onRemoveMedia,
        )
      }
      4 -> {
        HistorySection(
          items = downloadHistory,
          onInspect = onInspectHistoryItem,
          onOpenMagnet = onOpenHistoryMagnet,
          onDelete = onDeleteHistoryItem,
          onClear = onClearHistory,
        )
      }
    }
  }
}

@Composable
private fun MediaGridSection(
  items: List<SavedMediaEntity>,
  emptyTitle: String,
  emptyMessage: String,
  onOpenMedia: (String) -> Unit,
  onRemove: (SavedMediaEntity) -> Unit,
) {
  if (items.isEmpty()) {
    TorrentClawEmptyState(title = emptyTitle, message = emptyMessage)
    return
  }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 112.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    items(items, key = { "${it.id}_${it.category}" }) { item ->
      Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpenMedia(item.id) },
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(2f / 3f)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
          if (!item.posterUrl.isNullOrBlank()) {
            AsyncImage(
              model = item.posterUrl,
              contentDescription = item.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Icon(
              if (item.mediaType == MediaType.SHOW.name) Icons.Rounded.Tv else Icons.Rounded.Movie,
              contentDescription = null,
              modifier = Modifier.align(Alignment.Center).size(30.dp),
            )
          }
          IconButton(
            onClick = { onRemove(item) },
            modifier = Modifier.align(Alignment.TopEnd).size(34.dp),
          ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurface)
          }
        }
        Spacer(Modifier.height(5.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.year?.let {
          Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun HistorySection(
  items: List<DownloadHistoryEntity>,
  onInspect: (DownloadHistoryEntity) -> Unit,
  onOpenMagnet: (DownloadHistoryEntity) -> Unit,
  onDelete: (Long) -> Unit,
  onClear: () -> Unit,
) {
  if (items.isEmpty()) {
    TorrentClawEmptyState(
      title = "No Activity History",
      message = "Magnets opened, links copied, .torrent files downloaded, and Debrid queues will appear here.",
    )
    return
  }

  val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "TorrentClaw activity log (transfer progress lives in your external client)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onClear) {
        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Clear")
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
      items(items, key = { it.id }) { entry ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable { onInspect(entry) }
              .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!entry.posterUrl.isNullOrBlank()) {
            AsyncImage(
              model = entry.posterUrl,
              contentDescription = entry.mediaTitle,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(44.dp, 62.dp).clip(RoundedCornerShape(7.dp)),
            )
          } else {
            QualityScoreBadge(score = entry.qualityScore)
          }
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(entry.releaseName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
              "${entry.resolution} • Score ${entry.qualityScore} • ${entry.trackerSource}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              "${entry.actionType.replace("_", " ")} • ${formatter.format(Date(entry.timestamp))}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(onClick = { onOpenMagnet(entry) }) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = "Open magnet")
          }
          IconButton(onClick = { onDelete(entry.id) }) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete entry")
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
      }
    }
  }
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.CastMember
import com.example.data.model.MediaType
import com.example.data.model.TorrentListFilterState
import com.example.data.model.TorrentRelease
import com.example.data.model.formatDisplayDate
import com.example.data.model.formatFileSize
import com.example.data.model.formatSeasonEpisodeLabel
import com.example.data.model.formatVoteCount
import com.example.ui.components.TorrentFilterBar
import com.example.ui.components.TorrentFilterBottomSheet
import com.example.ui.components.TorrentReleaseRow
import com.example.ui.components.TorrentTagBadge
import com.example.ui.theme.LocalTorrentClawColors
import com.example.ui.viewmodel.MediaDetailUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaDetailScreen(
  uiState: MediaDetailUiState,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
  onSelectSeason: (Int?) -> Unit,
  onSelectEpisode: (Int?) -> Unit,
  onUpdateFilter: (TorrentListFilterState) -> Unit,
  onResetFilters: () -> Unit,
  onTorrentClick: (TorrentRelease) -> Unit,
  onMagnetClick: (TorrentRelease) -> Unit = onTorrentClick,
  onLoadMoreTorrents: () -> Unit = {},
  isFavorite: Boolean = false,
  onToggleFavorite: () -> Unit = {},
) {
  var showFilterSheet by remember { mutableStateOf(false) }
  val media = uiState.media ?: return
  val semantic = LocalTorrentClawColors.current
  val isTvShow = media.mediaType == MediaType.SHOW

  PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = onRefresh,
    modifier = Modifier.fillMaxSize(),
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Hero backdrop + back arrow + poster + metadata
      item {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
          val bg = media.backdropUrl ?: media.posterUrl
          if (!bg.isNullOrBlank()) {
            AsyncImage(
              model = ImageRequest.Builder(LocalContext.current).data(bg).crossfade(true).build(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxWidth().height(230.dp),
            )
          }
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(230.dp)
                .background(
                  Brush.verticalGradient(
                    listOf(
                      Color(0xFF0B111E).copy(alpha = 0.45f),
                      MaterialTheme.colorScheme.background,
                    )
                  )
                )
          )

          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
              ) {
                IconButton(
                  onClick = onBack,
                  modifier = Modifier.size(48.dp).testTag("detail_back_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                  )
                }
              }

              Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                border =
                  if (isFavorite) BorderStroke(1.dp, semantic.favoriteRose.copy(alpha = 0.7f))
                  else null,
              ) {
                IconButton(
                  onClick = onToggleFavorite,
                  modifier = Modifier.size(48.dp).testTag("detail_favorite_button"),
                ) {
                  Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription =
                      stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                    tint = if (isFavorite) semantic.favoriteRose else Color.White,
                  )
                }
              }
            }

            Spacer(Modifier.height(14.dp))

            Row(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.Bottom,
            ) {
              Box(
                modifier =
                  Modifier.width(104.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(34.dp))
                if (!media.posterUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(media.posterUrl).crossfade(true).build(),
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                }
              }

              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = media.title,
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                media.originalTitle?.takeIf { !it.equals(media.title, ignoreCase = true) }?.let { orig ->
                  Text(
                    text = orig,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  val displayDate = formatDisplayDate(media.releaseDate)
                  if (displayDate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                      Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                      )
                      Text(displayDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  } else {
                    media.year?.let {
                      Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                  Text(media.mediaType.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                  media.rating?.takeIf { it > 0 }?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                      Icon(Icons.Rounded.Star, contentDescription = null, tint = semantic.hdrGold, modifier = Modifier.size(16.dp))
                      val votes = formatVoteCount(media.voteCount)?.let { " ($it)" }.orEmpty()
                      Text("${"%.1f".format(r)}$votes", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                  }
                  media.imdbRating?.takeIf { it > 0 && it != media.rating }?.let { ir ->
                    Text("IMDb ${"%.1f".format(ir)}", style = MaterialTheme.typography.bodySmall, color = semantic.hdrGold, fontWeight = FontWeight.Medium)
                  }
                  media.runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
                    Text("${mins}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  media.status?.takeIf { it.isNotBlank() }?.let { st ->
                    TorrentTagBadge(st, semantic.verified)
                  }
                  media.certification?.takeIf { it.isNotBlank() }?.let { cert ->
                    TorrentTagBadge(cert, MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  media.originalLanguage?.takeIf { it.isNotBlank() }?.let { lang ->
                    TorrentTagBadge(lang.uppercase(), MaterialTheme.colorScheme.secondary)
                  }
                  if (isTvShow && (media.numberOfSeasons != null || media.numberOfEpisodes != null)) {
                    val tvSummary =
                      listOfNotNull(
                        media.numberOfSeasons?.let { "$it Seasons" },
                        media.numberOfEpisodes?.let { "$it Episodes" },
                      ).joinToString(" • ")
                    if (tvSummary.isNotBlank()) {
                      TorrentTagBadge(tvSummary, MaterialTheme.colorScheme.primary)
                    }
                  }
                  media.genres.take(4).forEach { g ->
                    TorrentTagBadge(g, MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  media.imdbId?.takeIf { it.isNotBlank() }?.let { imdb ->
                    TorrentTagBadge(imdb, MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  media.collectionName?.takeIf { it.isNotBlank() }?.let { col ->
                    TorrentTagBadge(col, MaterialTheme.colorScheme.tertiary)
                  }
                }
              }
            }
          }
        }
      }

      // Tagline & Overview
      if (!media.tagline.isNullOrBlank() || media.overview.isNotBlank()) {
        item {
          Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            media.tagline?.takeIf { it.isNotBlank() }?.let { tag ->
              Text(
                text = "“$tag”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            if (media.overview.isNotBlank()) {
              Text(
                text = media.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Smart Recommended Match
      uiState.recommendation?.let { rec ->
        item {
          Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                  Text("Recommended Match", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (rec.reasons.isNotEmpty()) {
                  Text(
                    text = rec.reasons.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.verified,
                  )
                }
              }
              Text(
                text = rec.release.releaseName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                val recMeta =
                  listOfNotNull(
                    formatSeasonEpisodeLabel(rec.release.seasonNumber, rec.release.episodeNumber),
                    rec.release.resolution.takeIf { it.isNotBlank() },
                    rec.release.videoCodec.takeIf { it.isNotBlank() },
                    rec.release.hdrFormat?.takeIf { it.isNotBlank() },
                    formatFileSize(rec.release.sizeBytes),
                    "▲ ${rec.release.seeders}",
                    formatDisplayDate(rec.release.releaseDate),
                  ).joinToString(" • ")
                Text(
                  text = recMeta,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Button(
                  onClick = { onMagnetClick(rec.release) },
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                  modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag("recommended_magnet_button"),
                ) {
                  Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Magnet")
                }
              }
            }
          }
        }
      }

      // Cast & Crew strip
      if (uiState.cast.isNotEmpty()) {
        item {
          CastSection(cast = uiState.cast)
        }
      }

      // TV Season & Episode selector
      if (isTvShow && (uiState.availableSeasons.isNotEmpty() || uiState.availableEpisodes.isNotEmpty())) {
        item {
          Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (uiState.availableSeasons.isNotEmpty()) {
              Text("Season", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilterChip(
                  selected = uiState.selectedSeason == null,
                  onClick = { onSelectSeason(null) },
                  label = { Text("All Seasons") },
                )
                uiState.availableSeasons.forEach { season ->
                  FilterChip(
                    selected = uiState.selectedSeason == season,
                    onClick = { onSelectSeason(season) },
                    label = { Text("Season $season") },
                  )
                }
              }
            }
            if (uiState.availableEpisodes.isNotEmpty()) {
              Text("Episode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilterChip(
                  selected = uiState.selectedEpisode == null,
                  onClick = { onSelectEpisode(null) },
                  label = { Text("All Episodes") },
                )
                uiState.availableEpisodes.forEach { ep ->
                  FilterChip(
                    selected = uiState.selectedEpisode == ep,
                    onClick = { onSelectEpisode(ep) },
                    label = { Text("E${ep.toString().padStart(2, '0')}") },
                  )
                }
              }
            }
          }
        }
      }

      // Available Torrents header + filter bar
      item {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Available Torrents (${uiState.filteredTorrents.size})",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = uiState.filter.sortBy.label,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          if (uiState.allTorrents.isNotEmpty() || uiState.filter.keyword.isNotEmpty()) {
            OutlinedTextField(
              value = uiState.filter.keyword,
              onValueChange = { newKeyword -> onUpdateFilter(uiState.filter.copy(keyword = newKeyword)) },
              placeholder = {
                Text(
                  text = "Search torrents (codec, group, source…)",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Rounded.Search,
                  contentDescription = "Search inside torrents",
                )
              },
              trailingIcon = {
                if (uiState.filter.keyword.isNotEmpty()) {
                  IconButton(
                    onClick = { onUpdateFilter(uiState.filter.copy(keyword = "")) },
                    modifier = Modifier.size(48.dp).testTag("detail_torrent_search_clear"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Clear,
                      contentDescription = "Clear torrent search",
                    )
                  }
                }
              },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("detail_torrent_search_input"),
            )
          }

          TorrentFilterBar(
            filter = uiState.filter,
            facets = uiState.facets,
            totalCount = uiState.allTorrents.size,
            onUpdateFilter = onUpdateFilter,
            onOpenSheet = { showFilterSheet = true },
          )
        }
      }

      if (uiState.isLoading && uiState.filteredTorrents.isEmpty()) {
        item {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
      } else if (uiState.filteredTorrents.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              when {
                uiState.error != null -> {
                  Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                  )
                  OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                  ) {
                    Text("Retry")
                  }
                }
                uiState.allTorrents.isEmpty() -> {
                  Text(
                    text = "No torrents are currently indexed for this title yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                  ) {
                    Text("Refresh")
                  }
                }
                else -> {
                  Text(
                    text = "No torrents match the selected filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  OutlinedButton(
                    onClick = onResetFilters,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                  ) {
                    Text("Clear Filters")
                  }
                }
              }
            }
          }
        }
      } else {
        itemsIndexed(
          uiState.filteredTorrents,
          key = { idx, it -> "${idx}_${it.infoHash}_${it.releaseName.hashCode()}" },
        ) { _, release ->
          Box(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(horizontal = 16.dp, vertical = 5.dp)) {
            TorrentReleaseRow(
              release = release,
              onClick = { onTorrentClick(release) },
              onMagnetClick = { onMagnetClick(release) },
            )
          }
        }
      }

      if (uiState.canLoadMoreTorrents || uiState.isLoadingMoreTorrents || uiState.loadMoreMessage != null) {
        item {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .widthIn(max = 960.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text =
                if (uiState.canLoadMoreTorrents) {
                  "Loaded ${uiState.allTorrents.size} releases • More public releases available"
                } else {
                  "Loaded ${uiState.allTorrents.size} releases"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.loadMoreMessage?.takeIf { it.isNotBlank() }?.let { msg ->
              Text(
                text = msg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
              )
            }
            if (uiState.canLoadMoreTorrents || uiState.isLoadingMoreTorrents) {
              OutlinedButton(
                onClick = onLoadMoreTorrents,
                enabled = !uiState.isLoadingMoreTorrents,
                shape = RoundedCornerShape(12.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("detail_load_more_torrents_button"),
              ) {
                if (uiState.isLoadingMoreTorrents) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                  )
                  Spacer(Modifier.width(10.dp))
                  Text("Loading More Releases…")
                } else {
                  Text("Load More Releases")
                }
              }
            }
          }
        }
      }
    }
  }

  if (showFilterSheet) {
    TorrentFilterBottomSheet(
      filter = uiState.filter,
      facets = uiState.facets,
      isTvShow = isTvShow,
      onApply = onUpdateFilter,
      onReset = onResetFilters,
      onDismiss = { showFilterSheet = false },
    )
  }
}

@Composable
private fun CastSection(cast: List<CastMember>) {
  Column(
    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "Cast & Crew",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(cast, key = { "${it.id}_${it.name}" }) { member ->
        Card(
          modifier = Modifier.width(104.dp),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier =
                Modifier.size(56.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
              contentAlignment = Alignment.Center,
            ) {
              if (!member.profileUrl.isNullOrBlank()) {
                AsyncImage(
                  model = member.profileUrl,
                  contentDescription = member.name,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              } else {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Text(
              text = member.name,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            member.role?.takeIf { it.isNotBlank() }?.let { role ->
              Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

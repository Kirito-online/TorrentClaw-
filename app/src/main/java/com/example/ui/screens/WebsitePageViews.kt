package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.web.NavigationGuard
import com.example.data.web.ParsedWebsitePage
import com.example.data.web.WebBlock
import com.example.data.web.WebFilterChip
import com.example.data.web.WebFormModel
import com.example.data.web.WebMediaCard
import com.example.data.web.WebNavLink
import com.example.data.web.WebPageKind
import com.example.data.web.WebPagination
import com.example.data.web.WebTorrentItem
import com.example.ui.components.WebsiteSiteHeader
import com.example.ui.theme.LocalTorrentClawColors

@Composable
fun WebsitePageHost(
  page: ParsedWebsitePage?,
  isInitialLoading: Boolean,
  errorMessage: String?,
  hasSessionCookies: Boolean,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onSubmitSearch: () -> Unit,
  onNavigate: (String) -> Unit,
  onSubmitForm: (WebFormModel, Map<String, String>) -> Unit,
  onTorrentClick: (WebTorrentItem) -> Unit,
  onRetry: () -> Unit,
  onGoBack: () -> Unit,
) {
  if (isInitialLoading && page == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
          text = "Loading torrentclaw.com…",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    return
  }

  if (errorMessage != null && page == null) {
    WebsiteErrorView(
      message = errorMessage,
      onRetry = onRetry,
      onGoBack = onGoBack,
      onHome = { onNavigate(NavigationGuard.HOME_URL) },
    )
    return
  }

  val activePage = page ?: return

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Box(modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp)) {
        WebsiteSiteHeader(
          page = activePage,
          hasSessionCookies = hasSessionCookies,
          searchQuery = searchQuery,
          onSearchQueryChange = onSearchQueryChange,
          onSubmitSearch = onSubmitSearch,
          onNavigate = onNavigate,
        )
      }
    }

    if (errorMessage != null) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
          }
        }
      }
    }

    when (val kind = activePage.kind) {
      is WebPageKind.Home -> {
        item {
          WebsiteHomeHero(
            title = kind.heroTitle,
            subtitle = kind.heroSubtitle,
            quickLinks = kind.quickLinks,
            onNavigate = onNavigate,
          )
        }
        kind.sections.forEach { section ->
          item {
            SectionHeaderRow(
              title = section.title,
              viewAllUrl = section.viewAllUrl,
              onNavigate = onNavigate,
            )
          }
          if (section.cards.isNotEmpty()) {
            item {
              LazyRow(
                modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                items(section.cards, key = { it.url }) { card ->
                  WebsitePosterCard(card = card, onClick = { onNavigate(card.url) })
                }
              }
            }
          }
          if (section.torrents.isNotEmpty()) {
            items(section.torrents, key = { it.infoHash ?: it.title }) { torrent ->
              Box(modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(horizontal = 16.dp, vertical = 5.dp)) {
                WebsiteTorrentRow(item = torrent, onClick = { onTorrentClick(torrent) })
              }
            }
          }
        }
        if (kind.sections.isEmpty()) {
          item {
            EmptyPageCard(
              message = "Search or pick a section above to browse torrentclaw.com.",
              onRetry = onRetry,
            )
          }
        }
        if (kind.footerLinks.isNotEmpty()) {
          item { WebsiteFooterRow(links = kind.footerLinks, onNavigate = onNavigate) }
        }
      }

      is WebPageKind.Listing -> {
        item {
          Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = kind.heading,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
              )
              kind.resultCountLabel?.let { count ->
                Text(
                  text = count,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            FilterChipStrip(chips = activePage.activeFilters, onNavigate = onNavigate)
            FilterChipStrip(chips = activePage.sortOptions, onNavigate = onNavigate)
          }
        }
        if (kind.cards.isNotEmpty()) {
          val rows = kind.cards.chunked(2)
          items(rows) { pair ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .widthIn(max = 880.dp)
                  .padding(horizontal = 16.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              pair.forEach { card ->
                WebsiteGridMediaCard(
                  card = card,
                  modifier = Modifier.weight(1f),
                  onClick = { onNavigate(card.url) },
                )
              }
              if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
        if (kind.torrents.isNotEmpty()) {
          items(kind.torrents, key = { it.infoHash ?: it.title }) { torrent ->
            Box(modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(horizontal = 16.dp, vertical = 5.dp)) {
              WebsiteTorrentRow(item = torrent, onClick = { onTorrentClick(torrent) })
            }
          }
        }
        if (kind.cards.isEmpty() && kind.torrents.isEmpty()) {
          item {
            EmptyPageCard(
              message = kind.emptyMessage ?: "No matching results found.",
              onRetry = onRetry,
            )
          }
        }
        kind.pagination?.let { pag ->
          item { PaginationBar(pagination = pag, onNavigate = onNavigate) }
        }
      }

      is WebPageKind.Detail -> {
        item {
          DetailHeroSection(
            detail = kind,
            onNavigate = onNavigate,
          )
        }
        if (kind.seasonLinks.isNotEmpty() || kind.episodeLinks.isNotEmpty()) {
          item {
            Column(
              modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(horizontal = 16.dp, vertical = 6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (kind.seasonLinks.isNotEmpty()) {
                Text(
                  text = "Seasons",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterChipStrip(chips = kind.seasonLinks, onNavigate = onNavigate)
              }
              if (kind.episodeLinks.isNotEmpty()) {
                Text(
                  text = "Episodes",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterChipStrip(chips = kind.episodeLinks, onNavigate = onNavigate)
              }
            }
          }
        }
        item {
          DetailTorrentsHeaderAndFilter(
            detail = kind,
            onNavigate = onNavigate,
            onTorrentClick = onTorrentClick,
          )
        }
        if (kind.relatedCards.isNotEmpty()) {
          item {
            SectionHeaderRow(title = "Related", viewAllUrl = null, onNavigate = onNavigate)
            LazyRow(
              modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(kind.relatedCards, key = { it.url }) { card ->
                WebsitePosterCard(card = card, onClick = { onNavigate(card.url) })
              }
            }
          }
        }
      }

      is WebPageKind.Auth -> {
        item {
          WebsiteAuthSection(
            auth = kind,
            onSubmitForm = onSubmitForm,
            onNavigate = onNavigate,
          )
        }
      }

      is WebPageKind.Generic -> {
        item {
          WebsiteGenericSection(
            generic = kind,
            onSubmitForm = onSubmitForm,
            onNavigate = onNavigate,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebsiteHomeHero(
  title: String,
  subtitle: String,
  quickLinks: List<WebNavLink>,
  onNavigate: (String) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (quickLinks.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          quickLinks.take(6).forEach { link ->
            OutlinedButton(
              onClick = { onNavigate(link.url) },
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
              Text(link.label, style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionHeaderRow(
  title: String,
  viewAllUrl: String?,
  onNavigate: (String) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .widthIn(max = 880.dp)
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (!viewAllUrl.isNullOrBlank()) {
      TextButton(onClick = { onNavigate(viewAllUrl) }) {
        Text("View all")
      }
    }
  }
}

@Composable
private fun WebsitePosterCard(card: WebMediaCard, onClick: () -> Unit) {
  Card(
    modifier =
      Modifier.width(136.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(2f / 3f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        if (!card.posterUrl.isNullOrBlank()) {
          AsyncImage(
            model = card.posterUrl,
            contentDescription = card.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
          )
        }
        card.rating?.let { rating ->
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.72f),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = Color(0xFFFFCA28),
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = rating,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
              )
            }
          }
        }
      }
      Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = card.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val sub = listOfNotNull(card.year, card.mediaType).joinToString(" • ")
        if (sub.isNotBlank()) {
          Text(
            text = sub,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun WebsiteGridMediaCard(
  card: WebMediaCard,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Card(
    modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.width(58.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        if (!card.posterUrl.isNullOrBlank()) {
          AsyncImage(
            model = card.posterUrl,
            contentDescription = card.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = card.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(card.year, card.mediaType, card.rating?.let { "★ $it" }).joinToString(" • ")
        if (meta.isNotBlank()) {
          Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebsiteTorrentRow(
  item: WebTorrentItem,
  onClick: () -> Unit,
) {
  val semantic = LocalTorrentClawColors.current
  Card(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      item.qualityScore?.let { score ->
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = semantic.verified.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, semantic.verified.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = score,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = semantic.verified,
            )
            Text(
              text = "Q",
              style = MaterialTheme.typography.labelSmall,
              color = semantic.verified,
            )
          }
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          item.resolution?.let { WebTagBadge(it, MaterialTheme.colorScheme.primary) }
          item.codecs.forEach { codec -> WebTagBadge(codec, MaterialTheme.colorScheme.onSurfaceVariant) }
          item.hdrTags.forEach { hdr -> WebTagBadge(hdr, semantic.hdrGold) }
          item.trueSpecBadge?.let { WebTagBadge(it, semantic.verified) }
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          item.size?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
          }
          item.seeders?.let {
            Text("▲ $it", style = MaterialTheme.typography.labelSmall, color = semantic.seederHigh)
          }
          item.leechers?.let {
            Text("▼ $it", style = MaterialTheme.typography.labelSmall, color = semantic.seederLow)
          }
          item.source?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = "Open or copy torrent link",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }
  }
}

@Composable
private fun WebTagBadge(label: String, tint: Color) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = tint.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.3f)),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeroSection(
  detail: WebPageKind.Detail,
  onNavigate: (String) -> Unit,
) {
  val semantic = LocalTorrentClawColors.current
  Column(
    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.verticalGradient(
              listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.background,
              )
            )
          )
          .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Box(
          modifier =
            Modifier.width(104.dp)
              .aspectRatio(2f / 3f)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
        ) {
          if (!detail.posterUrl.isNullOrBlank()) {
            AsyncImage(
              model = detail.posterUrl,
              contentDescription = detail.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(36.dp))
          }
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = detail.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          val meta =
            listOfNotNull(detail.year, detail.mediaType, detail.rating?.let { "★ $it" })
              .joinToString(" • ")
          if (meta.isNotBlank()) {
            Text(
              text = meta,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (detail.genres.isNotEmpty()) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              detail.genres.forEach { g ->
                WebTagBadge(g, MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }

    detail.overview?.takeIf { it.isNotBlank() }?.let { overview ->
      Text(
        text = overview,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    }

    if (detail.torrentFilters.isNotEmpty()) {
      Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        FilterChipStrip(chips = detail.torrentFilters, onNavigate = onNavigate)
      }
    }
  }
}

@Composable
private fun DetailTorrentsHeaderAndFilter(
  detail: WebPageKind.Detail,
  onNavigate: (String) -> Unit,
  onTorrentClick: (WebTorrentItem) -> Unit,
) {
  var selectedRes by remember(detail.title) { mutableStateOf<String?>(null) }
  val availableRes =
    remember(detail.torrents) {
      detail.torrents.mapNotNull { it.resolution }.distinct()
    }
  val displayed =
    remember(detail.torrents, selectedRes) {
      if (selectedRes == null) detail.torrents
      else detail.torrents.filter { it.resolution.equals(selectedRes, ignoreCase = true) }
    }

  Column(
    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Torrents (${displayed.size})",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (selectedRes != null) {
        TextButton(onClick = { selectedRes = null }) { Text("Show all") }
      }
    }

    if (availableRes.size > 1) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = selectedRes == null,
          onClick = { selectedRes = null },
          label = { Text("All (${detail.torrents.size})") },
        )
        availableRes.forEach { res ->
          FilterChip(
            selected = selectedRes.equals(res, true),
            onClick = { selectedRes = if (selectedRes == res) null else res },
            label = { Text(res) },
          )
        }
      }
    }

    if (displayed.isEmpty()) {
      EmptyPageCard(message = "No indexed torrents parsed on this page yet.", onRetry = { })
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        displayed.forEach { item ->
          WebsiteTorrentRow(item = item, onClick = { onTorrentClick(item) })
        }
      }
    }
  }
}

@Composable
private fun WebsiteAuthSection(
  auth: WebPageKind.Auth,
  onSubmitForm: (WebFormModel, Map<String, String>) -> Unit,
  onNavigate: (String) -> Unit,
) {
  val semantic = LocalTorrentClawColors.current
  Column(
    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (auth.isAccountDashboard) {
            Icon(Icons.Rounded.Verified, contentDescription = null, tint = semantic.verified)
          }
          Text(
            text = auth.heading,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        auth.subtitle?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        auth.statusBanner?.let { banner ->
          val tint = if (auth.isErrorBanner) MaterialTheme.colorScheme.error else semantic.verified
          Surface(
            color = tint.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = banner,
              style = MaterialTheme.typography.bodySmall,
              color = tint,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        if (auth.accountSummaryPairs.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            auth.accountSummaryPairs.forEach { (k, v) ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
          }
        }

        auth.form?.let { form ->
          DynamicWebForm(form = form, onSubmit = { values -> onSubmitForm(form, values) })
        }

        auth.logoutForm?.let { logout ->
          OutlinedButton(
            onClick = { onSubmitForm(logout, emptyMap()) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(logout.submitLabel)
          }
        }

        if (auth.secondaryLinks.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            auth.secondaryLinks.forEach { link ->
              TextButton(onClick = { onNavigate(link.url) }) {
                Text(link.label)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DynamicWebForm(
  form: WebFormModel,
  onSubmit: (Map<String, String>) -> Unit,
) {
  val fieldValues = remember(form) {
    mutableStateMapOf<String, String>().apply {
      form.fields.forEach { put(it.name, it.value) }
    }
  }
  val passwordVisibility = remember(form) { mutableStateMapOf<String, Boolean>() }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    form.fields.filter { !it.isHidden }.forEach { field ->
      val currentValue = fieldValues[field.name].orEmpty()
      val isPassword = field.type.equals("password", ignoreCase = true)
      val visible = passwordVisibility[field.name] == true

      OutlinedTextField(
        value = currentValue,
        onValueChange = { fieldValues[field.name] = it },
        label = { Text(field.label) },
        placeholder = {
          if (field.placeholder.isNotBlank()) Text(field.placeholder)
        },
        singleLine = field.type != "textarea",
        visualTransformation =
          if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
          KeyboardOptions(
            keyboardType =
              when {
                isPassword -> KeyboardType.Password
                field.type.equals("email", true) -> KeyboardType.Email
                else -> KeyboardType.Text
              }
          ),
        trailingIcon =
          if (isPassword) {
            {
              IconButton(onClick = { passwordVisibility[field.name] = !visible }) {
                Icon(
                  imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                  contentDescription = if (visible) "Hide password" else "Show password",
                )
              }
            }
          } else null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
      )
    }

    Button(
      onClick = { onSubmit(fieldValues.toMap()) },
      modifier = Modifier.fillMaxWidth().height(50.dp).testTag("web_form_submit"),
      shape = RoundedCornerShape(12.dp),
    ) {
      Text(form.submitLabel)
    }
  }
}

@Composable
private fun WebsiteGenericSection(
  generic: WebPageKind.Generic,
  onSubmitForm: (WebFormModel, Map<String, String>) -> Unit,
  onNavigate: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = generic.heading,
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onBackground,
    )
    generic.blocks.forEach { block ->
      when (block) {
        is WebBlock.Heading ->
          Text(
            text = block.text,
            style = if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
        is WebBlock.Paragraph ->
          Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        is WebBlock.CodeSnippet ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = block.text,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(12.dp),
            )
          }
        is WebBlock.AlertBanner ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color =
              if (block.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
              else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(block.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
          }
        is WebBlock.LinkList ->
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.links.forEach { link ->
              OutlinedButton(
                onClick = { onNavigate(link.url) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
              ) {
                Text(link.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        is WebBlock.BadgeRow -> Unit
        is WebBlock.KeyValueList -> Unit
      }
    }
    generic.forms.forEach { form ->
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Box(modifier = Modifier.padding(16.dp)) {
          DynamicWebForm(form = form, onSubmit = { values -> onSubmitForm(form, values) })
        }
      }
    }
    if (generic.footerLinks.isNotEmpty()) {
      WebsiteFooterRow(links = generic.footerLinks, onNavigate = onNavigate)
    }
  }
}

@Composable
private fun FilterChipStrip(
  chips: List<WebFilterChip>,
  onNavigate: (String) -> Unit,
) {
  if (chips.isEmpty()) return
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    chips.forEach { chip ->
      FilterChip(
        selected = chip.isSelected,
        onClick = { onNavigate(chip.url) },
        label = { Text(chip.label) },
      )
    }
  }
}

@Composable
private fun PaginationBar(
  pagination: WebPagination,
  onNavigate: (String) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(16.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    pagination.prevUrl?.let { prev ->
      OutlinedButton(onClick = { onNavigate(prev) }) { Text("Previous") }
      Spacer(Modifier.width(8.dp))
    }
    pagination.pageLinks.take(5).forEach { link ->
      if (link.isActive) {
        Button(
          onClick = { onNavigate(link.url) },
          contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
          Text(link.label)
        }
      } else {
        TextButton(onClick = { onNavigate(link.url) }) { Text(link.label) }
      }
    }
    pagination.nextUrl?.let { next ->
      Spacer(Modifier.width(8.dp))
      OutlinedButton(onClick = { onNavigate(next) }) { Text("Next") }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebsiteFooterRow(
  links: List<WebNavLink>,
  onNavigate: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(horizontal = 16.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      links.forEach { link ->
        TextButton(onClick = { onNavigate(link.url) }) {
          Text(link.label, style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@Composable
private fun EmptyPageCard(message: String, onRetry: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(16.dp),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedButton(onClick = onRetry) { Text("Reload page") }
    }
  }
}

@Composable
private fun WebsiteErrorView(
  message: String,
  onRetry: () -> Unit,
  onGoBack: () -> Unit,
  onHome: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.widthIn(max = 460.dp),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.ErrorOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(40.dp),
        )
        Text(
          text = "Unable to load torrentclaw.com",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(onClick = onRetry) { Text("Retry") }
          OutlinedButton(onClick = onGoBack) { Text("Back") }
          TextButton(onClick = onHome) { Text("Home") }
        }
      }
    }
  }
}

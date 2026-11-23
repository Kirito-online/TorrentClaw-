package com.example.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.preferences.PosterSize
import com.example.data.model.ApiHealthState
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.LocalTorrentClawColors

@Composable
fun SectionHeader(
  title: String,
  subtitle: String? = null,
  actionLabel: String? = null,
  onActionClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (actionLabel != null && onActionClick != null) {
      TextButton(onClick = onActionClick) {
        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(4.dp))
        Icon(
          Icons.AutoMirrored.Rounded.ArrowForward,
          contentDescription = actionLabel,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

@Composable
fun MediaPosterCard(
  item: MediaItem,
  posterSize: PosterSize,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val width: Dp = posterSize.widthDp.dp
  val semantic = LocalTorrentClawColors.current
  Column(
    modifier =
      modifier
        .width(width)
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick)
        .testTag("poster_card_${item.id}"),
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (item.mediaType == MediaType.SHOW) Icons.Rounded.Tv else Icons.Rounded.Movie,
            contentDescription = item.title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(34.dp),
          )
        }
      }

      // Subtle bottom gradient for crisp badge legibility
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(52.dp)
            .align(Alignment.BottomCenter)
            .background(
              Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
              )
            ),
      )

      // Top-left type / quality indicator
      item.bestResolution?.let { res ->
        Surface(
          color = Color.Black.copy(alpha = 0.72f),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        ) {
          Text(
            text = res,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }

      // Top-right quality score badge
      item.bestQualityScore?.takeIf { it > 0 }?.let { score ->
        val badgeColor =
          when {
            score >= 80 -> semantic.verified
            score >= 60 -> semantic.hdrGold
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          }
        Surface(
          color = Color.Black.copy(alpha = 0.78f),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
          Text(
            text = "$score",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
          )
        }
      }

      // Bottom metadata row inside poster
      Row(
        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        if (item.rating != null && item.rating > 0.0) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Rounded.Star,
              contentDescription = null,
              tint = semantic.hdrGold,
              modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
              text = String.format("%.1f", item.rating),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
            )
          }
        }
        if (item.torrentCount > 0) {
          Text(
            text = "${item.torrentCount} rel",
            style = MaterialTheme.typography.labelSmall,
            color = semantic.debridCached,
          )
        }
      }
    }

    Spacer(Modifier.height(6.dp))
    Text(
      text = item.title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    val metaLine =
      listOfNotNull(
          item.year?.toString(),
          if (item.mediaType == MediaType.SHOW) "Series" else "Movie",
        )
        .joinToString(" • ")
    Text(
      text = metaLine,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun ApiHealthBanner(
  health: ApiHealthState,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalTorrentClawColors.current
  when (health) {
    ApiHealthState.Connected -> Unit
    is ApiHealthState.RateLimited -> {
      Surface(
        color = semantic.warning.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = semantic.warning, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = "API rate limit reached (${health.retryAfterSeconds}s). Add an API key for higher limits.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
    is ApiHealthState.OfflineCached -> {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = health.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Retry")
          }
        }
      }
    }
    is ApiHealthState.Error -> {
      Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = health.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Retry")
          }
        }
      }
    }
  }
}

@Composable
fun TorrentClawEmptyState(
  title: String,
  message: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_empty_state_1795456050515),
      contentDescription = title,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(168.dp, 126.dp).clip(RoundedCornerShape(16.dp)),
    )
    Spacer(Modifier.height(16.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.height(16.dp))
      Button(onClick = onAction, modifier = Modifier.testTag("empty_state_action")) {
        Text(actionLabel)
      }
    }
  }
}

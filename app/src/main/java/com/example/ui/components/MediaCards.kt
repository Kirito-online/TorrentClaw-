package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.CollectionSummary
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.LocalTorrentClawColors

@Composable
fun FavoriteHeartButton(
  isFavorite: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTagValue: String? = null,
) {
  val semantic = LocalTorrentClawColors.current
  val scale by animateFloatAsState(
    targetValue = if (isFavorite) 1.12f else 1.0f,
    animationSpec = spring(stiffness = Spring.StiffnessMedium),
    label = "favorite_heart_scale",
  )

  Box(
    modifier = modifier.size(48.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = CircleShape,
      color = Color.Black.copy(alpha = 0.62f),
      border =
        BorderStroke(
          1.dp,
          if (isFavorite) semantic.favoriteRose.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.2f),
        ),
      modifier = Modifier.size(34.dp),
    ) {
      IconButton(
        onClick = onClick,
        modifier =
          Modifier.size(48.dp).let { m ->
            if (testTagValue != null) m.testTag(testTagValue) else m
          },
      ) {
        Icon(
          imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
          contentDescription =
            stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
          tint = if (isFavorite) semantic.favoriteRose else Color.White,
          modifier = Modifier.size(18.dp).scale(scale),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroSpotlightCard(
  item: MediaItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isFavorite: Boolean = false,
  onToggleFavorite: (() -> Unit)? = null,
) {
  Card(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
      val imageModel = item.backdropUrl ?: item.posterUrl
      if (!imageModel.isNullOrBlank()) {
        AsyncImage(
          model = imageModel,
          contentDescription = item.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                listOf(
                  Color.Black.copy(alpha = 0.25f),
                  Color(0xFF0B111E).copy(alpha = 0.94f),
                )
              )
            )
      )

      if (onToggleFavorite != null) {
        FavoriteHeartButton(
          isFavorite = isFavorite,
          onClick = onToggleFavorite,
          testTagValue = "spotlight_favorite_button",
          modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
      }

      Row(
        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        if (!item.posterUrl.isNullOrBlank()) {
          AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.width(76.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
          ) {
            Text(
              text = "TRENDING SPOTLIGHT",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val meta =
            listOfNotNull(
                item.year?.toString(),
                item.mediaType.label,
                item.rating?.let { "★ ${"%.1f".format(it)}" },
                item.torrentCount.takeIf { it > 0 }?.let { "$it releases" },
              )
              .joinToString(" • ")
          if (meta.isNotBlank()) {
            Text(
              text = meta,
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFFCBD5E1),
              maxLines = 1,
            )
          }
          Button(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(38.dp),
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Explore Torrents", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}

@Composable
fun PosterMediaCard(
  item: MediaItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isFavorite: Boolean = false,
  onToggleFavorite: (() -> Unit)? = null,
) {
  val semantic = LocalTorrentClawColors.current
  Card(
    modifier = modifier.width(134.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
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
        Icon(
          imageVector =
            if (item.mediaType == MediaType.SHOW) Icons.Rounded.Tv else Icons.Rounded.Movie,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(32.dp),
        )
        if (!item.posterUrl.isNullOrBlank()) {
          AsyncImage(
            model =
              ImageRequest.Builder(LocalContext.current)
                .data(item.posterUrl)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
        if (onToggleFavorite != null) {
          FavoriteHeartButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
            testTagValue = "card_favorite_button_${item.id}",
            modifier = Modifier.align(Alignment.TopStart),
          )
        }
        item.rating?.takeIf { it > 0 }?.let { rating ->
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.74f),
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
                tint = semantic.hdrGold,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "%.1f".format(rating),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
              )
            }
          }
        }
        if (item.torrentCount > 0) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.74f),
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
          ) {
            Text(
              text = "${item.torrentCount} torrents",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }
      Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val sub = listOfNotNull(item.year?.toString(), item.mediaType.label).joinToString(" • ")
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
fun GridMediaCard(
  item: MediaItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isFavorite: Boolean = false,
  onToggleFavorite: (() -> Unit)? = null,
) {
  val semantic = LocalTorrentClawColors.current
  Card(
    modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
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
        Icon(
          imageVector =
            if (item.mediaType == MediaType.SHOW) Icons.Rounded.Tv else Icons.Rounded.Movie,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(34.dp),
        )
        if (!item.posterUrl.isNullOrBlank()) {
          AsyncImage(
            model =
              ImageRequest.Builder(LocalContext.current)
                .data(item.posterUrl)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
        if (onToggleFavorite != null) {
          FavoriteHeartButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
            testTagValue = "card_favorite_button_${item.id}",
            modifier = Modifier.align(Alignment.TopStart),
          )
        }
        item.rating?.takeIf { it > 0 }?.let { r ->
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.74f),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(Icons.Rounded.Star, contentDescription = null, tint = semantic.hdrGold, modifier = Modifier.size(12.dp))
              Text("%.1f".format(r), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
          }
        }
      }
      Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val meta =
          listOfNotNull(
              item.year?.toString(),
              item.mediaType.label,
              item.torrentCount.takeIf { it > 0 }?.let { "$it links" },
            )
            .joinToString(" • ")
        if (meta.isNotBlank()) {
          Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
fun CollectionCard(
  collection: CollectionSummary,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.CollectionsBookmark,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = collection.name,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (collection.itemCount > 0) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = "${collection.itemCount}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
          }
        }
      }
      if (collection.description.isNotBlank()) {
        Text(
          text = collection.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (collection.posterUrls.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          collection.posterUrls.take(4).forEach { url ->
            AsyncImage(
              model = url,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier =
                Modifier.size(width = 48.dp, height = 70.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant),
            )
          }
        }
      }
    }
  }
}

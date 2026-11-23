package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.model.MediaItem
import com.example.data.model.TorrentRelease
import com.example.data.model.formatFileSize
import java.io.File

data class InstalledTorrentClient(
  val packageName: String,
  val label: String,
)

object TorrentIntentHelper {

  fun findInstalledTorrentClients(context: Context): List<InstalledTorrentClient> {
    val testUri = Uri.parse("magnet:?xt=urn:btih:0000000000000000000000000000000000000000")
    val probe = Intent(Intent.ACTION_VIEW, testUri)
    val pm = context.packageManager
    val resolved =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
      } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
      }
    return resolved
      .filter { it.activityInfo.packageName != context.packageName }
      .map { info ->
        InstalledTorrentClient(
          packageName = info.activityInfo.packageName,
          label = info.loadLabel(pm).toString(),
        )
      }
      .distinctBy { it.packageName }
  }

  fun openMagnetLink(
    context: Context,
    magnetUri: String,
    targetPackage: String? = null,
  ): Boolean {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(magnetUri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!targetPackage.isNullOrBlank()) {
          setPackage(targetPackage)
        }
      }
    return runCatching {
        context.startActivity(intent)
        true
      }
      .getOrDefault(false)
  }

  fun openTorrentFile(context: Context, file: File, targetPackage: String? = null): Boolean {
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
          Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-bittorrent")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
          }
        context.startActivity(Intent.createChooser(intent, "Open .torrent file").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
      }
      .getOrDefault(false)
  }

  fun shareTorrentFile(context: Context, file: File, releaseName: String): Boolean {
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
          Intent(Intent.ACTION_SEND).apply {
            type = "application/x-bittorrent"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, releaseName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        context.startActivity(
          Intent.createChooser(intent, "Share .torrent").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        )
        true
      }
      .getOrDefault(false)
  }

  fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
  }

  fun shareText(context: Context, subject: String, text: String) {
    val sendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
      }
    context.startActivity(
      Intent.createChooser(sendIntent, subject).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    )
  }

  fun shareMediaItem(context: Context, item: MediaItem) {
    val yearSuffix = item.year?.let { " ($it)" }.orEmpty()
    val url =
      when {
        item.imdbId != null -> "https://www.imdb.com/title/${item.imdbId}/"
        item.tmdbId != null ->
          "https://www.themoviedb.org/${if (item.mediaType.name == "SHOW") "tv" else "movie"}/${item.tmdbId}"
        else -> "https://torrentclaw.com/?q=${Uri.encode(item.title)}"
      }
    val text = buildString {
      append("${item.title}$yearSuffix\n")
      item.rating?.let { append("Rating: ${"%.1f".format(it)}/10 • ") }
      append("${item.torrentCount} releases on TorrentClaw\n")
      append(url)
    }
    shareText(context, item.title, text)
  }

  fun shareTorrentRelease(context: Context, release: TorrentRelease) {
    val text = buildString {
      append("${release.releaseName}\n")
      append("${release.resolution} • ${formatFileSize(release.sizeBytes)} • Score ${release.qualityScore}/100\n")
      append("Seeders: ${release.seeders} • Source: ${release.trackerSource}\n")
      append("Hash: ${release.infoHash}\n\n")
      append(release.resolvedMagnetUrl)
    }
    shareText(context, release.releaseName, text)
  }

  fun openBrowserUrl(context: Context, url: String) {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(intent) }
  }
}

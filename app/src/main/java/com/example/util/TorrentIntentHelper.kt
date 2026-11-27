package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.web.MagnetTarget
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
        pm.queryIntentActivities(
          probe,
          PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
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
        context.startActivity(
          Intent.createChooser(intent, "Open .torrent file").apply {
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

  fun sharePageUrl(context: Context, title: String, url: String) {
    shareText(context, title.ifBlank { "TorrentClaw" }, url)
  }

  fun shareMagnetTarget(context: Context, target: MagnetTarget) {
    val body = buildString {
      append(target.title)
      val meta = listOfNotNull(target.resolution, target.size, target.seeders?.let { "$it seeders" })
      if (meta.isNotEmpty()) {
        append("\n")
        append(meta.joinToString(" • "))
      }
      append("\n\n")
      append(target.primaryCopyUrl)
    }
    shareText(context, target.title, body)
  }
}

package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.model.TorrentRelease
import com.example.data.model.formatFileSize
import java.io.File

data class InstalledTorrentClient(
  val packageName: String,
  val label: String,
)

object TorrentIntentHelper {

  private fun queryHandlers(context: Context, probe: Intent): List<ResolveInfo> {
    val pm = context.packageManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(
        probe,
        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
      )
    } else {
      @Suppress("DEPRECATION")
      pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
    }
  }

  fun findInstalledTorrentClients(context: Context): List<InstalledTorrentClient> {
    val testUri = Uri.parse("magnet:?xt=urn:btih:0000000000000000000000000000000000000000")
    val probe = Intent(Intent.ACTION_VIEW, testUri)
    val pm = context.packageManager
    return queryHandlers(context, probe)
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
    val uri = Uri.parse(magnetUri)
    if (!targetPackage.isNullOrBlank()) {
      val targeted =
        Intent(Intent.ACTION_VIEW, uri).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          setPackage(targetPackage)
        }
      val launched =
        runCatching {
            context.startActivity(targeted)
            true
          }
          .getOrDefault(false)
      if (launched) return true
    }

    val baseIntent =
      Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    val allHandlers = queryHandlers(context, baseIntent)
    val externalHandlers = allHandlers.filter { it.activityInfo.packageName != context.packageName }
    if (externalHandlers.isEmpty()) {
      return false
    }

    if (externalHandlers.size == 1) {
      val onlyPkg = externalHandlers.first().activityInfo.packageName
      val singleIntent =
        Intent(Intent.ACTION_VIEW, uri).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          setPackage(onlyPkg)
        }
      return runCatching {
          context.startActivity(singleIntent)
          true
        }
        .getOrDefault(false)
    }

    val selfComponents =
      allHandlers
        .filter { it.activityInfo.packageName == context.packageName }
        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        .toTypedArray()

    val chooser =
      Intent.createChooser(baseIntent, "Open magnet link").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (selfComponents.isNotEmpty()) {
          putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, selfComponents)
        }
      }
    return runCatching {
        context.startActivity(chooser)
        true
      }
      .getOrDefault(false)
  }

  fun openTorrentFile(context: Context, file: File, targetPackage: String? = null): Boolean {
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val baseIntent =
          Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-bittorrent")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        if (!targetPackage.isNullOrBlank()) {
          val targeted = Intent(baseIntent).setPackage(targetPackage)
          val ok =
            runCatching {
                context.startActivity(targeted)
                true
              }
              .getOrDefault(false)
          if (ok) return@runCatching true
        }
        val allHandlers = queryHandlers(context, baseIntent)
        val externalHandlers = allHandlers.filter { it.activityInfo.packageName != context.packageName }
        if (externalHandlers.isEmpty()) return@runCatching false
        if (externalHandlers.size == 1) {
          context.startActivity(Intent(baseIntent).setPackage(externalHandlers.first().activityInfo.packageName))
          return@runCatching true
        }
        val selfComponents =
          allHandlers
            .filter { it.activityInfo.packageName == context.packageName }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .toTypedArray()
        context.startActivity(
          Intent.createChooser(baseIntent, "Open .torrent file").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (selfComponents.isNotEmpty()) {
              putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, selfComponents)
            }
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

  fun openWebUrl(context: Context, url: String): Boolean {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
        context.startActivity(intent)
        true
      }
      .getOrDefault(false)
  }

  fun shareTorrentRelease(context: Context, release: TorrentRelease, fallbackUrl: String? = null) {
    val text = buildString {
      append("${release.releaseName}\n")
      append("${release.resolution} • ${formatFileSize(release.sizeBytes)} • Score ${release.qualityScore}/100\n")
      append("Seeders: ${release.seeders} • Source: ${release.trackerSource}\n")
      if (release.infoHash.isNotBlank()) {
        append("Hash: ${release.infoHash}\n\n")
      }
      val link =
        release.resolvedMagnetUrl.takeIf { it.isNotBlank() }
          ?: release.resolvedContentWebUrl
          ?: fallbackUrl
      if (!link.isNullOrBlank()) {
        append(link)
      }
    }
    shareText(context, release.releaseName, text)
  }
}

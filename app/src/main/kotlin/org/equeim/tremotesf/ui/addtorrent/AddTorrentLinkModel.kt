package org.equeim.tremotesf.ui.addtorrent

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.ui.Settings
import timber.log.Timber

class AddTorrentLinkModel(private val initialUri: Uri?, application: Application) :
    AndroidViewModel(application) {

    suspend fun getInitialTorrentLink(): String? {
        initialUri?.let { return it.toString() }
        if (!Settings.fillTorrentLinkFromKeyboard.get()) {
            Timber.d("Filling torrent link from clipboard is disabled")
            return null
        }
        Timber.d("Filling torrent link from clipboard")
        val clipboardManager = getApplication<Application>().getSystemService<ClipboardManager>()
        if (clipboardManager == null) {
            Timber.e("ClipboardManager is null")
            return null
        }
        if (!clipboardManager.hasPrimaryClip()) {
            Timber.d("Clipboard is empty")
            return null
        }
        if (!TORRENT_LINK_MIME_TYPES.any { clipboardManager.primaryClipDescription?.hasMimeType(it) == true }) {
            Timber.d("Clipboard content has unsupported MIME type")
            return null
        }
        return clipboardManager
            .primaryClip
            ?.getTorrentUri(getApplication())
            ?.takeIf { it.type == TorrentUri.Type.Link }
            ?.uri
            ?.toString()
            .also {
                if (BuildConfig.DEBUG) {
                    Timber.d("Torrent link from clipboard is $it")
                }
            }
    }

    fun acceptDragStartEvent(clipDescription: ClipDescription): Boolean {
        Timber.i("Drag start event mime types = ${clipDescription.mimeTypes()}")
        return TORRENT_LINK_MIME_TYPES.any(clipDescription::hasMimeType)
    }

    fun getTorrentLinkFromDropEvent(clipData: ClipData): String? {
        return clipData.getTorrentUri(getApplication())
            ?.takeIf { it.type == TorrentUri.Type.Link }
            ?.uri
            ?.toString()
    }
}
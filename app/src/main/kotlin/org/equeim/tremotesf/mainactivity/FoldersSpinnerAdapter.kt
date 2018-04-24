package org.equeim.tremotesf.mainactivity

import android.content.Context
import com.amjjd.alphanum.AlphanumericComparator
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.BaseSpinnerAdapter
import java.text.Collator

class FoldersSpinnerAdapter(private val context: Context) : BaseSpinnerAdapter(R.string.folders) {
    private val foldersMap = mutableMapOf<String, Int>()
    val folders = mutableListOf<String>()
    private val comparator = AlphanumericComparator(Collator.getInstance())

    override fun getItem(position: Int): String {
        if (position == 0) {
            return context.getString(R.string.torrents_all, Rpc.torrents.size)
        }
        val folder = folders[position - 1]
        val torrents = foldersMap[folder]
        return context.getString(R.string.folders_spinner_text, folder, torrents)
    }

    override fun getCount(): Int {
        return folders.size + 1
    }

    fun update() {
        foldersMap.clear()
        for (torrent in Rpc.torrents) {
            foldersMap[torrent.downloadDirectory] = foldersMap.getOrElse(torrent.downloadDirectory, { 0 }) + 1
        }
        folders.clear()
        folders.addAll(foldersMap.keys.sortedWith(comparator))
        notifyDataSetChanged()
    }
}
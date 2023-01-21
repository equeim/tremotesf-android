package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.*
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.databinding.WebSeedersFragmentBinding
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.StateRestoringListAdapter
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.viewLifecycleObject

class WebSeedersFragment : TorrentPropertiesFragment.PagerFragment(
    R.layout.web_seeders_fragment,
    TorrentPropertiesFragment.PagerAdapter.Tab.WebSeeders
) {
    private val binding by viewLifecycleObject(WebSeedersFragmentBinding::bind)
    private val adapter by viewLifecycleObject { WebSeedersAdapter() }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        binding.webSeedersView.apply {
            adapter = this@WebSeedersFragment.adapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        val propertiesFragmentModel = TorrentPropertiesFragmentViewModel.get(navController)
        propertiesFragmentModel.torrent.launchAndCollectWhenStarted(viewLifecycleOwner, ::update)
    }

    private fun update(torrent: Torrent?) {
        adapter.update(torrent)
        binding.placeholder.visibility = if ((adapter.itemCount == 0) && torrent != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}

private class WebSeedersAdapter : StateRestoringListAdapter<String, WebSeedersAdapter.ViewHolder>(Callback()) {
    private val comparator = AlphanumericComparator()
    private var torrent: Torrent? = null

    override fun allowStateRestoring(): Boolean = torrent != null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.web_seeders_item, parent, false) as TextView
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = getItem(position)
    }

    fun update(torrent: Torrent?) {
        this.torrent = torrent
        submitList(torrent?.data?.webSeeders?.sortedWith(comparator))
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private class Callback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = true
    }
}

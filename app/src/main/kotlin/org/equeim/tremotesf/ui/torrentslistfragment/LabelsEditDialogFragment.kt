// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.getTorrentsLabels
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentsLabels
import org.equeim.tremotesf.ui.NavigationDialogFragment
import timber.log.Timber

class LabelsEditDialogFragment : NavigationDialogFragment() {
    private val args: LabelsEditDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_labels)
            .setView(R.layout.labels_edit_dialog)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val labelsEditView = checkNotNull(requireDialog().findViewById<LabelsEditView>(R.id.labels_edit_view))
                val labels = labelsEditView.enabledLabels
                GlobalRpcClient.performBackgroundRpcRequest(R.string.set_labels_error) {
                    setTorrentsLabels(args.torrentHashStrings.asList(), labels)
                }
                dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismiss() }
            .create()
            .apply {
                setOnShowListener {
                    val labelsEditView = checkNotNull(findViewById<LabelsEditView>(R.id.labels_edit_view))
                    labelsEditView.setInitialEnabledLabels(args.enabledLabels.asList())

                    lifecycleScope.launch {
                        val allLabels = try {
                            GlobalRpcClient.getTorrentsLabels()
                        } catch (e: RpcRequestError) {
                            Timber.e(e, "Failed to get torrents labels")
                            emptySet()
                        }
                        labelsEditView.setAllLabels(allLabels)
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
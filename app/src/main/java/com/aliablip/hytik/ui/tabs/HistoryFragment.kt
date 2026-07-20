package com.aliablip.hytik.ui.tabs

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aliablip.hytik.R
import com.aliablip.hytik.data.db.AppDatabase
import com.aliablip.hytik.data.db.DownloadHistoryEntity
import com.aliablip.hytik.databinding.FragmentHistoryBinding
import com.aliablip.hytik.ui.adapters.HistoryAdapter
import com.aliablip.hytik.ui.utils.FileUtils
import com.aliablip.hytik.ui.utils.UiUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter
    private var currentFilter = "ALL" // "ALL", "VIDEO", "AUDIO", "PHOTO"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilters()
        observeHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onOpen = { item -> FileUtils.openFile(requireContext(), item) },
            onShare = { item -> FileUtils.shareFile(requireContext(), item) },
            onDelete = { item -> confirmDelete(item) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { setFilter("ALL") }
        binding.chipVideo.setOnClickListener { setFilter("VIDEO") }
        binding.chipMusic.setOnClickListener { setFilter("AUDIO") }
        binding.chipPhoto.setOnClickListener { setFilter("PHOTO") }
    }

    private fun setFilter(type: String) {
        currentFilter = type
        observeHistory()
    }

    private fun observeHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val dao = AppDatabase.getInstance(requireContext()).downloadHistoryDao()
                val flow = if (currentFilter == "ALL") {
                    dao.getAllHistoryFlow()
                } else {
                    dao.getHistoryByTypeFlow(currentFilter)
                }

                flow.collectLatest { list ->
                    if (!::adapter.isInitialized || _binding == null) return@collectLatest
                    adapter.submitList(list)
                    if (list.isEmpty()) {
                        binding.layoutEmptyState.visibility = View.VISIBLE
                        binding.rvHistory.visibility = View.GONE
                    } else {
                        binding.layoutEmptyState.visibility = View.GONE
                        binding.rvHistory.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun confirmDelete(item: DownloadHistoryEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val dao = AppDatabase.getInstance(requireContext()).downloadHistoryDao()
                    dao.deleteById(item.id)
                    try {
                        val file = File(item.filePath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        // Ignore file deletion error
                    }
                    UiUtils.showSnackbar(binding.root, "Unduhan dihapus dari riwayat")
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

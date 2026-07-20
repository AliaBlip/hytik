package com.aliablip.hytik.ui.tabs

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aliablip.hytik.R
import com.aliablip.hytik.data.db.AppDatabase
import com.aliablip.hytik.data.engine.EngineMode
import com.aliablip.hytik.data.preferences.AppPreferences
import com.aliablip.hytik.databinding.FragmentSettingsBinding
import com.aliablip.hytik.ui.utils.UiUtils
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferences: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = AppPreferences(requireContext())

        setupUI()
    }

    private fun setupUI() {
        binding.switchAutoPaste.isChecked = preferences.isAutoPasteEnabled
        binding.switchAutoPaste.setOnCheckedChangeListener { _, isChecked ->
            preferences.isAutoPasteEnabled = isChecked
            UiUtils.showToast(requireContext(), if (isChecked) "Deteksi Papan Klip Aktif" else "Deteksi Papan Klip Nonaktif")
        }

        when (preferences.engineMode) {
            EngineMode.TIKWM_HD -> binding.rbEngineTikWm.isChecked = true
            EngineMode.TIKLY_FAST -> binding.rbEngineTikLy.isChecked = true
            else -> binding.rbEngineHybrid.isChecked = true
        }

        binding.rgEngineMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbEngineTikWm -> EngineMode.TIKWM_HD
                R.id.rbEngineTikLy -> EngineMode.TIKLY_FAST
                else -> EngineMode.HYBRID_AUTO
            }
            preferences.engineMode = mode
            UiUtils.showToast(requireContext(), "Mode Engine Diperbarui: ${mode.name}")
        }

        binding.btnClearHistory.setOnClickListener {
            confirmClearHistory()
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pref_clear_history)
            .setMessage("Apakah Anda yakin ingin menghapus seluruh catatan riwayat unduhan di aplikasi? File yang tersimpan di memori perangkat tidak akan terhapus.")
            .setPositiveButton(R.string.yes) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val dao = AppDatabase.getInstance(requireContext()).downloadHistoryDao()
                    dao.deleteAllHistory()
                    UiUtils.showSnackbar(binding.root, "Seluruh riwayat unduhan dibersihkan")
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

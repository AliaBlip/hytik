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
import com.aliablip.hytik.ui.utils.PermissionManager
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
            EngineMode.ULTRA_HD -> binding.rbEngineTikWm.isChecked = true
            EngineMode.TURBO_FAST -> binding.rbEngineTikLy.isChecked = true
            else -> binding.rbEngineHybrid.isChecked = true
        }

        // Update professional labels
        binding.rbEngineHybrid.text = "⚡ ${EngineMode.SMART_AUTO.getDisplayName()} (Direkomendasikan)\n${EngineMode.SMART_AUTO.getDescription()}"
        binding.rbEngineTikWm.text = "💎 ${EngineMode.ULTRA_HD.getDisplayName()}\n${EngineMode.ULTRA_HD.getDescription()}"
        binding.rbEngineTikLy.text = "🚀 ${EngineMode.TURBO_FAST.getDisplayName()}\n${EngineMode.TURBO_FAST.getDescription()}"

        binding.rgEngineMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbEngineTikWm -> EngineMode.ULTRA_HD
                R.id.rbEngineTikLy -> EngineMode.TURBO_FAST
                else -> EngineMode.SMART_AUTO
            }
            preferences.engineMode = mode
            UiUtils.showToast(requireContext(), "Mode Engine: ${mode.getDisplayName()}")
        }

        binding.btnClearHistory.setOnClickListener {
            confirmClearHistory()
        }

        binding.btnCheckPermission.setOnClickListener {
            showPermissionInfo()
        }

        // Permission status display
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val hasMedia = PermissionManager.hasMediaPermissions(requireContext())
        val hasNotif = PermissionManager.hasNotificationPermission(requireContext())
        binding.tvPermissionStatus.text = when {
            hasMedia && hasNotif -> "✅ Semua izin media & notifikasi aktif. HyTik siap digunakan optimal."
            hasMedia -> "⚠️ Izin media aktif, tapi notifikasi belum. Aktifkan notifikasi untuk melihat progress di bar."
            else -> "❌ Izin media belum aktif. Tap 'Kelola Izin' untuk mengaktifkan akses profesional."
        }
    }

    private fun showPermissionInfo() {
        val perms = PermissionManager.getRequiredMediaPermissions()
        val msg = StringBuilder()
        msg.append("HyTik memerlukan izin berikut untuk pengalaman profesional:\n\n")
        perms.forEach { p ->
            val status = if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), p) == android.content.pm.PackageManager.PERMISSION_GRANTED) "✅" else "❌"
            msg.append("$status ${p.substringAfterLast(".")}\n")
            msg.append("${PermissionManager.getPermissionRationale(p)}\n\n")
        }
        msg.append("Tanpa izin, download tetap bisa di Android 10+ via MediaStore, tapi akses galeri & notifikasi tidak optimal.")

        AlertDialog.Builder(requireContext())
            .setTitle("Izin Media & Notifikasi")
            .setMessage(msg.toString())
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                PermissionManager.openAppSettings(requireContext())
            }
            .setNegativeButton("Tutup", null)
            .show()
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

    override fun onResume() {
        super.onResume()
        if (_binding != null) updatePermissionStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

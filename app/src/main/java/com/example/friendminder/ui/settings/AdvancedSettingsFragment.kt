package com.example.friendminder.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.storage.BirthdayWorkScheduler
import com.example.friendminder.databinding.FragmentAdvancedSettingsBinding
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Explicit opt-in for direct SMS sending (chefcai/friend-minder#38), plus the
 * birthday/special-date reminder toggle (FRM-54). Reached via the gear icon
 * on HomeFragment's toolbar, deliberately separate from the tap-to-send
 * flow: SEND_SMS is now only ever requested here, after the user has read
 * the explanation and turned the setting on themselves, never as an
 * automatic side effect of tapping a reminder notification.
 *
 * There is deliberately no "Open Settings" deep link for a denied SEND_SMS
 * permission here (chefcai/friend-minder#38 follow-up): unlike the
 * READ_CONTACTS flow elsewhere in the app, Android treats SEND_SMS as a
 * hardware/SMS-restricted permission and grays out its per-app toggle in
 * system Settings, so that affordance was a dead end for users. The
 * permissionDeniedHelper text below instead explains the graceful fallback
 * (messages still open in the user's SMS app) and that re-checking the
 * checkbox is how to retry the OS permission prompt.
 */
class AdvancedSettingsFragment : Fragment() {

    private var _binding: FragmentAdvancedSettingsBinding? = null
    private val binding get() = _binding!!

    // Guards against each checkbox's own listener re-firing when we set its
    // checked state programmatically from refresh() below. Separate flags
    // per checkbox since they're independent settings.
    private var isSyncingUi = false
    private var isSyncingBirthdayUi = false

    private val requestSmsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewLifecycleOwner.lifecycleScope.launch {
                ServiceLocator.settingsRepository.setDirectSendEnabled(granted)
                refresh()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdvancedSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.title = getString(R.string.title_advanced_settings)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.directSendCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isSyncingUi) return@setOnCheckedChangeListener
            if (isChecked && !isSendSmsGranted()) {
                requestSmsPermission.launch(Manifest.permission.SEND_SMS)
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    ServiceLocator.settingsRepository.setDirectSendEnabled(isChecked)
                    refresh()
                }
            }
        }

        binding.birthdayCheckCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isSyncingBirthdayUi) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                ServiceLocator.settingsRepository.setBirthdayCheckEnabled(isChecked)
                val scheduler = BirthdayWorkScheduler(requireContext().applicationContext)
                if (isChecked) scheduler.ensureScheduled() else scheduler.cancel()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch { refresh() }
    }

    private fun isSendSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private suspend fun refresh() {
        val permissionGranted = isSendSmsGranted()
        val settingsRepo = ServiceLocator.settingsRepository

        // If the stored opt-in flag says enabled but the OS permission was
        // revoked externally (e.g. via system Settings) since it was last
        // checked here, correct the flag rather than show a checked box
        // that no longer reflects reality.
        if (!permissionGranted && settingsRepo.isDirectSendEnabled()) {
            settingsRepo.setDirectSendEnabled(false)
        }

        isSyncingUi = true
        binding.directSendCheckbox.isChecked = permissionGranted && settingsRepo.isDirectSendEnabled()
        isSyncingUi = false

        binding.permissionDeniedHelper.visibility = if (permissionGranted) View.GONE else View.VISIBLE

        isSyncingBirthdayUi = true
        binding.birthdayCheckCheckbox.isChecked = settingsRepo.isBirthdayCheckEnabled()
        isSyncingBirthdayUi = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): AdvancedSettingsFragment = AdvancedSettingsFragment()
    }
}

package com.example.friendminder.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.friendminder.databinding.FragmentOverallHistoryBinding

/**
 * Temporary stub for the "Overall History" bottom-nav destination (FRM-100).
 *
 * FRM-100's sign-off explicitly calls for this: slot 2 of the bottom nav
 * had no screen behind it when nav was approved, because FRM-101 (this
 * screen's real content - the former Dashboard's key-metric card + monthly
 * chart, per Designer-P3's spec) hadn't been specced yet. Rather than leave
 * the destination dead or build ahead of an unspecced/unapproved design,
 * this is a placeholder MainActivity's bottom-nav controller can route to
 * today; replace wholesale once FRM-101 lands a signed-off proposal.
 */
class OverallHistoryFragment : Fragment() {

    private var _binding: FragmentOverallHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): OverallHistoryFragment = OverallHistoryFragment()
    }
}

package com.example.friendminder.ui.contactdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogConfirmStopTrackingBinding
import com.example.friendminder.ui.common.applyPhaseThreeSheetChrome
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * GH #117 single-contact removal confirmation (SCREENS-PHASE3.md §6.3):
 * "Confirmation bottom sheet, because this does destroy data: 'This will
 * delete {n} logged outreaches. {Name} stays in your phone's contacts.'"
 * Mirrors [com.example.friendminder.ui.home.ConfirmBulkRemovalDialogFragment]'s
 * split (FRM-112): takes the contact's name and outreach count as
 * arguments - [ContactDetailFragment] already knows both by the time it
 * shows this - and hands back a plain confirm/cancel via [RESULT_KEY]; the
 * caller does the actual removal only after a confirmed result, same
 * "picker never persists" separation used throughout this app.
 */
class ConfirmStopTrackingDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogConfirmStopTrackingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogConfirmStopTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyPhaseThreeSheetChrome()

        val contactName = requireArguments().getString(ARG_CONTACT_NAME).orEmpty()
        val outreachCount = requireArguments().getInt(ARG_OUTREACH_COUNT)

        binding.confirmTitle.text = getString(R.string.format_stop_tracking_confirm_title, contactName)
        binding.confirmBody.text = getString(
            R.string.format_stop_tracking_confirm_body,
            resources.getQuantityString(R.plurals.format_bulk_removal_confirm_outreaches, outreachCount, outreachCount),
            contactName
        )
        binding.confirmButton.text = getString(R.string.action_stop_tracking)
        binding.confirmButton.setOnClickListener {
            setFragmentResult(RESULT_KEY, bundleOf())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "confirm_stop_tracking_result"
        private const val ARG_CONTACT_NAME = "arg_contact_name"
        private const val ARG_OUTREACH_COUNT = "arg_outreach_count"

        fun newInstance(contactName: String, outreachCount: Int): ConfirmStopTrackingDialogFragment =
            ConfirmStopTrackingDialogFragment().apply {
                arguments = bundleOf(
                    ARG_CONTACT_NAME to contactName,
                    ARG_OUTREACH_COUNT to outreachCount
                )
            }
    }
}

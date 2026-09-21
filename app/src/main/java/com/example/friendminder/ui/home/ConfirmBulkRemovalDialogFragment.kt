package com.example.friendminder.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogConfirmBulkRemovalBinding
import com.example.friendminder.ui.common.applyPhaseThreeSheetChrome
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * FRM-112 bulk-removal confirmation (SCREENS-PHASE3.md §10.4): "One bottom
 * sheet naming the real cost ... This deletes 37 logged outreaches. They
 * stay in your phone's contacts." Takes both counts as arguments (this
 * fragment does no repository work itself - [HomeFragment] already knows
 * both by the time it shows this) and hands back a plain confirm/cancel
 * via [RESULT_KEY]; the caller does the actual removal only after a
 * confirmed result, same "picker never persists" split as
 * [com.example.friendminder.ui.addcontacts.BulkGroupPickerDialogFragment].
 */
class ConfirmBulkRemovalDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogConfirmBulkRemovalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogConfirmBulkRemovalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyPhaseThreeSheetChrome()

        val contactCount = requireArguments().getInt(ARG_CONTACT_COUNT)
        val outreachCount = requireArguments().getInt(ARG_OUTREACH_COUNT)

        binding.confirmTitle.text = resources.getQuantityString(
            R.plurals.format_bulk_removal_confirm_people, contactCount, contactCount
        )
        // §10.4's copy is two independently-pluralized counts in one
        // sentence ("Stop tracking 3 people? This deletes 37 logged
        // outreaches."), which a single Android <plurals> resource can't
        // express - resolving each count's wording separately and joining
        // them is simpler than forcing one quantity to drive both.
        binding.confirmBody.text = getString(
            R.string.format_bulk_removal_confirm_body,
            resources.getQuantityString(R.plurals.format_bulk_removal_confirm_outreaches, outreachCount, outreachCount)
        )
        // §10.4 doesn't give the button its own count-varying copy (only
        // the title and body clauses carry numbers), so this is a plain
        // string rather than a third plurals resource.
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
        const val RESULT_KEY = "confirm_bulk_removal_result"
        private const val ARG_CONTACT_COUNT = "arg_contact_count"
        private const val ARG_OUTREACH_COUNT = "arg_outreach_count"

        fun newInstance(contactCount: Int, outreachCount: Int): ConfirmBulkRemovalDialogFragment =
            ConfirmBulkRemovalDialogFragment().apply {
                arguments = bundleOf(
                    ARG_CONTACT_COUNT to contactCount,
                    ARG_OUTREACH_COUNT to outreachCount
                )
            }
    }
}

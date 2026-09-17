package com.example.friendminder.ui.groups

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogGroupEditBinding
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Create/edit a [com.example.friendminder.data.models.ContactGroup] (FRM-56;
 * SCREENS-PHASE2.md §3). One dialog handles both create (no [ARG_GROUP_ID])
 * and edit (existing group id passed in) rather than two near-identical
 * classes.
 *
 * Reports back via the Fragment Result API ([RESULT_KEY]) rather than a
 * constructor lambda, since a lambda isn't safely retained across
 * configuration change the way a `Bundle`-backed result is.
 */
class GroupEditDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogGroupEditBinding? = null
    private val binding get() = _binding!!

    private val groupId: String? by lazy { requireArguments().getString(ARG_GROUP_ID) }
    private var selectedColor: Int = 0
    private val swatchViews = mutableListOf<View>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogGroupEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dialogTitle.text = getString(
            if (groupId == null) R.string.action_new_group else R.string.title_edit_group
        )
        binding.closeButton.setOnClickListener { dismiss() }
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.nameInput.doOnTextChanged { text, _, _, _ ->
            binding.saveButton.isEnabled = !text.isNullOrBlank()
        }
        binding.saveButton.text = getString(if (groupId == null) R.string.action_create else R.string.action_save)
        binding.saveButton.isEnabled = false
        binding.saveButton.setOnClickListener { save() }

        buildSwatches()

        val existingId = groupId
        if (existingId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val existing = ServiceLocator.contactGroupRepository.getGroup(existingId)
                if (existing != null) {
                    binding.nameInput.setText(existing.name)
                    binding.saveButton.isEnabled = existing.name.isNotBlank()
                    selectColor(existing.color)
                }
            }
        } else {
            selectColor(ContextCompat.getColor(requireContext(), GROUP_COLOR_RES[0]))
        }
    }

    private fun buildSwatches() {
        binding.colorSwatchContainer.removeAllViews()
        swatchViews.clear()
        val density = resources.displayMetrics.density
        val swatchSizePx = (SWATCH_SIZE_DP * density).toInt()
        val touchTargetPx = (TOUCH_TARGET_DP * density).toInt()

        GROUP_COLOR_RES.forEachIndexed { index, colorRes ->
            val color = ContextCompat.getColor(requireContext(), colorRes)
            val swatch = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(swatchSizePx, swatchSizePx, android.view.Gravity.CENTER)
                background = plainSwatchDrawable(color)
            }
            val target = FrameLayout(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(touchTargetPx, touchTargetPx).also {
                    if (index > 0) it.marginStart = (SWATCH_MARGIN_DP * density).toInt()
                }
                contentDescription = resources.getStringArray(R.array.group_color_names)[index]
                isClickable = true
                isFocusable = true
                addView(swatch)
                setOnClickListener { selectColor(color) }
            }
            swatchViews += swatch
            binding.colorSwatchContainer.addView(target)
        }
    }

    private fun selectColor(color: Int) {
        selectedColor = color
        GROUP_COLOR_RES.forEachIndexed { index, colorRes ->
            val c = ContextCompat.getColor(requireContext(), colorRes)
            swatchViews[index].background = if (c == color) selectedSwatchDrawable(c) else plainSwatchDrawable(c)
        }
    }

    private fun plainSwatchDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun selectedSwatchDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((2 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(requireContext(), R.color.fm_primary))
    }

    private fun save() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) return
        binding.saveButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val existingId = groupId
            if (existingId == null) {
                ServiceLocator.groupService.createGroup(name, selectedColor, icon = null)
            } else {
                ServiceLocator.groupService.renameGroup(existingId, name)
                ServiceLocator.groupService.recolorGroup(existingId, selectedColor)
            }
            setFragmentResult(RESULT_KEY, bundleOf())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "group_edit_result"
        private const val ARG_GROUP_ID = "arg_group_id"
        private const val SWATCH_SIZE_DP = 40
        private const val TOUCH_TARGET_DP = 48
        private const val SWATCH_MARGIN_DP = 4

        private val GROUP_COLOR_RES = intArrayOf(
            R.color.fm_group_color_1,
            R.color.fm_group_color_2,
            R.color.fm_group_color_3,
            R.color.fm_group_color_4,
            R.color.fm_group_color_5,
            R.color.fm_group_color_6,
            R.color.fm_group_color_7,
            R.color.fm_group_color_8
        )

        fun newInstance(groupId: String? = null): GroupEditDialogFragment =
            GroupEditDialogFragment().apply {
                arguments = bundleOf(ARG_GROUP_ID to groupId)
            }
    }
}

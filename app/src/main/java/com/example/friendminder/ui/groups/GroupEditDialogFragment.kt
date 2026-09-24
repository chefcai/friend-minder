package com.example.friendminder.ui.groups

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogGroupEditBinding
import com.example.friendminder.ui.common.FmBottomSheet
import com.example.friendminder.ui.common.IdentityPalette
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Create/edit a [com.example.friendminder.data.models.ContactGroup] (FRM-56).
 * One sheet handles both create (no [ARG_GROUP_ID]) and edit.
 *
 * FRM-174 (DL-2): on the shared [FmBottomSheet] - sentence-case title,
 * one full-width primary ("Create group" / "Save") that says why when it
 * is disabled. The 8 swatches are a 2 x 4 grid of 48dp circles, 24dp apart
 * with 16dp between rows (they used to be one horizontal row whose 8th
 * swatch was clipped to a 17dp-wide target). The selected swatch gets a
 * 2dp fm_ink ring with a 3dp gap outside it, instead of an fm_primary
 * stroke drawn on the swatch itself.
 *
 * Reports back via the Fragment Result API ([RESULT_KEY]).
 */
class GroupEditDialogFragment : FmBottomSheet() {

    private var _binding: DialogGroupEditBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val groupId: String? by lazy { requireArguments().getString(ARG_GROUP_ID) }
    private var selectedColor: Int = 0
    private val swatchCells = mutableListOf<View>()

    override fun sheetTitle(): CharSequence =
        getString(if (groupId == null) R.string.title_new_group_sheet else R.string.title_edit_group_sheet)

    override fun primaryLabel(): CharSequence =
        getString(if (groupId == null) R.string.action_create_group else R.string.action_save)

    override fun onPrimaryClick() = save()

    override fun onCreateSheetContent(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?): View {
        _binding = DialogGroupEditBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.nameInput.doOnTextChanged { _, _, _, _ -> renderPrimaryEnabled() }
        renderPrimaryEnabled()
        buildSwatches()

        val existingId = groupId
        if (existingId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val existing = ServiceLocator.contactGroupRepository.getGroup(existingId)
                if (existing != null) {
                    binding.nameInput.setText(existing.name)
                    // FRM-176: an unknown stored colour selects its nearest swatch.
                    selectColor(IdentityPalette.nearestIdentityColor(requireContext(), existing.color))
                }
            }
        } else {
            selectColor(ContextCompat.getColor(requireContext(), GROUP_COLOR_RES[0]))
        }
    }

    private fun renderPrimaryEnabled() {
        val hasName = !binding.nameInput.text.isNullOrBlank()
        setPrimaryEnabled(
            hasName,
            getString(if (groupId == null) R.string.helper_group_needs_name_create else R.string.helper_group_needs_name_save)
        )
    }

    private fun buildSwatches() {
        binding.swatchGrid.removeAllViews()
        swatchCells.clear()
        val cellPx = resources.getDimensionPixelSize(R.dimen.fm_swatch_cell)
        val gapH = resources.getDimensionPixelSize(R.dimen.fm_swatch_cell_gap_h)
        val gapV = resources.getDimensionPixelSize(R.dimen.fm_swatch_cell_gap_v)
        val names = resources.getStringArray(R.array.group_color_names)
        GROUP_COLOR_RES.toList().chunked(SWATCHES_PER_ROW).forEachIndexed { rowIndex, rowColors ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { if (rowIndex > 0) it.topMargin = gapV }
            }
            rowColors.forEachIndexed { columnIndex, colorRes ->
                val index = rowIndex * SWATCHES_PER_ROW + columnIndex
                val color = ContextCompat.getColor(requireContext(), colorRes)
                val cell = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(cellPx, cellPx).also {
                        if (columnIndex > 0) it.marginStart = gapH
                    }
                    contentDescription = names[index]
                    isClickable = true
                    isFocusable = true
                    background = swatchDrawable(color, selected = false)
                    setOnClickListener { selectColor(color) }
                }
                swatchCells += cell
                row.addView(cell)
            }
            binding.swatchGrid.addView(row)
        }
    }

    private fun selectColor(color: Int) {
        selectedColor = color
        GROUP_COLOR_RES.forEachIndexed { index, colorRes ->
            val c = ContextCompat.getColor(requireContext(), colorRes)
            val isSelected = c == color
            swatchCells[index].background = swatchDrawable(c, isSelected)
            swatchCells[index].isSelected = isSelected
            ViewCompat.setStateDescription(
                swatchCells[index],
                getString(if (isSelected) R.string.content_desc_candidate_selected else R.string.content_desc_candidate_not_selected)
            )
        }
    }

    /** 48dp swatch centred in the 58dp cell; when selected, a 2dp fm_ink ring at the cell edge (3dp gap). */
    private fun swatchDrawable(color: Int, selected: Boolean): Drawable {
        val inset = resources.getDimensionPixelSize(R.dimen.fm_swatch_ring_width) +
            resources.getDimensionPixelSize(R.dimen.fm_swatch_ring_gap)
        val swatch = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            // FRM-176: light identity fills keep the 1dp fm_divider hairline.
            val index = IdentityPalette.nearestIndex(color)
            if (IdentityPalette.isLightFill(index)) {
                setStroke(
                    resources.displayMetrics.density.toInt().coerceAtLeast(1),
                    ContextCompat.getColor(requireContext(), R.color.fm_divider)
                )
            }
        }
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            if (selected) {
                setStroke(
                    resources.getDimensionPixelSize(R.dimen.fm_swatch_ring_width),
                    ContextCompat.getColor(requireContext(), R.color.fm_ink)
                )
            }
        }
        return LayerDrawable(arrayOf(ring, swatch)).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    private fun save() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) return
        sheet.sheetPrimaryButton.isEnabled = false

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
        private const val SWATCHES_PER_ROW = 4

        // FRM-176 (GR-1): the swatches are the shared identity palette.
        private val GROUP_COLOR_RES = IdentityPalette.colorRes

        fun newInstance(groupId: String? = null): GroupEditDialogFragment =
            GroupEditDialogFragment().apply {
                arguments = bundleOf(ARG_GROUP_ID to groupId)
            }
    }
}

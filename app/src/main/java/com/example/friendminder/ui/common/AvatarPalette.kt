package com.example.friendminder.ui.common

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.friendminder.R
import kotlin.math.abs

/**
 * Deterministic avatar fallback color assignment (FRM-61, redefined for
 * Phase 3 - FRM-108), per docs/DESIGN-SYSTEM-PHASE3.md §2.6:
 *
 * - 10 colors (`R.color.fm_avatar_1`..`fm_avatar_10`) spanning warm and cool
 *   hues - not a single band any more - assigned by
 *   `abs(contactId.hashCode()) % 10` so the same contact always gets the
 *   same color across app runs and screens. Minimum pairwise ΔE is 21.2,
 *   up from Phase 2's 4.8 (eight of the old ten read as the same
 *   blue-green on a real device).
 * - Light mode only (explicit product decision - see
 *   DESIGN-SYSTEM-PHASE3.md §1.6): each swatch pairs with a single fixed
 *   initials-text color, chosen by Designer for contrast, not computed at
 *   runtime. Unlike the Phase 2 palette, which shared exactly two text
 *   colors (a white and a single dark ink) across all ten swatches, four
 *   of the Phase 3 fills (Sky, Seafoam, Sand, Rose) are light enough that
 *   they each need their *own* dark ink to clear 4.5:1 - one shared dark
 *   value doesn't clear the bar against all four. `fm_avatar_text_1..10`
 *   in colors.xml is therefore a fully per-index array, not two colors
 *   referenced ten times.
 * - Index 1 (Pine, `#2C6E49`) is the one value that isn't a straight port
 *   from the widened-band revision Cai approved on 2026-09-20: it replaced
 *   `Deep Teal #0F6F6A` on 2026-09-21 because that value sat ΔE 7.0 from
 *   `fm_primary`, which the status badge (§2.5) also uses - a contact row
 *   with a streak would have carried two near-identical teals at either
 *   end. Pine restores the gap to 21.7 and leaves the other nine untouched.
 */
object AvatarPalette {

    private const val PALETTE_SIZE = 10

    private val lightColorRes = intArrayOf(
        R.color.fm_avatar_1,
        R.color.fm_avatar_2,
        R.color.fm_avatar_3,
        R.color.fm_avatar_4,
        R.color.fm_avatar_5,
        R.color.fm_avatar_6,
        R.color.fm_avatar_7,
        R.color.fm_avatar_8,
        R.color.fm_avatar_9,
        R.color.fm_avatar_10
    )

    /**
     * Per-index initials-text color, in the same order as [lightColorRes].
     * Fixed pairing from DESIGN-SYSTEM-PHASE3.md §2.6's table - not
     * derived, just looked up.
     */
    private val initialsTextColorRes = intArrayOf(
        R.color.fm_avatar_text_1,  // Pine    #2C6E49 - WHITE (6.12:1)
        R.color.fm_avatar_text_2,  // Cyan    #0B6E92 - WHITE (5.74:1)
        R.color.fm_avatar_text_3,  // Sky     #9CCBEC - #10323F (7.85:1)
        R.color.fm_avatar_text_4,  // Indigo  #3D55A4 - WHITE (6.92:1)
        R.color.fm_avatar_text_5,  // Slate   #62707C - WHITE (5.09:1)
        R.color.fm_avatar_text_6,  // Seafoam #8ED0C4 - #0F3B34 (7.06:1)
        R.color.fm_avatar_text_7,  // Sand    #DCC084 - #3A2F14 (7.46:1)
        R.color.fm_avatar_text_8,  // Clay    #B85535 - WHITE (4.78:1)
        R.color.fm_avatar_text_9,  // Plum    #80458A - WHITE (6.70:1)
        R.color.fm_avatar_text_10  // Rose    #E8B3BC - #4A2028 (7.59:1)
    )

    /** Stable palette index for a contact. */
    fun indexFor(contactId: String): Int = abs(contactId.hashCode()) % PALETTE_SIZE

    /** The avatar background color for [contactId]. */
    @ColorInt
    fun colorFor(context: Context, contactId: String): Int =
        ContextCompat.getColor(context, lightColorRes[indexFor(contactId)])

    /** The initials text color to pair with [colorFor] for the same contact. */
    @ColorInt
    fun initialsTextColorFor(context: Context, contactId: String): Int =
        ContextCompat.getColor(context, initialsTextColorRes[indexFor(contactId)])
}

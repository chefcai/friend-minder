package com.example.friendminder.ui.common

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.friendminder.R
import kotlin.math.abs

/**
 * Deterministic avatar fallback color assignment (FRM-61), per Designer's
 * DESIGN-SYSTEM-PHASE2.md §2.4:
 *
 * - 10 colors within a single cool hue band (~166-212 deg, teal through
 *   blue; `R.color.fm_avatar_1`..`fm_avatar_10`), assigned by
 *   `abs(contactId.hashCode()) % 10` so the same contact always gets the
 *   same color across app runs and screens.
 * - Light mode only (explicit product decision - see
 *   DESIGN-SYSTEM-PHASE2.md §0/§9): each swatch pairs with a single fixed
 *   initials-text color (white or dark ink) chosen by Designer for contrast,
 *   not computed at runtime. There is no dark-theme variant or lightness
 *   shift to compute, so the HSL math this object previously carried
 *   (`lighten`/`rgbToHsl`/`hslToRgb`) has been removed along with it.
 * - The index-to-text-color pairing below is unchanged from an earlier
 *   "Warm Circle" pass of this palette (chefcai/friend-minder#67, since
 *   superseded before it should have shipped): the current cool palette was
 *   produced by a luminance-matched hue rotation of the Warm Circle values,
 *   which preserves which indices need dark-ink text vs white text exactly.
 *   Only the color resource values (in colors.xml) and the ratios in the
 *   comments below changed.
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
     * Fixed pairing from DESIGN-SYSTEM-PHASE2.md §2.4's contrast table -
     * not derived, just looked up.
     */
    private val initialsTextColorRes = intArrayOf(
        R.color.fm_avatar_initials_white, // 0 #227376 - WHITE (5.55:1)
        R.color.fm_avatar_initials_ink,   // 1 #41A2D0 - INK (5.05:1)
        R.color.fm_avatar_initials_white, // 2 #26665D - WHITE (6.69:1)
        R.color.fm_avatar_initials_ink,   // 3 #83B5E5 - INK (6.71:1)
        R.color.fm_avatar_initials_white, // 4 #3E6E74 - WHITE (5.70:1)
        R.color.fm_avatar_initials_white, // 5 #2D6E5F - WHITE (5.99:1)
        R.color.fm_avatar_initials_white, // 6 #446E8C - WHITE (5.45:1)
        R.color.fm_avatar_initials_ink,   // 7 #25AFD2 - INK (5.63:1)
        R.color.fm_avatar_initials_white, // 8 #2F5451 - WHITE (8.37:1)
        R.color.fm_avatar_initials_ink    // 9 #9FBFE3 - INK (7.63:1)
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

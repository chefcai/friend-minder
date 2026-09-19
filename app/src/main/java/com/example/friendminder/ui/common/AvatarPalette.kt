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
 * - 10 warm-family colors (`R.color.fm_avatar_1`..`fm_avatar_10`), assigned
 *   by `abs(contactId.hashCode()) % 10` so the same contact always gets the
 *   same color across app runs and screens.
 * - Light mode only (explicit product decision - see
 *   DESIGN-SYSTEM-PHASE2.md §0/§9): each swatch pairs with a single fixed
 *   initials-text color (white or dark ink) chosen by Designer for contrast,
 *   not computed at runtime. There is no dark-theme variant or lightness
 *   shift to compute, so the HSL math this object previously carried
 *   (`lighten`/`rgbToHsl`/`hslToRgb`) has been removed along with it.
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
        R.color.fm_avatar_initials_white, // 0 #A2522F - WHITE (5.55:1)
        R.color.fm_avatar_initials_ink,   // 1 #CC8C33 - INK (5.10:1)
        R.color.fm_avatar_initials_white, // 2 #944438 - WHITE (6.68:1)
        R.color.fm_avatar_initials_ink,   // 3 #D4AC35 - INK (6.75:1)
        R.color.fm_avatar_initials_white, // 4 #855E47 - WHITE (5.69:1)
        R.color.fm_avatar_initials_white, // 5 #A34643 - WHITE (5.98:1)
        R.color.fm_avatar_initials_white, // 6 #7C673C - WHITE (5.44:1)
        R.color.fm_avatar_initials_ink,   // 7 #DF8F49 - INK (5.65:1)
        R.color.fm_avatar_initials_white, // 8 #68443B - WHITE (8.47:1)
        R.color.fm_avatar_initials_ink    // 9 #D1BB61 - INK (7.61:1)
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

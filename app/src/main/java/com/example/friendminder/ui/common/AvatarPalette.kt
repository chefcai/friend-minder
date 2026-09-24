package com.example.friendminder.ui.common

import android.content.Context
import androidx.annotation.ColorInt
import kotlin.math.abs

/**
 * Deterministic avatar fallback colour (FRM-61, FRM-108). FRM-176 (GR-1):
 * avatars now draw from the shared 8-colour [IdentityPalette] - the same
 * colours groups use - via , so the same
 * contact always gets the same colour. Each fill has one fixed initials ink
 * (DESIGN-SYSTEM Section 2.6); the four light fills (Sky, Seafoam, Sand,
 * Rose) also take a 1dp fm_divider hairline (see AvatarBinder).
 */
object AvatarPalette {

    /** Stable palette index for a contact. */
    fun indexFor(contactId: String): Int = abs(contactId.hashCode()) % IdentityPalette.SIZE

    /** True for the four light fills that need a 1dp hairline (FRM-127). */
    fun isLightFill(paletteIndex: Int): Boolean = IdentityPalette.isLightFill(paletteIndex)

    /** The avatar background colour for [contactId]. */
    @ColorInt
    fun colorFor(context: Context, contactId: String): Int = IdentityPalette.color(context, indexFor(contactId))

    /** The initials text colour to pair with [colorFor] for the same contact. */
    @ColorInt
    fun initialsTextColorFor(context: Context, contactId: String): Int =
        IdentityPalette.ink(context, indexFor(contactId))
}

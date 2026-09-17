package com.example.friendminder.ui.common

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.example.friendminder.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic avatar fallback color assignment (FRM-40), per Designer's
 * Confluence "Phase 2 UI Specs" §1:
 *
 * - 8 HSL-distributed colors (`R.color.fm_avatar_1`..`fm_avatar_8`), assigned
 *   by `abs(contactId.hashCode()) % 8` so the same contact always gets the
 *   same color across app runs and screens.
 * - Dark theme uses "the same hues +8% lightness" - computed here via HSL
 *   math rather than hardcoded as separate dark color resources, per the
 *   spec's explicit "compute at build time, don't hardcode" instruction.
 * - Initials text is white in light theme. In dark theme, indices 3 and 6
 *   (`fm_avatar_4` and `fm_avatar_7`) flip to dark initials text to preserve
 *   contrast against their lightened backgrounds; every other index keeps
 *   white text in both themes. This flip is the Designer spec's decision,
 *   not re-derived here.
 *
 * The HSL math below works on plain packed ARGB ints rather than
 * `android.graphics.Color` so it's a plain-JUnit-testable pure function
 * (no Robolectric needed) — see AvatarPaletteTest.
 */
object AvatarPalette {

    private const val PALETTE_SIZE = 8
    private const val DARK_LIGHTNESS_BOOST = 0.08f
    private val DARK_INITIALS_TEXT_INDICES = setOf(3, 6)

    private val lightColorRes = intArrayOf(
        R.color.fm_avatar_1,
        R.color.fm_avatar_2,
        R.color.fm_avatar_3,
        R.color.fm_avatar_4,
        R.color.fm_avatar_5,
        R.color.fm_avatar_6,
        R.color.fm_avatar_7,
        R.color.fm_avatar_8
    )

    /** Stable palette index for a contact, independent of theme. */
    fun indexFor(contactId: String): Int = abs(contactId.hashCode()) % PALETTE_SIZE

    /**
     * The avatar background color for [contactId] in the given theme.
     * [isDarkTheme] should reflect the current UI mode (e.g. from
     * `resources.configuration` or a night-mode check), not the system
     * default, so it stays correct if the app ever offers an in-app theme
     * override.
     */
    @ColorInt
    fun colorFor(context: Context, contactId: String, isDarkTheme: Boolean): Int {
        val lightColor = ContextCompat.getColor(context, lightColorRes[indexFor(contactId)])
        return if (isDarkTheme) lighten(lightColor, DARK_LIGHTNESS_BOOST) else lightColor
    }

    /** The initials text color to pair with [colorFor] for the same contact/theme. */
    @ColorInt
    fun initialsTextColorFor(context: Context, contactId: String, isDarkTheme: Boolean): Int {
        val useDarkText = isDarkTheme && indexFor(contactId) in DARK_INITIALS_TEXT_INDICES
        return ContextCompat.getColor(
            context,
            if (useDarkText) R.color.fm_avatar_initials_dark else R.color.fm_avatar_initials_light
        )
    }

    /**
     * Adds [amount] (0f-1f) to the color's HSL lightness, clamped to [0, 1].
     * Hue and saturation are preserved. Alpha is preserved unchanged.
     */
    @ColorInt
    @VisibleForTesting
    internal fun lighten(@ColorInt color: Int, amount: Float): Int {
        val alpha = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        val (h, s, l) = rgbToHsl(r, g, b)
        return hslToRgb(h, s, min(1f, l + amount), alpha)
    }

    @VisibleForTesting
    internal fun rgbToHsl(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val l = (maxC + minC) / 2f
        if (maxC == minC) return Triple(0f, 0f, l)

        val d = maxC - minC
        val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
        val h = when (maxC) {
            rf -> ((gf - bf) / d + (if (gf < bf) 6f else 0f))
            gf -> (bf - rf) / d + 2f
            else -> (rf - gf) / d + 4f
        } / 6f
        return Triple(h, s, l)
    }

    @ColorInt
    @VisibleForTesting
    internal fun hslToRgb(h: Float, s: Float, l: Float, alpha: Int = 0xFF): Int {
        val (r, g, b) = if (s == 0f) {
            val gray = (l * 255f).toInt()
            Triple(gray, gray, gray)
        } else {
            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            Triple(
                (hueToRgb(p, q, h + 1f / 3f) * 255f).toInt(),
                (hueToRgb(p, q, h) * 255f).toInt(),
                (hueToRgb(p, q, h - 1f / 3f) * 255f).toInt()
            )
        }
        return (alpha and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF)
    }

    private fun hueToRgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
}

package com.example.friendminder.ui.common

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.friendminder.R
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * FRM-176 (GR-1): the one 8-colour identity palette (DESIGN-SYSTEM Section
 * 2.6) shared by avatar fallbacks, group circles, chip dots and the group
 * swatch grid. Minimum pairwise dE76 is 26.0 (Sky / Seafoam).
 *
 * Group colours are stored as raw ARGB Ints. The Architect's Room v2 -> v3
 * migration (GROUP_COLOR_REMAP_2_3) moved the eight old group colours onto
 * this palette; anything else (an unknown or future value) is shown as its
 * nearest identity colour by dE76 via [nearestIndex] / [nearestIdentityColor].
 * nearestIndex reproduces the migration table exactly for the old colours
 * (IdentityPaletteTest).
 */
object IdentityPalette {

    const val SIZE = 8

    val colorRes = intArrayOf(
        R.color.fm_identity_1, R.color.fm_identity_2, R.color.fm_identity_3, R.color.fm_identity_4,
        R.color.fm_identity_5, R.color.fm_identity_6, R.color.fm_identity_7, R.color.fm_identity_8
    )

    val inkRes = intArrayOf(
        R.color.fm_identity_ink_1, R.color.fm_identity_ink_2, R.color.fm_identity_ink_3, R.color.fm_identity_ink_4,
        R.color.fm_identity_ink_5, R.color.fm_identity_ink_6, R.color.fm_identity_ink_7, R.color.fm_identity_ink_8
    )

    /**
     * The same eight fills as plain ARGB, for the pure colour maths below
     * (unit tests have no Resources). Must match fm_identity_1..8.
     */
    val ARGB: IntArray = intArrayOf(
        0xFF2C6E49.toInt(), 0xFF0B6E92.toInt(), 0xFF9CCBEC.toInt(), 0xFF3D55A4.toInt(),
        0xFF8ED0C4.toInt(), 0xFFDCC084.toInt(), 0xFF80458A.toInt(), 0xFFE8B3BC.toInt()
    )

    /** Sky, Seafoam, Sand, Rose: light enough to need the 1dp fm_divider hairline on white. */
    private val lightFillIndices = setOf(2, 4, 5, 7)

    fun isLightFill(index: Int): Boolean = index in lightFillIndices

    @ColorInt
    fun color(context: Context, index: Int): Int = ContextCompat.getColor(context, colorRes[index])

    @ColorInt
    fun ink(context: Context, index: Int): Int = ContextCompat.getColor(context, inkRes[index])

    /** Index of the identity colour nearest to [argb] by CIE76 dE (alpha ignored). */
    fun nearestIndex(argb: Int): Int {
        val target = toLab(argb)
        return ARGB.indices.minByOrNull { deltaE76(target, toLab(ARGB[it])) } ?: 0
    }

    /** The identity colour a stored group colour is displayed as. */
    @ColorInt
    fun nearestIdentityColor(context: Context, argb: Int): Int = color(context, nearestIndex(argb))

    /** CIE76 colour difference between two ARGB colours. */
    fun deltaE76(a: Int, b: Int): Double = deltaE76(toLab(a), toLab(b))

    private fun deltaE76(a: DoubleArray, b: DoubleArray): Double =
        sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))

    // sRGB (D65) -> CIE L*a*b*.
    @Suppress("MagicNumber")
    private fun toLab(argb: Int): DoubleArray {
        fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = lin((argb shr 16) and 0xFF)
        val g = lin((argb shr 8) and 0xFF)
        val b = lin(argb and 0xFF)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double): Double = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }
}

package com.example.friendminder.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure parts of [AvatarPalette] (FRM-40 / FRM-45): the
 * deterministic index assignment and the HSL lightness math used to derive
 * dark-theme avatar colors "at build time" rather than from hardcoded hex
 * values. [AvatarPalette.colorFor] / [AvatarPalette.initialsTextColorFor]
 * themselves need a real Android `Context` for resource lookup and aren't
 * covered here — see the manual verification note in the PR description.
 */
class AvatarPaletteTest {

    @Test
    fun `indexFor is deterministic for the same contact id`() {
        val id = "contact-42"
        val first = AvatarPalette.indexFor(id)
        val second = AvatarPalette.indexFor(id)
        assertEquals(first, second)
    }

    @Test
    fun `indexFor stays within the 8-color palette range`() {
        val ids = listOf("1", "42", "abc-def-ghi", "", "🎉", "a".repeat(500))
        ids.forEach { id ->
            val index = AvatarPalette.indexFor(id)
            assertTrue("index $index for id '$id' out of range", index in 0..7)
        }
    }

    @Test
    fun `indexFor spreads different ids across the palette`() {
        val ids = (0 until 200).map { "contact-$it" }
        val distinctIndices = ids.map(AvatarPalette::indexFor).toSet()
        // Not a strict uniform-distribution assertion, just a sanity check
        // that hashCode() % 8 isn't collapsing everything onto one index.
        assertTrue("expected more than one distinct index across 200 ids", distinctIndices.size > 1)
    }

    @Test
    fun `lighten increases lightness and preserves hue and alpha`() {
        val original = 0xFF5C6BC0.toInt() // fm_avatar_1
        val lightened = AvatarPalette.lighten(original, 0.08f)

        val (hBefore, _, lBefore) = AvatarPalette.rgbToHsl(
            (original ushr 16) and 0xFF,
            (original ushr 8) and 0xFF,
            original and 0xFF
        )
        val (hAfter, _, lAfter) = AvatarPalette.rgbToHsl(
            (lightened ushr 16) and 0xFF,
            (lightened ushr 8) and 0xFF,
            lightened and 0xFF
        )

        assertTrue("lightness should increase", lAfter > lBefore)
        assertEquals("hue should be preserved", hBefore, hAfter, 0.01f)
        assertEquals("alpha should be preserved", (original ushr 24) and 0xFF, (lightened ushr 24) and 0xFF)
    }

    @Test
    fun `lighten clamps at full lightness instead of overflowing`() {
        val white = 0xFFFFFFFF.toInt()
        val lightened = AvatarPalette.lighten(white, 0.5f)
        assertEquals(white, lightened)
    }

    @Test
    fun `hslToRgb round-trips through rgbToHsl within rounding tolerance`() {
        val samples = listOf(0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEC7063.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        samples.forEach { color ->
            val r = (color ushr 16) and 0xFF
            val g = (color ushr 8) and 0xFF
            val b = color and 0xFF
            val (h, s, l) = AvatarPalette.rgbToHsl(r, g, b)
            val roundTripped = AvatarPalette.hslToRgb(h, s, l)

            val rr = (roundTripped ushr 16) and 0xFF
            val rg = (roundTripped ushr 8) and 0xFF
            val rb = roundTripped and 0xFF

            assertTrue("red channel drifted too far for $color", Math.abs(rr - r) <= 1)
            assertTrue("green channel drifted too far for $color", Math.abs(rg - g) <= 1)
            assertTrue("blue channel drifted too far for $color", Math.abs(rb - b) <= 1)
        }
    }
}

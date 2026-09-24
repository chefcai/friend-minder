package com.example.friendminder.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FRM-176 (GR-1): the identity palette's colour maths - the spec's minimum
 * pairwise dE76, and where each old-palette group colour renders if one is
 * still stored (the v2 -> v3 migration itself now sets every group to Pine,
 * FRM-180; this covers devices that reached v3 before that change).
 */
class IdentityPaletteTest {

    private val names = listOf("Pine", "Cyan", "Sky", "Indigo", "Seafoam", "Sand", "Plum", "Rose")

    @Test
    fun paletteHasEightColours() {
        assertEquals(8, IdentityPalette.ARGB.size)
        assertEquals(IdentityPalette.SIZE, IdentityPalette.ARGB.size)
    }

    @Test
    fun minPairwiseDeltaEIs26() {
        val argb = IdentityPalette.ARGB
        var min = Double.MAX_VALUE
        for (i in argb.indices) for (j in i + 1 until argb.size) {
            min = minOf(min, IdentityPalette.deltaE76(argb[i], argb[j]))
        }
        assertEquals(26.0, min, 0.05)
    }

    @Test
    fun eachColourIsItsOwnNearest() {
        IdentityPalette.ARGB.forEachIndexed { i, c -> assertEquals(i, IdentityPalette.nearestIndex(c)) }
    }

    @Test
    fun oldGroupColoursRenderAsNearestFill() {
        val expected = mapOf(
            0xFF138173.toInt() to "Pine", // Teal
            0xFF14A3BB.toInt() to "Cyan", // Cyan
            0xFF83C3F5.toInt() to "Sky", // Sky
            0xFF136967.toInt() to "Pine", // Deep Teal
            0xFF3C989F.toInt() to "Seafoam", // Slate
            0xFF276274.toInt() to "Cyan", // Ink Blue
            0xFFCCE0E9.toInt() to "Sky", // Mist
            0xFF152A2B.toInt() to "Cyan" // Midnight
        )
        expected.forEach { (argb, name) ->
            assertEquals("#%06X".format(argb and 0xFFFFFF), name, names[IdentityPalette.nearestIndex(argb)])
        }
    }

    @Test
    fun lightFillsAreSkySeafoamSandRose() {
        val light = (0 until 8).filter { IdentityPalette.isLightFill(it) }.map { names[it] }
        assertTrue(light == listOf("Sky", "Seafoam", "Sand", "Rose"))
    }
}

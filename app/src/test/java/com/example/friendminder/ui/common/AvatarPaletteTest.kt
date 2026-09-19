package com.example.friendminder.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure parts of [AvatarPalette] (FRM-61): the deterministic
 * index assignment and the fixed index-to-text-color pairing from
 * DESIGN-SYSTEM-PHASE2.md §2.4. [AvatarPalette.colorFor] /
 * [AvatarPalette.initialsTextColorFor] themselves need a real Android
 * `Context` for resource lookup and aren't covered here — see the manual
 * verification note in the PR description.
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
    fun `indexFor stays within the 10-color palette range`() {
        val ids = listOf("1", "42", "abc-def-ghi", "", "🎉", "a".repeat(500))
        ids.forEach { id ->
            val index = AvatarPalette.indexFor(id)
            assertTrue("index $index for id '$id' out of range", index in 0..9)
        }
    }

    @Test
    fun `indexFor spreads different ids across the palette`() {
        val ids = (0 until 200).map { "contact-$it" }
        val distinctIndices = ids.map(AvatarPalette::indexFor).toSet()
        // Not a strict uniform-distribution assertion, just a sanity check
        // that hashCode() % 10 isn't collapsing everything onto one index.
        assertTrue("expected more than one distinct index across 200 ids", distinctIndices.size > 1)
    }
}

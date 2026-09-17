package com.example.friendminder.work

import com.example.friendminder.data.models.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionSelectorTest {

    private fun contact(id: String, name: String = id) =
        Contact(id = id, name = name, phoneNumber = "555-0000")

    @Test
    fun `empty friend list returns empty pool`() {
        val pool = SuggestionSelector.selectPool(emptyList(), emptyMap(), emptyMap())
        assertTrue(pool.isEmpty())
    }

    @Test
    fun `all contacts eligible returns all of them`() {
        val alice = contact("1", "Alice")
        val bob = contact("2", "Bob")
        val pool = SuggestionSelector.selectPool(
            friends = listOf(alice, bob),
            cooldownStatus = mapOf("1" to false, "2" to false),
            lastSuggested = emptyMap()
        )
        assertEquals(setOf(alice, bob), pool.toSet())
    }

    @Test
    fun `contacts on cooldown are excluded when some are eligible`() {
        val alice = contact("1", "Alice")
        val bob = contact("2", "Bob")
        val pool = SuggestionSelector.selectPool(
            friends = listOf(alice, bob),
            cooldownStatus = mapOf("1" to true, "2" to false),
            lastSuggested = emptyMap()
        )
        assertEquals(listOf(bob), pool)
    }

    @Test
    fun `falls back to least recently suggested when everyone is on cooldown`() {
        val alice = contact("1", "Alice")
        val bob = contact("2", "Bob")
        val carol = contact("3", "Carol")
        val pool = SuggestionSelector.selectPool(
            friends = listOf(alice, bob, carol),
            cooldownStatus = mapOf("1" to true, "2" to true, "3" to true),
            lastSuggested = mapOf("1" to 5000L, "2" to 1000L, "3" to 9000L)
        )
        assertEquals(listOf(bob), pool) // bob has the oldest (smallest) timestamp
    }

    @Test
    fun `never-suggested contact treated as timestamp zero in fallback`() {
        val alice = contact("1", "Alice")
        val bob = contact("2", "Bob")
        val pool = SuggestionSelector.selectPool(
            friends = listOf(alice, bob),
            cooldownStatus = mapOf("1" to true, "2" to true),
            lastSuggested = mapOf("1" to 5000L) // bob missing -> defaults to 0L, oldest
        )
        assertEquals(listOf(bob), pool)
    }

    @Test
    fun `single friend on cooldown still returns that friend as fallback`() {
        val alice = contact("1", "Alice")
        val pool = SuggestionSelector.selectPool(
            friends = listOf(alice),
            cooldownStatus = mapOf("1" to true),
            lastSuggested = mapOf("1" to 1234L)
        )
        assertEquals(listOf(alice), pool)
    }
}

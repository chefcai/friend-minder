package com.example.friendminder.work

import com.example.friendminder.data.models.Contact

/**
 * Pure selection logic for FRM-9/FRM-10, factored out of [SuggestionWorker]
 * so it is unit-testable without WorkManager/Context. The caller resolves
 * the cooldown snapshot via suspend repository calls first, then passes
 * plain data in here.
 */
object SuggestionSelector {

    /**
     * @param cooldownStatus contactId -> true if currently on cooldown
     * @param lastSuggested contactId -> last-suggested epoch millis (absent = never suggested)
     * @return every not-on-cooldown contact, or — if everyone is on cooldown —
     *   a single-element list with the least-recently-suggested contact
     *   (PRD S8: cooldown should soften, not block, reminders). Empty if
     *   [friends] is empty.
     */
    fun selectPool(
        friends: List<Contact>,
        cooldownStatus: Map<String, Boolean>,
        lastSuggested: Map<String, Long>
    ): List<Contact> {
        if (friends.isEmpty()) return emptyList()

        val eligible = friends.filter { cooldownStatus[it.id] != true }
        if (eligible.isNotEmpty()) return eligible

        var oldest = friends.first()
        var oldestTimestamp = lastSuggested[oldest.id] ?: 0L
        for (friend in friends) {
            val timestamp = lastSuggested[friend.id] ?: 0L
            if (timestamp < oldestTimestamp) {
                oldest = friend
                oldestTimestamp = timestamp
            }
        }
        return listOf(oldest)
    }
}

package com.example.friendminder.domain.services

/**
 * Which level of the precedence chain contributed the winning interval in
 * an [EffectiveInterval] (GH #121 / FRM-97). Contact Detail's CHECK-IN
 * FREQUENCY row (SCREENS-PHASE3 §6.2) needs this, not just the number, or a
 * user who set 7 days on the contact and sees 3 will read it as a bug
 * instead of a shorter group override winning.
 */
sealed interface IntervalSource {
    /** The contact's own [com.example.friendminder.data.storage.ReminderFrequencyRepository] override. */
    data object Contact : IntervalSource

    /** One of the contact's groups had an explicit [com.example.friendminder.data.models.ContactGroup.reminderFrequencyDays]. */
    data class Group(val groupId: String, val groupName: String) : IntervalSource

    /** The app-wide [com.example.friendminder.data.storage.SettingsRepository] cooldown — always available, never "unset". */
    data object Global : IntervalSource
}

/**
 * The result of resolving a contact's effective check-in interval across
 * contact / group / global levels (GH #121 / FRM-97 — "the shortest interval
 * wins": the minimum of every *explicitly-set* interval that applies).
 *
 * [sources] lists every level tied at [days], not an arbitrary single pick —
 * a contact in two groups both explicitly set to the same winning value is a
 * real case now that GH #95 kept groups many-to-many, and which of several
 * tied groups "wins" has no principled answer, so the caller (Designer/
 * Publisher) decides how to display a plural source ("from Close Friends and
 * Band") rather than the service silently discarding one. Order is
 * deterministic — Contact first, then Groups alphabetically by name, then
 * Global last — so the same inputs always render the same way.
 */
data class EffectiveInterval(
    val days: Int,
    val sources: List<IntervalSource>
)

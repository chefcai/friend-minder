package com.example.friendminder.ui.common

/**
 * FRM-155 (audit CD-3 / AC-1): lets a screen declare whether the app's
 * bottom nav is shown while it is on top, instead of MainActivity keeping a
 * hard-coded list. MainActivity reads it on every back-stack change.
 *
 * Default is shown - the Phase 3 "nav on every screen" rule still holds for
 * everything else, including Group Detail, Advanced and Notifications.
 * Cai's 2026-09-23 decision narrows it for exactly three screens: Contact
 * Detail (8 above-the-fold decisions with it) and both add-contacts steps
 * (a nav tap silently abandoned the selection).
 */
interface BottomNavPolicy {
    val showsBottomNav: Boolean get() = true
}

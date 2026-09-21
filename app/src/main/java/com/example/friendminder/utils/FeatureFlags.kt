package com.example.friendminder.utils

/**
 * App-wide feature toggles. First one added for FRM-114; none of this
 * existed before that ticket - see its Jira comment for why manual
 * outreach logging is the one going behind a flag rather than being
 * deleted.
 */
object FeatureFlags {
    /**
     * Manual/backdated outreach logging (the "Log outreach" button on
     * Contact Detail, and everything it unlocks: recording outreach that
     * happened outside the app, backdating it, attaching a note, and the
     * non-SMS types CALL/IN_PERSON/VIDEO/OTHER). Deferred to a higher
     * subscription tier (FRM-114, Cai's 2026-09-21 decision) - off for
     * now, but [com.example.friendminder.ui.outreach.OutreachLogDialogFragment]
     * and the services behind it stay fully wired so re-enabling is a
     * one-line flip, not a rebuild. Automatic logging via the nudge ->
     * send-SMS path ([com.example.friendminder.notifications.SmsLaunchActivity])
     * is unrelated to this flag and keeps working regardless.
     */
    const val MANUAL_OUTREACH_LOGGING = false
}

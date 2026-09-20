# Friend-minder Phase 2 — Architecture (Architect, FRM-3-Phase2)

Covers FRM-30 through FRM-35: the Phase 2 data model, storage layer, statistics
service, and WorkManager extensions. See `claude/friend-minder-phase-2-PRD.md`
(project docs) for feature requirements this implements.

## Storage layer decision: SharedPreferences + JSON, then a partial Room migration (FRM-81)

The PRD (§8.1) left this open ("Room database (preferred) or SharedPreferences +
JSON if Room adds too much overhead"). The MVP codebase already used
SharedPreferences + Gson exclusively — `SharedPrefsFriendListRepository`,
`SharedPrefsCooldownRepository`, `SharedPrefsSettingsRepository` — with no Room
dependency, no `kapt`/`ksp`, and a stated F-Droid philosophy of "a small,
inspectable dependency tree" (see `ServiceLocator`'s KDoc). Phase 2 initially
kept that pattern for all five of its new repositories, for the reasons below.

**Update (FRM-81, after beta with a single confirmed user, no production data
at stake): `ContactGroupRepository`, `SpecialDateRepository`, and
`OutreachLogRepository` moved to Room.** The original "zero migration risk"
rationale below no longer applied once there was no real user data to put at
risk, and the Dashboard chart bug (`DashboardChartCalculator.weeklyBuckets()`
subtracting cumulative counts backwards) was the kind of correctness bug a
hand-rolled JSON store makes easier to introduce than a real relational store
with actual FK/CASCADE semantics would have. `Settings`, `Cooldown`,
`ReminderFrequency`, `StatisticsCache`, and Contact/FriendList **stayed on
SharedPreferences+JSON** — deliberately scoped down from "migrate everything"
to just the three repositories with real relational shape (M:N membership,
append-mostly logs, replace-by-source dates). See "Room migration (FRM-81)"
below for the mechanics; the reasoning that follows here is the original
Phase 2 rationale for *not* migrating, kept for the historical record and
because it still applies to the five repositories that stayed on
SharedPreferences.

Phase 2 originally continued the MVP's pattern rather than introducing Room,
for these reasons:

- **Consistency.** Every new repository (`ContactGroupRepository`,
  `SpecialDateRepository`, `OutreachLogRepository`, `ReminderFrequencyRepository`,
  `StatisticsCacheRepository`) follows the exact same interface +
  `SharedPrefs*` implementation shape as the MVP's three repositories.
- **Scale.** This is a personal contact list (tens, not thousands, of
  contacts); none of Phase 2's query patterns need SQL — everything is
  "all entries for one contact" or "all entries," which a JSON blob handles
  fine at this scale.
- **Zero migration risk.** Introducing Room would mean either running the
  MVP's existing SharedPreferences data through a one-time import into a new
  SQLite database (real migration risk, real failure modes) or running two
  storage systems side by side. Staying with SharedPreferences avoids that
  entirely — see "Migration strategy" below.
- **Build simplicity.** No `kapt`/`ksp` annotation processing added to the
  build, no schema export files to manage, no `Migration` classes to write
  and test.

If a future phase needs real relational queries (e.g. cross-contact
aggregate reporting at large scale), revisit this decision then — the
repository *interfaces* defined here are the seam a Room-backed
implementation would slot behind without changing any caller.

## Data model

New entities (`data/models/`), all plain data classes with no Android
dependency:

```
ContactGroup(id, name, color, icon?, createdAt)
SpecialDate(id, contactId, label, month, day, reminderDaysBefore, source: CONTACTS|CUSTOM)
OutreachLog(id, contactId, timestamp, type: SMS|CALL|IN_PERSON|VIDEO|OTHER, note?)
ContactStatistics(contactId, lastContacted?, daysSinceContact?, streak, reachRate, totalContacts, computedAt)
AggregateStatistics(totalFriends, medianDaysSinceContact?, healthiestStreaks, mostNeglected, monthlyOutreachCount)
```

**Deliberate deviation from the PRD §7 sketch:** `Contact` is *not* extended
with `groupIds`/`reminderFrequency`/`specialDates` fields. Group membership,
frequency overrides, and special dates each live in their own repository,
keyed by `contactId`, instead. Two reasons:

1. **Backward compatibility is structural, not procedural.** Gson deserializes
   the MVP's existing `Contact` JSON via reflection, bypassing Kotlin
   constructor defaults — a missing field in old JSON does not reliably
   become the new field's default value the way a plain Kotlin instantiation
   would. Adding fields to `Contact` risked exactly the "backward
   compatibility with MVP data" failure FRM-31 calls out. Leaving `Contact`
   and the Friend List JSON blob completely untouched sidesteps the problem
   instead of working around it.
2. **Independent lifecycles.** Groups, frequency overrides, special dates,
   and outreach logs are all edited independently of the Friend List itself
   and of each other; separate stores mean none of these features can
   corrupt another's data or the MVP's.

`ContactGroup` also omits the PRD's `memberCount` field — see
`GroupService.getMemberCount`'s KDoc: computed on demand from membership data
so it can never drift out of sync with actual membership.

## Storage layer (`data/storage/`)

| Repository | Backing store | Notes |
|---|---|---|
| `ContactGroupRepository` | Room (`contact_groups` + `contact_group_membership`, FRM-81) | Group list + M:N membership; `deleteGroup` cascades to membership rows via `ForeignKey(onDelete = CASCADE)`. Was `friend_minder_groups` SharedPreferences until FRM-81. |
| `ReminderFrequencyRepository` | `friend_minder_reminder_frequency` (SharedPreferences) | `contactId -> Int` override; null/absent = use `SettingsRepository.getCooldownDays()`. Out of scope for the FRM-81 migration. |
| `SpecialDateRepository` | Room (`special_dates`, FRM-81) | Birthdays (`source = CONTACTS`) and custom dates (`source = CUSTOM`) in one table; `replaceContactsSourced` is a `@Transaction` delete-then-insert DAO method. Was `friend_minder_special_dates` SharedPreferences until FRM-81. |
| `OutreachLogRepository` | Room (`outreach_logs`, FRM-81) | Append-mostly log table. Was `friend_minder_outreach_log` SharedPreferences until FRM-81. |
| `StatisticsCacheRepository` | `friend_minder_statistics_cache` (SharedPreferences) | `contactId -> ContactStatistics`, with `computedAt` for staleness checks. Out of scope for the FRM-81 migration. |

`CooldownRepository` (existing, MVP) gained two new methods —
`getReminderCount` / `incrementReminderCount` — because reach rate (PRD §6.5)
needs a lifetime count of reminders *sent*, which the MVP never tracked (only
the *last* suggestion timestamp, for cooldown checking). `SuggestionWorker`
now calls `incrementReminderCount` alongside its existing `setLastSuggestion`
call.

`ContactsLoader` (existing, MVP) gained `loadBirthdays()`, following the same
`ContactsContract` query pattern as `loadContactsWithPhoneNumbers()`. It
parses both `--MM-DD` (no year) and `yyyy-MM-dd` event date formats and
returns month/day only, per the PRD's privacy note.

## Domain services (`domain/services/`) — API contracts for Designer/Publisher

Four services, each with an interface (the contract) and a `Default*`
implementation. All are wired through `ServiceLocator` (e.g.
`ServiceLocator.groupService`), matching the MVP's existing manual-DI pattern
— no Hilt/Dagger/Koin.

**`GroupService`** — CRUD for `ContactGroup` + M:N membership.
```kotlin
val group = ServiceLocator.groupService.createGroup(name = "Close Friends", color = 0xFF4CAF50.toInt(), icon = null)
ServiceLocator.groupService.assignContactToGroup(contactId = "42", groupId = group.id)
val members: List<Contact> = ServiceLocator.groupService.getContactsInGroup(group.id)
```

**`OutreachLogService`** — manual + auto outreach logging.
```kotlin
ServiceLocator.outreachLogService.logOutreach(contactId = "42", type = OutreachType.IN_PERSON, note = "Coffee at Brew Haven")
val history = ServiceLocator.outreachLogService.getHistory("42") // newest first
```
**Integration point for Publisher:** `SmsLaunchActivity` should call
`logOutreach(contactId, type = OutreachType.SMS)` whenever a reminder
actually results in a sent text, so `getLastContacted` reflects real
outreach rather than only `CooldownRepository`'s suggestion-bookkeeping
timestamp. This wasn't done here since it touches Publisher's own file
(`SmsLaunchActivity`) and Publisher owns that integration's UX (e.g. whether
a failed direct-send should still log).

**`StatisticsService`** — per-contact and aggregate stats, cached with a
24-hour TTL (PRD §8.4 "compute on-demand and cache; refresh daily"), or pass
`forceRefresh = true` after a contact change.
```kotlin
val stats = ServiceLocator.statisticsService.getStatistics(contactId = "42")
val dashboard = ServiceLocator.statisticsService.getAggregateStatistics()
```
The math itself (`streak`, `reachRate`, `median`, `daysSince`) is factored
into a pure `StatisticsCalculator` object with no Android/repository
dependency — same pattern as the MVP's `SuggestionSelector` and
`SlotScheduling` — so it's unit-tested directly (`StatisticsCalculatorTest`,
12 cases, all sub-millisecond).

Streak/reach-rate semantics, since the PRD glossary is qualitative:
- **Streak** = length of the trailing run of outreach entries (newest first)
  whose consecutive gaps never exceed `frequencyDays + 1` day of grace. A
  single logged contact is a streak of 1; no logs is a streak of 0.
- **Reach rate** = `min(outreachCount, remindersSent) / remindersSent * 100`,
  capped at 100% (a contact can have more logs than tracked reminders once
  manual logging is added — that's a healthy outcome, not an error).

**`BirthdayService`** — birthdays + custom special dates.
```kotlin
ServiceLocator.birthdayService.refreshBirthdaysFromContacts() // after Friend List changes, and daily via BirthdayWorker
val upcoming = ServiceLocator.birthdayService.getUpcomingDates(withinDays = 14) // PRD §6.3 "next 14 days" widget
ServiceLocator.birthdayService.addCustomSpecialDate(contactId = "42", label = "Anniversary", month = 6, day = 12)
```
`reminderDaysBefore` follows the PRD §7 convention: `0` = day of, a negative
number = that many days before (e.g. `-7`). `getDueToday()` is what
`BirthdayWorker` polls each morning.

## WorkManager (`work/`)

- **`SuggestionWorker`** (existing, MVP) — modified, not replaced. It now
  looks up each contact's `ReminderFrequencyRepository` override before
  checking cooldown, falling back to the global default (FRM-32). It also
  records a reminder-count increment for `StatisticsService`.
- **`BirthdayWorker`** (new) — daily `CoroutineWorker`: calls
  `BirthdayService.refreshBirthdaysFromContacts()`, then posts a notification
  (via `NotificationHelper.postSpecialDateReminder`, new) for everything
  `getDueToday()` returns. Reuses the existing `SmsLaunchActivity` tap-to-send
  flow — a birthday reminder is pre-filled with "Happy birthday, {name}! 🎉";
  a custom date gets a generic "{label} today" body.
- **`BirthdayWorkScheduler`** (new) — schedules `BirthdayWorker` as a daily
  periodic work request at 8 AM local time, `ExistingPeriodicWorkPolicy.KEEP`
  so re-running `ensureScheduled()` (called from `FriendMinderApplication.onCreate()`)
  doesn't reset an already-pending countdown. Deliberately not folded into
  `NotificationScheduler`/`WorkManagerNotificationScheduler` — that
  interface's slot/random-window model is specific to the user-configurable
  suggestion schedule; the birthday check is a single fixed job with no
  Phase 2 settings toggle.

The birthday check is unconditionally scheduled on every app launch — there's
no Phase 2 settings UI to opt in/out of it yet (that would be a
Designer/Publisher addition to `SettingsFragment`).

## Migration strategy

There is no destructive schema change in Phase 2, so there are no `Migration`
classes to write or test (the PRD's "Database Migrations" ask, reframed for a
SharedPreferences-based system rather than Room):

- Every Phase 2 repository is backed by its **own, new** SharedPreferences
  file (see the table above). None of them read or write any MVP prefs file
  (`friend_minder_prefs`, `friend_minder_cooldowns`, `friend_minder_settings`),
  except `CooldownRepository`'s two additive new keys within its existing file.
- A Phase-1-only install therefore has these new files simply absent, and
  every new repository already treats "file/key absent" as its documented
  empty/null default (`emptyList()`, `null`, `0`) — this was the actual
  compatibility contract to prove, not a data transform.
- `SchemaVersion` (new) adds an explicit version marker (`MVP = 1`,
  `PHASE_2 = 2`), written on every app launch by `FriendMinderApplication`.
  The MVP never had one; this is purely a hook for a *future* phase that
  does need a real migration path, not something Phase 2 itself consumes.
- **`Phase1CompatibilityTest`** (androidTest) is the migration test: it writes
  the MVP's exact JSON shape directly into `friend_minder_prefs` /
  `friend_minder_cooldowns` (simulating a real Phase 1 install with no Phase 2
  code ever having run), then verifies the MVP repositories still read it
  correctly and every new Phase 2 repository returns safe empty defaults for
  that same pre-existing contact.

## Room migration (FRM-81)

`data/room/` holds the Room side: `OutreachLogEntity`, `ContactGroupEntity` +
`ContactGroupMembershipEntity` (the M:N join table, `groupId` foreign-keyed
with `CASCADE` delete), `SpecialDateEntity`, their three DAOs, and
`AppDatabase` (version 1, `exportSchema = true` — see `app/schemas/`).
`RoomOutreachLogRepository`/`RoomContactGroupRepository`/`RoomSpecialDateRepository`
(in `data/storage/`, alongside the `SharedPrefs*` classes they replaced)
implement the same, unchanged repository interfaces — no caller outside
`ServiceLocator` needed to change.

**Migration mechanics** (`data/storage/RoomMigration.kt`): a one-time, blocking
copy (`runBlocking`, not fire-and-forget) in `FriendMinderApplication.onCreate()`,
gated by a new `SchemaVersion.ROOM_MIGRATION_V1` so it runs exactly once. It
reads old data through the existing `SharedPrefsOutreachLogRepository`/
`SharedPrefsContactGroupRepository`/`SharedPrefsSpecialDateRepository` classes'
own public methods (so it can't drift from their (de)serialization logic) and
writes it into Room via the DAOs. **The old SharedPreferences data is
deliberately never deleted** — kept as a rollback safety net. It runs before
`ServiceLocator.init()` so nothing can read from the (possibly still-empty)
Room database before migration completes.

**Build wrinkle worth recording:** KSP (Room's annotation processor) does not
support AGP 9's built-in Kotlin support, and the classic
`org.jetbrains.kotlin.android` plugin isn't compatible with AGP 9's new DSL
either (`BaseExtension` cast failure). `gradle.properties` sets
`android.builtInKotlin=false` and `android.newDsl=false` — AGP's own
documented bridge — until KSP adds built-in-Kotlin support upstream. This is
why this migration's PR touches Gradle plugin wiring at all, despite being
otherwise a pure storage-layer change.

## Testing

- **Unit tests** (`app/src/test`, plain JUnit, no Android/mocking
  dependency — matches the MVP's `SuggestionSelectorTest`/`SlotSchedulingTest`
  style): `StatisticsCalculatorTest` — 12 cases covering `daysSince`,
  `streak` (including the exact grace-period boundary), `reachRate`
  (including the >100%-clamped case), and `median` (empty/odd/even). All run
  in milliseconds, well under the "<100ms per contact on 50-contact list"
  target.
- **Integration tests** (`app/src/androidTest`, `AndroidJUnit4`, run against
  real `SharedPreferences` on a device/emulator — this app has no
  Robolectric/Mockito dependency, so anything needing a real `Context` is an
  instrumented test rather than a JVM unit test): `SharedPrefsContactGroupRepositoryTest`
  (CRUD + M:N membership) and `Phase1CompatibilityTest` (see above).
- **Room tests** (FRM-81, `app/src/androidTest/.../data/room/` and
  `RoomMigrationTest`): `ContactGroupDaoTest` (including the CASCADE-delete
  behavior), `SpecialDateDaoTest` (`replaceContactsSourced` atomicity),
  `OutreachLogDaoTest`, using `Room.inMemoryDatabaseBuilder`; `RoomMigrationTest`
  verifies the migration copies data correctly, is idempotent, and never
  deletes the old SharedPreferences data.
- Both `./gradlew testDebugUnitTest` and `./gradlew compileDebugAndroidTestKotlin`
  pass; `./gradlew assembleDebug` and `./gradlew detekt` are clean. Running
  the androidTest suite itself needs a connected device/emulator, which
  wasn't available in this environment — Publisher's CI pipeline
  (`claude/ci-cd-adoption-plan.md`) is the right place to execute
  `connectedDebugAndroidTest`.

## What's explicitly out of scope here (Designer/Publisher's job)

- Any UI: `DashboardFragment`, `GroupsFragment`, `ContactDetailFragment`
  updates, `OutreachLogDialog`, settings toggles.
- Wiring `SmsLaunchActivity` to call `OutreachLogService.logOutreach` (see
  "Integration point for Publisher" above).
- A Phase 2 settings toggle for the birthday check (currently always-on).
- Contact photo loading, avatar fallback colors, animations (PRD §6.6) — all
  visual, per the Designer/Publisher split in `claude/agents-phase-2.md`.

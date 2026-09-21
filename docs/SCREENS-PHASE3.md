# Friend-Minder — Phase 3 Screen Specs

**Owner:** Designer-P3 · **Epic:** [FRM-93](https://chefcai.atlassian.net/browse/FRM-93)
**Depends on:** `DESIGN-SYSTEM-PHASE3.md` — every token referenced here is defined there.
**Supersedes:** `docs/SCREENS-PHASE2.md` — delete it in the same PR.

| § | Screen | Ticket | Status |
|---|---|---|---|
| 1 | Home (root) | [FRM-99](https://chefcai.atlassian.net/browse/FRM-99) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 2 | Bottom navigation | [FRM-100](https://chefcai.atlassian.net/browse/FRM-100) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 3 | Home empty state | [FRM-107](https://chefcai.atlassian.net/browse/FRM-107) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 4 | Overall History | [FRM-101](https://chefcai.atlassian.net/browse/FRM-101) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 5 | Groups / Group Detail | [FRM-103](https://chefcai.atlassian.net/browse/FRM-103) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 6 | Contact Detail | [FRM-103](https://chefcai.atlassian.net/browse/FRM-103) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 7 | Settings / Advanced | [FRM-103](https://chefcai.atlassian.net/browse/FRM-103) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 8 | Dialogs | [FRM-103](https://chefcai.atlassian.net/browse/FRM-103) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 9 | Add-contact flow | [FRM-102](https://chefcai.atlassian.net/browse/FRM-102) | ✅ **Approved 2026-09-21 — cleared for Publisher** |
| 10 | Bulk removal (Home selection mode) | [FRM-112](https://chefcai.atlassian.net/browse/FRM-112) | ✅ **Approved 2026-09-21 — cleared for Publisher** |

Current-state reference screenshots and UI dumps for every screen below are in
`~/shared/FRM/handoff/designer/final/current-state/` on `painisbeautiful`.

---

## 1. Home — FRM-99

**Replaces:** `DashboardFragment` as the app's root, and absorbs the contact-list role of the old `HomeFragment` setup hub. Current state: `01-home-current.png` (Dashboard) and `03-setup-hub.png`.

### 1.1 Structure, top to bottom

```
┌──────────────────────────────┐
│  [ status bar — #1F817D ]    │  system inset, same fill, no seam
│                              │
│  Friend-Minder          ⋯    │  72dp header, 30sp/400 white, 24dp gutter
├──────────────────────────────┤  ← flat colour boundary. no shadow, no divider.
│  ◯  Alice Johnson       (5)  │  76dp row — filled badge = streak
│      3 days ago              │
│  ────────────────────────    │  1dp #CFE7EA, inset 24dp both ends
│  ◯  Amir Johnson        ( )  │  open ring = no streak
│      Today                   │
│  ────────────────────────    │
│  …                           │
│                          ⊕   │  56dp FAB, 24dp right, 16dp above nav
├──────────────────────────────┤  1dp #CFE7EA
│     ◻       ▦        ⚙       │  64dp bottom nav, icon-only
└──────────────────────────────┘
```

### 1.2 Header

Per DESIGN-SYSTEM §5. Title is the literal string **"Friend-Minder"**, matching the mockup — not "Home" and not "Dashboard". Home is the root, so there is **no back arrow, ever**.

No overflow menu. The two actions that live in today's Dashboard toolbar both move: the people icon (*Edit Friends*) is superseded by the FAB and the add-contact flow (FRM-102), and the gear (*setup hub*) is superseded by the Settings nav destination.

### 1.3 The list

- **Every tracked contact**, not a filtered subset. There is no "Needs attention" section and no urgency tone — the last-touch string carries that, in words (see §1.5).
- **Sorted alphabetically** by display name, using a locale-aware, case-insensitive `Collator` — not `String.compareTo`, which sorts "ashley" after "Zoe" and mis-orders accented names.
- Row height **76dp**, growing if text wraps (it should not — see 1.6).
- Layout across the row: 24dp gutter → 48dp avatar → 16dp → flexible text block → 12dp → 28dp badge → 24dp gutter.
- Text block: contact name in Row Primary (17sp/400, `fm_ink`); beneath it the last-touch string in Row Secondary (14sp/400, `fm_ink_dim`).
- Divider between rows only — **not after the last row**, and not between the header and the first row.
- Row tap → Contact Detail, shared-axis Z, 250ms.
- **Row long-press enters selection mode** for bulk removal — see §10. (Amended 2026-09-21; this line previously read "nothing in v1". Cai's proposal, and it is better than what it replaced.)
- No swipe actions, and no context menu.
- List bottom padding **88dp** so the FAB never permanently occludes the final row.
- No A–Z fast-scroll index and no sticky letter headers. Both add density for a list that is, for most users, under 40 rows. Revisit only if test users ask.

### 1.4 The last-touch string

The mockup is internally inconsistent here — it shows both "14 days ago" and "1 week ago". One ladder, applied strictly:

| Elapsed | String |
|---|---|
| never logged | `Never` |
| same calendar day | `Today` |
| previous calendar day | `Yesterday` |
| 2–13 days | `{n} days ago` |
| 14–27 days | `{n} weeks ago` (floor) |
| 28–364 days | `{n} months ago` (floor) |
| 365+ days | `Over a year ago` |

Calendar-day based, not elapsed-hours based: something logged at 11pm yesterday reads "Yesterday" at 1am, not "2 hours ago".

### 1.5 The status badge — **revised 2026-09-21, two states**

| Condition | Badge | `contentDescription` |
|---|---|---|
| Streak ≥ 1 | 28dp `#1F817D` filled circle, white numeral (`9+` above 9) | "{n} day streak" |
| Everything else | 28dp circle, no fill, 2dp `#1F817D` ring | "No streak, last contact {last-touch string}" |

**Overdue is gone as a list concept.** Nothing on Home distinguishes a contact who is two days past their window from one who is a year past. That is deliberate, and it holds because of how this product actually works: the daily notification picks the person for you. Home is a reference surface, not a triage queue — so a per-row urgency tone was solving a problem the product does not have, and it was competing with the last-touch line, which says `Over a year ago` far more precisely than any colour could.

It also means the badge column is now genuinely quiet: a scatter of open rings with a few filled ones, rather than a column of competing tones.

**One consequence to be aware of, not a blocker:** with alphabetical sort and no urgency tone, Home no longer surfaces *who is most overdue* at a glance — that job now belongs entirely to the daily notification and, for a deliberate review, to Overall History (FRM-101). If test users report they want to triage from Home, the fix is a sort control, not a badge tone.

### 1.6 Long names and long strings (PRD edge case)

The name/last-touch block is the only flexible element. Avatar (48dp) and badge (28dp) are fixed and never shrink.

- Name: single line, `ellipsize=end`, `layout_weight=1`.
- Last-touch: single line, `ellipsize=end`.
- The badge is laid out **before** the text block in the constraint chain so the text yields first — the current build does the reverse in places, which is how a long name can push a trailing control off-screen.
- Verify at 200% font scale and at 320dp width ("Donovan Arthen" + "Over a year ago" + a `9+` badge is the worst realistic case).

### 1.7 Add-contact entry point — **Designer's call, per PRD §8**

**Decision: a 56dp FAB, bottom-right, 24dp from the right gutter, 16dp above the bottom nav.**

Reasoning, and the case against it, since this is the one placement question the PRD explicitly left open:

- Adding people is the only create action on Home, and on a new install it is the *only* thing there is to do — it should not be hidden in the hardest-to-reach corner of a 6.7" phone.
- It is the one action that must remain reachable while scrolling a 45-row list. A header icon scrolls out of the thumb's reach; a FAB does not.
- The objection is real: a floating element in a design whose thesis is "nothing floats". Three mitigations — it is a circle rather than a box, it is the *only* elevated thing in the app (DESIGN-SYSTEM §4.4), and the list carries 88dp of bottom padding so it never sits permanently over content.
- The mockup shows no add affordance at all, so it is not evidence against a FAB. It simply never depicted this state.

**Alternative if Cai prefers it:** a white `+` glyph as a header action at the top right. Flatter and closer to a literal reading of the mockup; worse reach. Say the word on FRM-99 and the spec changes to that — it is a one-line change here and a small one for Publisher.

FAB tap → the rebuilt add-contact flow (FRM-102). `contentDescription`: "Add a contact to track".

**Where you add more contacts later — the full lifecycle, since it spans two specs:**

| List state | Entry point |
|---|---|
| Empty (first launch) | The **"Add someone"** button in the empty state (§3). The FAB is hidden here so there aren't two identical controls on a nearly blank screen. |
| One or more contacts | The **FAB**, bottom-right, on every scroll position. This is the permanent, only entry point from that moment on. |

So the empty state's button is a one-time stand-in that hands off to the FAB. There is no third path and no hidden one: the old toolbar people-icon → *Edit Friends* route is retired with the Dashboard, and *Edit Friends* itself is absorbed into the FRM-102 flow.

Removing a tracked contact has **two** paths, and neither is a swipe. One at a time, it lives in Contact Detail (§6.3). Several at once, it is Home's long-press selection mode (§10). There is no swipe-to-delete and no long-press *menu* — long-press enters selection mode, which is a state, not a popup.

---

## 2. Bottom navigation — FRM-100

**Current state:** there is no bottom nav. Navigation today is two toolbar icons on the Dashboard plus a chevron-list "setup hub" (`03-setup-hub.png`). All of that is retired.

### 2.1 Spec

- **Exactly three destinations, icon-only**, in this order: **Groups · Overall History · Settings**. Text labels were weighed against the glyphs on GH #119 and rejected: the bar stays wordless at 64dp, and the Groups glyph was redrawn instead (DESIGN-SYSTEM §6.9).
- Height **64dp** plus the bottom system inset (the bar's fill extends behind the gesture bar; its content does not).
- Ground `fm_surface` `#FFFFFF`, a 1dp `fm_divider` `#CFE7EA` hairline along the top edge, **0dp elevation**.
- Each item occupies one third of the width, with its 48dp touch target centred in that third.
- Glyph **28dp**.
  - Inactive: outline variant, `fm_ink_dim` `#4C6A6E` — 5.84:1 on white.
  - Active: **filled** variant, `fm_primary` `#1F817D` — 4.67:1 on white.
- **No Material 3 active-indicator pill.** The active state is carried by colour *and* by the outline→filled shape change, which is what keeps it non-colour-dependent. A pill would reintroduce exactly the boxing this phase removes.
- No labels, no badges, no dots in v1. Badge treatments are FRM-109 and only if one emerges naturally from a later proposal.

| Slot | Icon | `contentDescription` / `tooltipText` |
|---|---|---|
| 1 | Three people — one in front, two behind (DESIGN-SYSTEM §6.9) | "Groups" |
| 2 | Bar chart — three rounded bars (DESIGN-SYSTEM §6.9) | "Overall History" |
| 3 | Gear, inner circle centred at 12,12 (DESIGN-SYSTEM §6.9) | "Settings" |

### 2.2 Where Home lives — the asymmetry, called out

Three nav slots and four screens. Home is the root and is deliberately **not** a nav destination, which means Home needs a way back.

**Spec:** the bottom bar is present on all four screens so any destination can be reached from any other. Groups, Overall History and Settings each carry a **back arrow in the header** returning to Home, and system back from any of the three also returns to Home. Home itself never shows a back arrow and never shows a selected nav item — when you are on Home, all three nav glyphs are in their inactive state.

This reads the mockup the way it appears to be drawn: a bar of three entry points sitting at the bottom of Home, rather than a four-tab tab-bar with one tab missing.

**Flagging it explicitly for sign-off:** "all three inactive while on Home" is unusual, and the alternative — making Home a fourth nav item — is a clean, conventional solution that costs one PRD decision. I am not recommending it (the PRD's "exactly three" is a deliberate simplification and four icon-only tabs is measurably harder to learn), but it is the obvious counter-proposal and Cai should reject it knowingly rather than by omission.

### 2.3 Back-stack rules

- Home is the start destination and is never added to the back stack twice.
- Switching between nav destinations replaces rather than stacks — the back stack never grows past `Home → {destination} → {detail}`.
- System back from Contact Detail returns to wherever it was opened from (Home, or a Group Detail member list).

---

## 3. Home empty state — FRM-107

**Current state:** the app has no Home empty state; the nearest equivalent is the Groups empty state (`04-groups.png`), which is a 96dp-gap layout with a pill button and reads acceptably but sits on the wrong palette.

**Zero tracked contacts** — the state every new install starts in, so it is the first screen most users ever see.

- Header renders normally (teal, "Friend-Minder"). The bottom nav renders normally. Only the list area is replaced.
- Centred in the content area, per DESIGN-SYSTEM §6.7:
  - 96dp outline glyph — two overlapping person circles — `fm_ink_dim` at 40% opacity.
  - 32dp gap.
  - Headline, Screen Heading 22sp/500 `fm_ink`: **"No one here yet"**
  - 8dp gap.
  - Body 16sp/400 `fm_ink_dim`, max 2 lines, 280dp max width: **"Add a few people you'd like to stay in touch with. Friend-Minder will nudge you, one at a time."**
  - 48dp gap.
  - Filled button, 56dp, `fm_primary`: **"Add someone"** → the add-contact flow.
- The FAB is **hidden** in this state. Two buttons doing the same thing is a worse first screen than one, and the FAB returns as soon as the list is non-empty.
- No illustration beyond the glyph, no colour, no card.

**Second empty state — all contacts filtered out** does not exist on Home, because Home has no filter. Search lives in the add-contact flow only.

---

## 4. Overall History — FRM-101

**Replaces `DashboardFragment`, which is deleted.** Current state: `12-dashboard-populated.png` (all five cards rendering, captured with seeded data) and `01-home-current.png` (the same screen with empty data, three cards).

### 4.1 What moves and what goes — the full accounting

The PRD names two things moving here. The real Dashboard has **five** cards, two of which the PRD never accounts for. Left undecided, deleting the Dashboard would have silently removed two live features.

| Dashboard card | Fate |
|---|---|
| Key metric — "You've reached out to N contacts this month" | **Moves here**, reworked from a sentence into a figure |
| "Your strongest connections" (top streaks) | **Moves here** — Cai's decision, 2026-09-21 |
| "Needs attention" | **Retired** (PRD §8) — the signal is the Home row badge |
| "Monthly outreach" chart | **Moves here, re-skinned only** — Cai's decision, 2026-09-21 |
| "Upcoming birthdays & dates" | **Retired as a cross-contact view** — Cai's decision, 2026-09-21 |

On that last one: **no data and no behaviour is lost.** Birthday and special-date notifications continue to fire unchanged, and each contact's Special Dates tab on Contact Detail is untouched. What goes is only the aggregated "what's coming up across everyone" list. Logged on FRM-105 as a deliberate removal so it does not read as an oversight later.

**Correction, 2026-09-21.** An earlier revision of this section claimed the chart showed no data and had no axis labels, and parked both on FRM-110. **Both claims were wrong** — see §4.5. FRM-110 is closed as invalid. Phase 3 applies tokens and removes the card; it does not touch the drawing or the query.

### 4.2 Structure

```
┌──────────────────────────────┐
│  [ status bar — #1F817D ]    │
│  ←  Overall History          │  72dp header, back arrow → Home
├──────────────────────────────┤
│  ┌────────────────────────┐  │
│  │  12                    │  │  the one #E6F5FB block on this screen
│  │  contacts reached      │  │
│  │  this month            │  │
│  └────────────────────────┘  │
│                              │
│  MONTHLY OUTREACH            │  section label
│  ▁ ▃ ▅ ▂                     │  160dp, bars on white, no card
│  ──────────────────────────  │  1dp baseline
│                              │
│  LONGEST STREAKS             │
│  ◯  Priya Sharma        (9+) │  identical to a Home row
│  ────────────────────────    │
│  ◯  Alice Johnson        (5) │
├──────────────────────────────┤
│     ◻       ▦        ⚙       │  slot 2 active, filled
└──────────────────────────────┘
```

### 4.3 Header and nav
Per DESIGN-SYSTEM §5. Title "Overall History", white back arrow at the 24dp gutter returning to Home, title starting at 72dp. No toolbar actions. Bottom nav present with **slot 2 active** — filled bar-chart glyph in `fm_primary`.

### 4.4 Key metric
The one `fm_surface_accent` `#E6F5FB` block permitted on this screen (DESIGN-SYSTEM §2.2). Full content width, 16dp radius, 24dp internal padding, 32dp below the header.

- **Figure:** the count alone, 48sp/56sp weight 500, `fm_heading` `#195556`.
- **Label:** beneath it, 4dp gap, 16sp/24sp `fm_ink_dim` — "contact reached this month" / "contacts reached this month" via a quantity string, not string concatenation.

A sentence inside a tinted box is not a metric — the number is the content, and on a screen whose job is "how am I doing" it should be the largest thing present. This is the one place in Phase 3 where type goes above 30sp.

**Accessibility:** the block carries one `contentDescription` — "{n} contacts reached this month" — so TalkBack reads a statement rather than a bare numeral followed by a fragment.

### 4.5 Monthly outreach

Section label "MONTHLY OUTREACH" (Section Label style, 32dp above). Chart sits **directly on the surface, no card**.

- Height **160dp**, up from today's 96dp.
- Bars `fm_primary`, 4dp radius on top corners only.
- 1dp `fm_divider` baseline across the content width. No gridlines, no frame.
- **`BarChartView` already draws persistent axis labels** ("3wk ago" … "This wk", from `DashboardChartCalculator.axisWeeksAgo`), added in GH #101 / PR #105. **Preserve them** and style them as Caption in `fm_ink_dim` per DESIGN-SYSTEM §6.8. They are not new work and must not be dropped in the re-skin.
- Bucket logic, bar count, scaling and the existing `contentDescription` contract are otherwise unchanged. Re-skin only, per Cai.

> **Correction, 2026-09-21 — I got this wrong the first time, twice.**
>
> I originally wrote that the chart "renders as a single solid teal rectangle with no axis, no baseline, no scale and no labels" and that its accessibility text "still reads `0, 0, …` after outreach is logged", and raised FRM-110 on that basis. Both observations were artefacts of how I captured, not defects:
>
> 1. **The "0, 0, …" reading was my own truncation.** `BarChartView.buildContentDescription` joins *every* bucket with ", "; my dump script cut each field at 24 characters, so I read the first two zero buckets and never saw the last one. The single bar visible on the right of `12-dashboard-populated.png` is the "This wk" bucket rendering the one outreach I had logged — the chart was correct, and the screenshot proves it.
> 2. **The missing axis labels were real, but already fixed.** PR #105 added them and merged at 2026-09-21T00:19Z — **26 minutes after** the `app-debug.apk` I tested was built (2026-09-20 19:53 ET). I was reading a stale build.
>
> The lesson for the rest of this phase: a `uiautomator` dump field is not the whole value, and an installed APK is not `main`. Both are now checked before any current-state claim.

### 4.6 Longest streaks
Section label "LONGEST STREAKS", 32dp above.

- Rows are **identical to the Home contact row** (§1.3): 76dp, 24dp gutter, 48dp avatar, 16dp gap, name over last-touch, 28dp filled streak badge at the trailing edge, 1dp `fm_divider` inset 24dp between rows and none after the last.
- Reusing the Home row verbatim is deliberate — same item layout, no new component, and the badge is already exactly the streak numeral this section is about.
- **Top 5 by streak length, descending.** Ties broken by name using the same locale-aware `Collator` as Home, so ordering is stable rather than arbitrary.
- Row tap → Contact Detail, shared-axis Z, 250ms — same as Home.
- **The whole section, label included, is hidden when no contact has a streak.** Never render a section header above nothing.

### 4.7 Empty state
When no outreach has ever been logged, the key metric, chart and streak sections are all replaced by a single empty state. Header and bottom nav render normally.

- 96dp outline chart glyph, `fm_ink_dim` at 40%.
- Headline: **"Nothing to show yet"**
- Body: **"Log an outreach or two and your history will build up here."**
- **No button** — an explicit deviation from DESIGN-SYSTEM §6.7. The only action that resolves this state lives on Home, and the header's back arrow already goes there; a second control pointing at the same destination is the same two-controls problem the Home empty state avoids by hiding its FAB.

### 4.8 Scrolling
The whole screen is **one** scroll container. The streak list is a static list inside it, not a nested scrolling `RecyclerView` — Phase 2 hit a real nested-scroll/footer bug (GH #72/#76) and this is the same shape of mistake.

---

## 5. Groups and Group Detail — FRM-103

Current state: `04-groups.png` (empty), `14-groups-populated.png`, `07-group-detail.png` (empty), `13-group-detail-populated.png`.

Both screens are already flat — no cards to remove. What they need is the Phase 3 palette, the standard row, and **one decision that propagates across the whole app**.

### 5.0 The cross-cutting decision: one "add" pattern

Today there are three different affordances for "add a thing to this list":

| Screen | Today |
|---|---|
| Home / Edit Friends | toolbar people icon |
| Groups | `+` in the header |
| Group Detail | full-width outlined button pinned at the bottom |

**Phase 3 uses a FAB on all three.** Same 56dp `fm_primary` circle, same position, same behaviour — and on each screen's empty state the FAB is hidden in favour of the empty state's own button, exactly as Home does (§3). One pattern, learned once.

The pinned outlined button on Group Detail goes: an outlined button is the lowest-emphasis button style in the system, which is the wrong weight for a screen's only action.

### 5.1 Groups

- Header: teal, title "Groups", back arrow → Home. **No header `+`** (see §5.0). Bottom nav present, **slot 1 active**.
- Rows **76dp**, up from 64dp, and structurally identical to a Home contact row:
  - 24dp gutter → **48dp circle filled with the group's colour** → 16dp → name (Row Primary) over member count (Row Secondary, "2 members" / "1 member" via a quantity string) → 24dp gutter.
  - The 16dp colour dot becomes a full 48dp circle. It occupies the avatar slot, so Groups and Home rows align to the same grid, and the group's colour finally has enough area to be identifiable rather than decorative.
  - `fm_group_color_7` (`#CCE0E9`, "Mist") is pale enough to disappear against white — it takes the same **1dp `fm_divider` hairline** the light avatar fills take (DESIGN-SYSTEM §4.3).
- **The trailing chevron is removed.** No other list in the app uses one, and a whole row is already the target.
- 1dp `fm_divider` between rows, inset 24dp, none after the last.
- Empty state per DESIGN-SYSTEM §6.7: "No groups yet" / "Groups let you set different check-in rhythms for different people." / **"Create a group"**. FAB hidden.

### 5.2 Group Detail

- Header: teal, group name as title, back arrow → Groups.
- **The header action changes from a gear to a pencil.** This is not cosmetic: the bottom nav now uses a gear for Settings, so a gear meaning "edit this group" is a direct collision introduced by FRM-100. Same applies anywhere else a gear currently means something other than Settings.
- Member rows: the Home contact row **minus the streak badge** — 76dp, 48dp avatar, name over last-touch. Tapping a row opens Contact Detail, same as everywhere else.
- **Removing a member is no longer a persistent `✕` on every row.** The trailing control becomes a 48dp remove button that acts immediately and raises a 4-second **Undo snackbar** — no confirmation dialog. Removing someone from a group destroys nothing (the contact, their history and their frequency are untouched), so a modal is friction for a reversible action; undo is the right weight.
- Add member: FAB (§5.0).
- Empty state: "No members yet" / "Add people to this group to give them a shared rhythm." / **"Add a contact"**. FAB hidden.

### 5.3 Existing group membership when adding a member (GH #95)

**Cai's decision, 2026-09-21: option 1.** Groups stay many-to-many; this is a visibility improvement, not a model change. A contact can be in Close Friends *and* Band, and the add-member picker says so rather than letting you find out later.

**Where.** Group Detail → FAB → the add-member picker (§5.0). Only there.

- **Contact Detail already solves this** — its GROUPS section (§6.2) shows every group the person is in, as chips, directly above the "+ Add" that opens the group sheet. Nothing to add.
- **Add-contact Step 2 (§9.4)** does not need it either: those contacts are brand new and belong to nothing yet.

**The treatment: the picker's secondary line carries membership instead of last-touch.**

Row shape is unchanged — 76dp, 48dp avatar, name at Row Primary, one secondary line at Row Secondary in `fm_ink_dim`. What changes is only what that second line says, and only in this picker:

| Contact's state | Secondary line |
|---|---|
| In no other group | *(blank — the line is omitted, the name centres in the row)* |
| In one other group | `In Close Friends` |
| In two | `In Close Friends and Band` |
| In three or more | `In Close Friends +2` |

**Why replace the line rather than add one.** A third line would push the row past 76dp and break alignment with every other contact list in the app. And last-touch is not what this screen is for: you are answering *"should this person be in this group"*, a question last-touch does not help with, while membership does. Each screen's secondary line should carry what that screen is about.

**Two things not to do, both of which are the obvious move:**

1. **No group-colour dots on these rows.** §6.2 uses an 8dp colour dot on Contact Detail's group *chips*, and copying that here puts two or three coloured dots on every row of a scrolling list — confetti, and it competes with the avatar for the same job. Plain `fm_ink_dim` text.
2. **Contacts already in *this* group are not listed at all.** Not greyed, not shown with a tick. A picker whose job is "add someone" should not display people there is nothing to do with; excluding them also removes any question about what tapping them means.

---

---

## 6. Contact Detail — FRM-103

Current state: `08-contact-detail.png`.

### 6.1 What is wrong today
It is not card-boxed, so the structural work is small — but the content is written as sentences where the rest of Phase 3 speaks in figures, and it carries a gear that now collides with the nav.

### 6.2 Structure

- Header: teal, **contact name as title**, back arrow. **No header action at all** — the gear ("Reminder Frequency") is removed, and frequency becomes a tappable row in the body (§6.4). One fewer icon, and no gear collision.
- Identity block, centred: 96dp avatar, 24dp gap, name at Screen Heading (22sp/500).
- **Stats become three figures, not three sentences.** Today: `Last contact: Never contacted` / `Streak: 0 days` / `Reach rate: 0%` — three full-width text lines. Phase 3: one row of three evenly-spaced stacks, each a 24sp/500 `fm_heading` figure over a 13sp `fm_ink_dim` label:

```
     12              5              40%
  outreaches     day streak     reach rate
```

  No boxes, no dividers — 32dp of air above and below. This is the mockup's vocabulary (numerals and badges) applied to the one screen that is entirely about one person's numbers. `Never` and `—` are the zero-state figures; never render "0 days" where "—" is truer.
- **GROUPS** section label, then chips per DESIGN-SYSTEM §6.4 — group colour as an 8dp leading dot, never as the chip fill — plus a trailing "+ Add" chip.
- **CHECK-IN FREQUENCY** section label, then a single tappable row: the **effective** interval and where it came from, with a trailing chevron, opening the frequency bottom sheet. This is what replaces the header gear. See §6.2a — the row shows the interval that will actually fire, not the one stored against this contact.
- Segmented control **History / Special Dates**: `fm_surface_sunken` track, selected segment `fm_primary_container` with an `fm_accent` label. Note this is a live instance of the DESIGN-SYSTEM §2.4 trap — `fm_primary` as the label here would fail AA on the container.
- The selected list beneath, flat rows, 1dp dividers inset 24dp.
- **No pinned primary action.** The "Log outreach" button is removed — manual outreach logging is deferred to a possible higher subscription tier (FRM-114 hides it behind a flag; FRM-115 is the Phase 4 backlog story). The screen's one control is the remove FAB in §6.3.

**Why there is no pinned button here any more.** The earlier rule — a **FAB** where the screen is a list you extend (Home, Groups, Group Detail), a **pinned button** where the screen is about one entity with one dominant action — assumed Contact Detail *had* a dominant action. With logging deferred, it does not. Contact Detail is a record you open to read, not a form you open to submit. Do not reach for a pinned button to fill the space the old one left.

**Nothing else on this screen changes.** The History tab still reads from `OutreachLogService.getHistory()`, and the three figures at the top still count outreaches logged automatically when a nudge results in a sent SMS. Removing the button costs the screen no data.

### 6.2a The check-in frequency row shows the *effective* interval (GH #121)

Per-group intervals (GH #121) mean a contact's own setting is no longer the whole answer. Cai's rule: **the effective interval is the minimum of every explicitly-set interval that applies** — the contact's own, any group's, and the global default. Not a contact-beats-group-beats-global chain.

So a user can set this contact to 7 days, reopen the screen, and see 3. Unexplained, that is a bug report. The row therefore always names its source when the source is not this contact:

| Which interval wins | Row reads |
|---|---|
| The contact's own | `Every 3 days` |
| The global default (contact unset, no group) | `Every 3 days · default` |
| One group | `Every 3 days · from Close Friends` |
| Two or more groups tie at the minimum | `Every 3 days · from 2 groups` |
| A group ties with the contact's own value | `Every 3 days` — the contact's own wins the attribution |

The suffix is Row Secondary weight in `fm_ink_dim`, on the same line as the value, separated by ` · `. It is not a second line and not a chip.

The last row of that table is a rule, not an edge case: when the numbers are equal, attribute to the setting **the user made here**, because the alternative tells someone their own choice did not count when in fact it did.

**The sheet has to say this too, at the moment it matters.** Tapping the row opens the §8.4 picker, which sets *this contact's own* value — and choosing anything longer than a group's interval will not change when the nudge fires. Say so in the sheet rather than letting the row contradict the choice a second later: beneath the sheet title, one line of Body in `fm_ink_dim` —

> *Close Friends checks in every 3 days, so anything longer won't change when you're nudged.*

Shown only when a group interval is shorter than at least one selectable option; named group is the shortest one. This is the honest version of a constraint the user cannot otherwise see, and it costs one line.

**What this needs from the service.** The frequency query returns the source alongside the number, not just the minimum — raised with Architect-P3 on FRM-97. The fragment must not re-derive the winner itself; two implementations of a precedence rule is one more than is safe.

---

### 6.3 Stop tracking a contact

This is the **single-contact** removal path. Home's long-press selection mode (§10) is the bulk one; they share the confirmation copy below and nothing else.

**The control is a FAB, in the same position as Home's add FAB** — Cai's direction, 2026-09-21: add and remove sit in the same place on their respective screens, so the gesture transfers.

- 56dp circle, `fm_error` fill, white 26dp **person-minus** outline glyph, 3dp elevation.
- 24dp from the right gutter, **16dp above the bottom system inset**. Contact Detail is a pushed screen with **no bottom nav**, so that 16dp measures off the inset, not off a bar — do not copy Home's "16dp above the nav" literally.
- `contentDescription`: "Stop tracking {name}".
- The scroll container carries **72dp** bottom padding (56dp FAB + 16dp) with `clipToPadding="false"`, so the FAB never sits permanently over the last history row. This is the §1.3 rule at this screen's measurements; it is also the defect filed as GH #124 on Home, so it is a known way to get this wrong.

This FAB and Home's are the only elevated components in the app (DESIGN-SYSTEM §4.4). They are never on screen together.

**The mis-tap objection, answered rather than waved off.** A destructive control sitting where muscle memory says "add" is a real risk, and it deserves a real answer. Four things separate them: the fill is `fm_error`, not `fm_primary`; the glyph is a person-minus, not a plus; the screen carries a 96dp avatar and the contact's name in the header, so the context is unmistakable; and completing the action takes three deliberate steps with the cost stated in words. That is the same structure as §10, where Cai was right to prefer four deliberate steps over a buried settings screen.

Unlike removing a group member, this **does** destroy data — the outreach history goes with it — so it gets a confirmation bottom sheet that says so plainly, with the count: *"This will delete {n} logged outreaches. {Name} stays in your phone's contacts."*

**That second sentence is load-bearing.** Removing a tracked contact never touches the entry in the phone's own contacts database, and the one moment a user needs to be told so is the moment they are being asked to confirm a deletion. Keep it.

---

## 7. Settings and Advanced — FRM-103

Current state: `10-settings.png`, `11-advanced-settings.png`.

### 7.1 Two pattern changes that apply to every row

1. **Switches, not checkboxes.** Both screens use `CheckBox` for boolean settings. A checkbox is a form-selection control; a switch is the settings idiom, reads as immediate rather than pending, and gives a far larger target.
2. **Label first, description beneath.** Advanced currently puts a three-line paragraph *above* its control, so the explanation is read before it is clear what it explains. Invert it: label at Row Primary, description at Row Secondary underneath, control trailing. Every settings row in the app, same shape.

### 7.2 Settings

- Header: teal, "Settings", back arrow → Home. Bottom nav present, **slot 3 active**.
- **REMINDER TIME** — segmented control (Fixed time / Random window) replacing the radio pair, then the time as a tappable row with the value and a chevron.
- **REMINDERS** — "How many reminders per day" and "Don't repeat a contact within" become tappable rows showing their current value with a trailing chevron, opening a bottom-sheet picker. Both are bare `Spinner`s today, which look like unstyled form controls dropped into a settings screen. This also matches the frequency row on Contact Detail, so "a setting with a value" looks the same everywhere.
- **MESSAGE TEMPLATES** — switch for "Include a pre-filled message", then the text area per DESIGN-SYSTEM §6.3, with helper text *beneath* the field rather than floating on its border. Counter ("3 templates") stays, as Caption, right-aligned.
- **The notification preview block moves here from the retired setup hub**, and becomes this screen's one `fm_surface_accent` block. Its dashed border goes — a dashed outline appears nowhere else in the app and reads as a dropzone.
- **ADVANCED** — a normal row with a chevron in its own section, not a bare text link floating at the bottom.
- **There is no "Save Settings" button.** See §7.4.

### 7.3 Advanced

Same row shape. The two settings become switches with their explanations beneath the labels.

The SMS-permission message is currently three lines of `fm_error` red. **It should not be red.** "Permission isn't granted yet" is an informational state, not an error — nothing has gone wrong and the user has not failed at anything. It becomes Row Secondary `fm_ink_dim` text beneath the switch. Reserve `fm_error` for a state the user must act on to recover from.

### 7.4 Automatic save, with a visible confirmation — **decided by Cai, 2026-09-21**

**The "Save Settings" button is deleted. Every setting persists the moment it changes.** A Save button on a settings screen is a reliable source of silent data loss — someone flips a switch, backs out, and assumes it stuck.

Auto-save on its own has the opposite failure: nothing tells you it worked. So every persisted change raises a transient **"Saved" indicator**.

**The indicator**
- A pill, 32dp tall, fully rounded, **`fm_accent` `#155D5B` fill with a white 16dp check glyph and a white 14sp/600 "Saved" label**, 16dp horizontal padding.
- Horizontally centred, **overlaid** 12dp below the header — absolutely positioned, so nothing on the screen shifts when it appears or leaves.
- 150ms fade in, 1.6s hold, 200ms fade out.

**Why `fm_accent` and not the obvious `fm_primary_container`.** A pale container pill measures **1.35:1** against white — no boundary, so it wouldn't read as a distinct object at all. And `fm_primary` as a label on that container measures **3.46:1**, a straight AA fail — the DESIGN-SYSTEM §2.4 trap, live. A dark fill with white text (**7.64:1**) is unambiguous on white and reads as transient notification rather than page chrome.

**Debounce: 600ms after the last change.** Flipping three switches in a row, or typing into the templates field, produces **one** indicator, not a stream of them. Without this the templates field would flash on every keystroke.

**Scope.** Settings and Advanced only. Elsewhere the changed value in situ is already the confirmation — Contact Detail's frequency row redraws with its new value, a group chip appears, a member row disappears with an Undo snackbar. Adding a pill to those would be noise on top of feedback that already exists.

**Accessibility.** One `announceForAccessibility("Saved")` per debounced save. The pill itself is `importantForAccessibility="no"` so TalkBack doesn't announce it twice. With system animations disabled it appears and disappears without the fades, holding for the same 1.6s.

### 7.5 The missing-contacts banner — deferred, [FRM-111](https://chefcai.atlassian.net/browse/FRM-111)

Cai's call, 2026-09-21: the banner does **not** get a designed home in Phase 3. Settings would bury something actionable and time-sensitive; the top of the Home list would mean amending an already-signed-off spec mid-implementation.

**Updated 2026-09-21 — it is not lost after all.** Publisher-P3 (PR #108) renamed the retired setup hub `HomeFragment` → `LegacyDiagnosticsFragment`, dropped its three navigation shortcut rows (now redundant against the bottom nav and Home's FAB), and wired what remained — the missing-contacts banner, the notifications-disabled banner, and the test-notification preview — under a plain **"Notifications & diagnostics"** row in Settings.

That is a better outcome than the spec called for, and the right instinct: keep working functionality reachable rather than delete it for want of a design. It means Phase 3 ships **with** missing-contact surfacing, just not yet in a Phase 3 treatment.

**Two consequences to reconcile when FRM-103 is implemented:**
1. §7.2 places the notification preview **inline on Settings as that screen's one `fm_surface_accent` block**. Publisher currently has it one level down, inside the diagnostics fragment. Either is defensible; they should not both ship. My preference stands — inline on Settings, and the diagnostics fragment keeps only the two banners — but this is worth a deliberate decision rather than a collision.
2. FRM-111 narrows accordingly: it is no longer "reintroduce a lost banner" but "give the surviving banner a Phase 3 treatment and a considered home". The machinery is already wired, so nothing needs rescuing from deletion.

---

## 8. Dialogs — FRM-103

Current state: `05-dialog-group-edit.png`, `09-dialog-outreach-log.png`, plus the *Add special date* dialog.

### 8.1 One shape for all of them
Every dialog is a bottom sheet per DESIGN-SYSTEM §6.6: `fm_surface` ground, 16dp top corners, 24dp gutter, a 4×32dp `fm_divider` drag handle centred 12dp from the top, scrim `#1B2E30` at 40%.

- **Add special date is an `AlertDialog` today** while every other dialog is a bottom sheet. Convert it.
- **Dismissal is the handle, the scrim and back — nothing else.** *New Group* currently offers an `✕` **and** a Cancel button **and** the scrim: three ways out competing with one way forward. Both the `✕` and the Cancel go; the filled primary becomes the only button.

### 8.2 The colour-swatch overflow (real bug)
Eight 48dp swatches plus seven 10dp gaps need 454dp; a 360dp screen minus 24dp gutters leaves 312dp. The eighth swatch ("Midnight") is clipped to 44px of its 126px width — confirmed on device.

**Fix: two rows of four.** 4 × 48dp + 3 × 24dp = 264dp, comfortably inside 312dp, with 16dp between rows.

**Selected state: a 2dp `fm_ink` ring with a 3dp gap outside the swatch** — not a checkmark drawn inside it. A check would need per-swatch light/dark pairing exactly like the avatar initials; a ring outside the circle is legible against every fill without any pairing table.

### 8.3 Log Outreach
- **Default the Type to SMS.** Today the Save button is disabled until a Type is picked and nothing on screen says why — a dead primary button with no explanation. SMS is the common case; defaulting removes a required choice *and* the dead-button state.
- Date and time become tappable rows with values and chevrons, matching every other "setting with a value" in the app.
- Note field per §6.3.
- Single filled primary, "Log it".

### 8.4 Frequency and group-assignment sheets
Same shape. Both are reached from tappable value rows (Contact Detail §6.2, Settings §7.2) and both return a single value, so each is a list of options with the current one marked — no separate confirm button; tapping an option selects and dismisses. `ValuePickerDialogFragment` is the one component; every "setting with a value" in the app goes through it.

**"Custom…" — the one row that does not select and dismiss (GH #98).**

Presets cover the common cases and nothing else, so each interval picker carries a final row reading **`Custom…`**, set apart from the options above it by a 1dp `fm_divider` hairline inset 24dp. It has no radio mark, because it is not a value.

Tapping it **replaces the option list in place** — the same sheet, no second sheet stacked on the first:

- Section Label **`DAYS`**, then a single numeric input per DESIGN-SYSTEM §6.3, `inputType="number"`, focused with the keyboard raised.
- A filled primary, **`Set`**, full width. This is the one picker sheet that has a button, and it has one because typing a number has no natural moment of commitment the way tapping a row does.
- Back, the handle and the scrim return to the option list rather than dismissing the sheet, so a mis-tap on `Custom…` costs one gesture.
- Out-of-range or empty → `Set` is disabled and the field shows its helper text beneath. Never a disabled button with no explanation — that is the §8.3 mistake.
- On `Set`, the sheet dismisses and the value row shows the custom value exactly as any preset would. A custom value already in force appears as a marked option at the top of the list next time the sheet opens, so it is one tap to keep.

**Build this once.** The global cooldown (§7.2), the per-contact frequency (§6.2), the add-contact bulk default (§9.3) and any per-group interval (GH #121) are the same control asking the same question. Three separate custom-value flows is the outcome to avoid.

---

## 9. Add-contact flow — FRM-102

Current state: `02-manage-friends.png` (*Edit Friends*). Replaces `FriendListFragment` in both of its modes.

### 9.0 First launch — **onboarding is dropped** (Cai, 2026-09-21)

`MainActivity` currently routes on *"do you have friends yet"*: with zero tracked contacts it launches straight into `FriendListFragment(isOnboarding = true)`, which on save hands off to `SettingsFragment(isOnboarding = true)`. **It never shows Home.** Which meant the Home empty state approved on FRM-107 could not be reached at all — a conflict nobody spotted, because the PRD never mentions onboarding.

**Resolved: the app always launches to Home.** Zero contacts shows the FRM-107 empty state; "Add someone" starts this flow.

- `MainActivity`'s `hasFriends` branch goes — one start destination, always.
- **`isOnboarding` is retired from both `FriendListFragment` and `SettingsFragment`**, including `SettingsFragment`'s onboarding-only hiding of `advancedRow`.
- The guided walk to Settings is lost. That is acceptable: the defaults are already sensible (8:00 AM, one per day, every 3 days), and Settings is now one tap in the bottom nav instead of buried behind a setup hub — more discoverable than today, not less.

### 9.1 Shape: two steps, one flow

Entered from the Home FAB, or "Add someone" on the Home empty state.

**Both steps are full screens, not dialogs.** This is deliberate: the Phase 2 nested-scroll/footer bug (GH #72/#76) was a *dialog* problem — a scrolling list inside a sheet with a pinned footer. A full screen with one `RecyclerView` and a footer outside it cannot reproduce that class of bug.

### 9.2 Step 1 — Choose people

- Header: teal, "Add contacts", back arrow (cancels the flow).
- Search field directly beneath, per DESIGN-SYSTEM §6.3, 24dp gutter.
- List: **untracked device contacts only** — already-tracked people are not shown (see §9.5). Alphabetical by the same locale-aware `Collator` as Home.
- Rows 76dp: 48dp avatar → name (Row Primary) over phone number (Row Secondary; "3 numbers" when the contact has several) → 28dp selection control.
- **The selection control reuses the Home badge geometry exactly**: unselected is a 2dp `fm_primary` open ring, selected is a filled `fm_primary` circle with a white check. Same 28dp slot, same two-state filled/open vocabulary the whole app now speaks — nothing new to learn, and it needs no new component.
- Footer: pinned bar on `fm_surface` with a 1dp `fm_divider` top edge — count on the left, filled **"Next"** on the right.
  - At zero selected the button is disabled **and the left text says "Select someone to continue"**, so the disabled state explains itself. (The same defect as Log Outreach's dead Save button, §8.3 — worth not repeating here.)
- List bottom padding = footer height + 16dp, so the last row is never trapped under the footer.

**Multi-number contacts** (FRM-28 / GH #39 — must survive the rebuild): selecting a contact with more than one number opens a **"Which number for {name}?"** bottom sheet, numbers listed primary-first, converted from today's `AlertDialog` per §8.1. Choosing a number completes the selection; deselecting and reselecting the row is how you change it.

### 9.3 Step 1 — states that must not be lost

The current screen handles four states that a rebuild will silently drop if they aren't written down:

| State | Treatment |
|---|---|
| Permission not yet requested | Request on entry |
| Denied once | Empty-state shape (§6.7): "Friend-Minder needs your contacts", body — *it reads names and numbers only, and nothing leaves your phone* — filled **"Allow access"** |
| Permanently denied | Same shape, body explains it must be enabled in system settings, filled **"Open settings"**. **Re-check on resume** — the current code does this and it must survive |
| No contacts have phone numbers | Empty-state shape: "No contacts with phone numbers" |

### 9.4 Step 2 — Set them up

This is where the PRD's "group and frequency assignment at add-time" is satisfied.

- Header: teal, "Set up", back arrow → step 1 **with selections intact**.
- Summary: the selected avatars in a row (up to six, then "+N"), with "3 people" beneath.
- Two tappable value rows, identical in shape to Settings' value rows (§7.2):
  - **Check in every** — defaults to the app default, "3 days" → bottom-sheet picker.
  - **Groups** — defaults to "None" → bottom-sheet multi-select of existing groups, plus "New group".
- One line of Body beneath: *"You can change either of these for one person later."* — this is a bulk default, not a per-person commitment, and saying so prevents people labouring over it.
- Pinned filled button: **"Add 3 people"** (quantity string).

**Why two steps rather than folding the settings into each row.** Choosing people and configuring them are different tasks. Inline configuration would mean three taps per person and a row dense enough to undo the whole simplification directive. Two steps keeps each screen doing one thing, and accepting the defaults is a single tap.

On completion: return to Home, new contacts appear in the alphabetical list, **snackbar "3 people added" with Undo**.

### 9.5 PRD open question #4 — answered

> *Should the rebuilt flow reuse the native contact picker (`ContactsContract`) underneath, or does "simplify the picker" mean replacing that mechanism too?*

**The question rests on a false premise, and the answer is: the query stays, the UI is rebuilt.**

The app has never used the system picker UI. `FriendListFragment` reads contacts through `ContactsLoader` — a direct `ContactsContract` query — and renders its own list, search and selection. So "simplify the picker" cannot mean "replace `ContactsContract`"; there is no system picker UI to remove.

Keeping the query is also the only option that preserves three things a system picker cannot give us: multi-number resolution (FRM-28), filtering out contacts with no phone number at all, and multi-select in one pass.

### 9.6 Verification (PRD edge case)

Verify on-device **with 45+ contacts**, not a small test set: scroll to the end of step 1, confirm the footer never overlaps the last row, confirm search filters without losing selections, and confirm selections survive back-navigation from step 2.

---

## 10. Bulk removal — selection mode on Home ([FRM-112](https://chefcai.atlassian.net/browse/FRM-112))

**Revised 2026-09-21 on Cai's proposal.** This replaces an earlier version that put bulk removal behind a *"Manage tracked contacts"* row in Settings. That version is superseded; the Settings row is not built.

### 10.1 Why the Settings screen was the weaker answer

The original reasoning was that bulk removal destroys outreach history and so "should not be one tap from the screen people use every day". That argument does not survive contact with the actual interaction: long-press → select → tap remove → confirm is **four deliberate steps**, not one tap. The premise was wrong, so the conclusion was too.

What the Home-based version gets right:

- **It is where the contacts are.** You notice you have stopped talking to five people *while looking at the list*, not while in Settings. Pruning is a response to seeing the list.
- **Long-press-to-multi-select is a standard Android idiom** — Gmail, Files, Photos. Near-zero learning cost for the audience this app has.
- **It deletes a whole screen and a Settings row from the phase.** Fewer things to build, fewer to maintain. That is the simplification directive applied to the IA itself, not just to pixels.
- It needs **no new components**: the selection control and the confirmation sheet already exist for FRM-102.

**The honest cost: discoverability.** Long-press is invisible — someone who does not know the idiom will never find bulk removal. That is acceptable because single-contact removal on Contact Detail (§6.3) is the discoverable path and bulk is the power path. It is a real trade, not a free win.

### 10.2 Entering and leaving

- **Long-press any contact row on Home.** That row becomes selected and the screen enters selection mode.
- While in the mode, **tap toggles selection** — rows do not navigate.
- Leave via the header's ✕, or system back, or by deselecting the last contact (deselecting everything exits automatically rather than leaving an empty mode).
- Leaving restores the title, the streak badges and the add FAB.

### 10.3 What changes on screen

| Element | Normal | Selection mode |
|---|---|---|
| Header title | "Friend-Minder" | "3 selected" |
| Header leading | nothing (Home is root) | white ✕ at the 24dp gutter; title shifts to 72dp |
| Row trailing 28dp slot | streak badge | **selection control** — 2dp `fm_primary` open ring, or filled circle with a white check |
| FAB | `fm_primary`, + glyph, "Add a contact" | **`fm_error` `#B3261E`, trash glyph**, "Stop tracking 3 selected contacts" |
| Bottom nav | normal | unchanged |

**The trailing slot swap is the point.** The badge column is already a 28dp circle that is either filled or open; in selection mode it carries selection instead of streak. Nothing moves, nothing resizes, and the control is the one already specced for FRM-102 §9.2. The streak badges return on exit.

The FAB swap keeps position and size and changes only fill and glyph — `#B3261E` with a white glyph is **6.54:1**, and red-plus-trash against teal-plus-plus is unmistakable.

### 10.4 Confirming

One bottom sheet naming the real cost: *"Stop tracking 3 people? This deletes 37 logged outreaches. They stay in your phone's contacts."* Confirm, or dismiss by handle/scrim/back.

After confirming: selection mode exits, the rows disappear from the list, snackbar **"3 people removed"**. **No Undo** — the history is genuinely gone, and an Undo that cannot restore it would be a lie. The confirmation carries the weight.

### 10.5 Accessibility — the part long-press usually breaks

A long-press gesture is not a usable primary path under TalkBack. Every contact row therefore carries an explicit **custom accessibility action, "Select"**, so selection mode is reachable without the gesture at all.

In the mode: each row announces its selection state, the header's "3 selected" is a live region that announces on change, and the destructive FAB's `contentDescription` states the count rather than saying "Remove".

Single-contact removal on Contact Detail (§6.3) is unchanged and remains independent of this.

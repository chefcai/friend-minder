# Friend-minder v2 — Phase 2 Screen Specs

**Owner:** Designer agent (FRM-4-Phase2) · **Tickets:** FRM-36–41 · **Source:** Phase 2 PRD §6.6, §8.3, §9
**Companion doc:** `DESIGN-SYSTEM-PHASE2.md` (colors, type, spacing, animation tokens referenced below as `§DS.x`)
**Status:** Final — ready for Publisher implementation (FRM-42)

Wireframes below are ASCII layout diagrams, precise enough for Publisher to build from directly. Visual mockup graphics (Canva-generated) accompany this doc under `docs/wireframes/` — see that folder for renders of each screen alongside these specs.

---

## 1. HomeFragment (updated)

**Ticket:** FRM-39, FRM-40, FRM-41 (photos, avatar fallback, animation)

```
┌─────────────────────────────────────┐
│  Friend-minder          [≡ menu]     │  AppBar, Headline Small
│  "You've reached 12 friends"         │  optional subtitle, Caption, on-primary
├─────────────────────────────────────┤
│ ┌───┐                                │
│ │ 🖼 │●  Alice Chen            [ 🔥5 ]│  48dp avatar w/ 8dp status dot,
│ │48dp│                        3d ago │  streak badge OR status dot (not both
│ └───┘                                │  — badge takes priority if streak>0)
├─────────────────────────────────────┤
│ ┌───┐                                │
│ │ AJ │●  Amir Johnson              │  Fallback: initials on palette color
│ │48dp│                        Today │  (§DS.4.3), status dot only (no streak)
│ └───┘                                │
├─────────────────────────────────────┤
│              ...                     │
├─────────────────────────────────────┤
│  [Dashboard]  [Groups]  [Settings]   │  Bottom nav, 3 destinations
└─────────────────────────────────────┘
```

- Row height: 72dp (48dp avatar + 12dp vertical padding top/bottom), full-width tap target.
- Row content: avatar (left, 48dp) → name (Subheading Small) + last-contact caption (stacked) → streak badge or status dot (right-aligned, vertically centered).
- List loads with fade-in stagger per `§DS.5` (300ms, 20ms stagger, first 8 rows only).
- Tapping a row: shared-axis-Z transition (250ms) into ContactDetailFragment.
- **Empty state:** no contacts imported yet → centered illustration + "No friends added yet" + "Import contacts" button (existing MVP flow, unchanged).
- **Loading state (photo):** gray `surface-variant` circle placeholder, swapped for photo or initials once `ContactsContract` query + async load resolves — never a layout jump (reserve the 48dp space immediately).

---

## 2. DashboardFragment (new)

**Ticket:** FRM-38

```
┌─────────────────────────────────────┐
│  Dashboard                           │  Headline Small
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │  🎯 12 contacts reached out to │   │  Key metric card, Subheading,
│ │     this month                 │   │  primary-container background
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │  Your strongest connections    │   │  Top streaks card
│ │  ┌──┐ ┌──┐ ┌──┐                │   │  up to 5 avatars w/ streak badge,
│ │  │🖼5│ │🖼8│ │AJ3│ ...          │   │  horizontal scroll if >3 fit
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │  Needs attention                │   │  Most-neglected card,
│ │  ┌──┐ Bob — 21 days             │   │  status-attention/neglected color
│ │  │🖼 │ Priya — 14 days           │   │  accent, tap row → Contact Detail
│ │  └──┘ [Reach out →]             │   │  inline quick-action button
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │  Monthly outreach       ▁▃▅▇▄  │   │  Bar chart (§DS.4.6), 4-5 bars
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │  Upcoming birthdays & dates     │   │  Next 14 days, per PRD §6.3
│ │  🎂 Alice — in 3 days           │   │  tap → Contact Detail
│ └───────────────────────────────┘   │
└─────────────────────────────────────┘
```

- All 5 cards use `§DS.4.1` card spec (12dp radius, 4dp elevation, 16dp padding), stacked with `space-6` (24dp) between them.
- Card load order/priority: key metric → streaks → neglected → chart → upcoming dates. If any single card's data is still computing, that card shows a shimmer/skeleton (not a spinner) at its final size — cards never reflow after their content resolves.
- "Needs attention" card intentionally uses `status-attention`, not `status-neglected` red, as its dominant accent — red is reserved for individual contacts truly past-due (see `§DS.1.2` note on not shaming); the card *title* itself stays neutral (`on-surface`), only the per-row indicator uses status color.
- Top streaks card: horizontal `RecyclerView`, avatar 48dp + streak badge, name below in Caption style, tap → Contact Detail.
- Empty state (new user, no history): single card, "Reach out to a few friends to see your stats here" + illustration — do not show 5 empty cards stacked.
- Chart interaction: tap a bar → tooltip showing exact count, auto-dismiss after 2s (no persistent axis labels beyond week/day markers, per `§DS.4.6`).

---

## 3. GroupsFragment (new)

**Ticket:** FRM-36 (indirectly — grouping context for logging), part of design system rollout

```
┌─────────────────────────────────────┐
│  Groups                    [ + ]     │  Headline Small, FAB or AppBar action
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │ 🟢 Close Friends          (8)  │   │  Group card: color dot/icon,
│ │                            >   │   │  name, member count, chevron
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │ 🔵 Family                  (5) │   │
│ │                            >   │   │
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │ 🟡 Work Colleagues        (12) │   │  long-press → edit/delete sheet
│ │                            >   │   │
│ └───────────────────────────────┘   │
└─────────────────────────────────────┘

Create/Edit Group (bottom sheet, slides in 250ms per §DS.5):
┌─────────────────────────────────────┐
│  New Group                    [✕]    │
│  ┌─────────────────────────────┐    │
│  │ Group name                    │    │  Text field, 48dp height
│  └─────────────────────────────┘    │
│  Color:  🔴 🟠 🟡 🟢 🔵 🟣 ⚪ ⚫      │  8-swatch picker, 40dp circles,
│                                       │  selected = 2dp outline ring
│  Icon (optional):  😀 🎉 💼 ❤️  ...  │  emoji picker, horizontal scroll
│  [ Cancel ]           [ Create ]     │  filled button (§DS.4.2)
└─────────────────────────────────────┘

Group Detail:
┌─────────────────────────────────────┐
│  ← Close Friends            [✏️]     │
├─────────────────────────────────────┤
│ ┌───┐ Alice Chen              [ ✕ ]  │  member row, 48dp avatar,
│ │🖼 │                                │  trailing ✕ removes from group
│ └───┘                                │  (does not delete contact — inline
├─────────────────────────────────────┤  confirmation snackbar with Undo)
│ ┌───┐ Bob Nguyen               [ ✕ ]  │
│ │🖼 │                                │
│ └───┘                                │
│  [ + Add contact to group ]          │
└─────────────────────────────────────┘
```

- Group color swatches use a curated 8-color set distinct from the avatar-fallback palette (§DS.1.4) to avoid a group color visually reading as "this is that contact's fallback color" — Publisher: use a separate, slightly more saturated palette for group identity chips (exact hex list to follow if Cai wants literal color-matching; otherwise reuse Material tonal palette accents).
- Deleting a group: confirmation dialog ("Delete 'Close Friends'? Contacts won't be removed, only the group.") — matches PRD §6.1 implementation note exactly; this copy should not be softened or skipped.
- Empty state: "No groups yet" + "Create your first group" button (per PRD §9.1 onboarding — optional, skippable).

---

## 4. ContactDetailFragment (updated)

**Ticket:** FRM-37 (outreach history), stats section (FRM-38 spec applied per-contact), group assignment, special dates

```
┌─────────────────────────────────────┐
│  ← Alice Chen                [ ⚙️ ]  │  gear → Edit Frequency dialog
├─────────────────────────────────────┤
│           ┌────────┐                 │
│           │  🖼 96dp │                │  Contact photo/initials, 96dp,
│           └────────┘                 │  centered
│           Alice Chen                 │  Headline Small
│      🎂 Sep 22   ·   Every 5 days    │  Caption: birthday + frequency
├─────────────────────────────────────┤
│  Last contact: 3 days ago            │  Stats section (Body)
│  🔥 Streak: 5 days                   │
│  Reach rate: 65% (13 of 20)          │
├─────────────────────────────────────┤
│  Groups:  [Close Friends ✕] [+ Add]  │  Chip row, tap chip ✕ to remove,
│                                       │  [+ Add] opens group picker sheet
├─────────────────────────────────────┤
│  [ History ]  [ Special Dates ]      │  Tab row (Material 3 tabs)
├───────────────────────────────────────
│  ▎ 📱 SMS — "Hey, how's it going?"   │  Outreach History tab (default):
│  ▎    Sep 14, 2:30pm                 │  timeline-style list, icon per
│  ▎ ☕ In-person — "Coffee at Brew    │  type, date + optional note
│  ▎    Haven"  ·  Sep 10               │
│  ▎ 📞 Call                            │
│  ▎    Sep 3                           │
├─────────────────────────────────────┤
│         [ + Log Outreach ]           │  Filled button (secondary color,
│                                       │  §DS.4.2), opens OutreachLogDialog
└─────────────────────────────────────┘
```

- Photo: 96dp, same fallback rules as `§DS.4.3`.
- Stats section pulls directly from Architect's `StatisticsService` (per PRD §7 Statistics entity) — Designer spec assumes fields `lastContacted`, `streak`, `reachRate` are available synchronously or via cached Flow; if Architect's service is async-only with no cache-then-refresh pattern, this section needs a skeleton state (flag to Architect/Tracker if that's not already covered — see FRM-38 dependency).
- Special Dates tab: list of birthday (auto, from `ContactsContract`, non-deletable but editable reminder lead time) + custom dates (user-added, full CRUD), each row shows label, month/day, and "remind me: [day of / 1 day before / 1 week before]" as a dropdown.
- Frequency editor (gear icon → dialog): slider 1–30 days with numeric input fallback, "Reset to default" text button, current effective value shown live as the slider moves ("Every 5 days").
- Empty outreach history: "No outreach logged yet — send a message or log a hangout" + the same Log Outreach button, just centered as an empty state rather than list-trailing.

---

## 5. OutreachLogDialog (new)

**Ticket:** FRM-36

```
┌─────────────────────────────────────┐
│  Log Outreach                 [✕]    │  Bottom sheet, slides in 250ms
├─────────────────────────────────────┤
│  Type                                │
│  ( SMS )  ( Call )  ( In-person )    │  Segmented / chip selector,
│  ( Video )  ( Other )                │  single-select, 48dp tall chips
├─────────────────────────────────────┤
│  When                                │
│  📅 Sep 14, 2026    🕐 2:30 PM       │  Date/time picker fields,
│                                       │  defaults to now; tap opens
│                                       │  native Android date/time picker
├─────────────────────────────────────┤
│  Note (optional)                     │
│  ┌─────────────────────────────┐    │
│  │ Got coffee at Brew Haven     │    │  Multi-line text field, 3 lines
│  └─────────────────────────────┘    │  visible, expands as needed
├─────────────────────────────────────┤
│  [ Cancel ]              [ Save ]    │  Save disabled until Type selected
└─────────────────────────────────────┘
```

- Entry points: FAB or bottom-sheet menu item in Contact Detail (per PRD — Publisher's call which, given existing MVP navigation; either is fine as long as it's reachable in ≤1 tap from Contact Detail).
- Backdating: date picker allows any past date (no future dates — outreach can't be logged ahead of time), matching PRD §6.4 "allow backdating for past hangouts."
- On save: dialog dismisses with the 200ms exit slide (§DS.5), Contact Detail's History tab updates immediately (optimistic UI — don't wait on a full stats recompute round-trip to show the new log entry), and streak/last-contact stats refresh in the background.
- Validation: Type is required (Save button disabled with `on-surface` at 38% opacity until a type is chosen); Note has no character limit but soft-wraps; no validation needed on date/time beyond the past-only constraint.
- Quick-log-from-home (PRD's "optional" swipe/notification action): **deferred** — not specified here as it's explicitly optional in the PRD and would need its own interaction spec (swipe direction, confirmation) if greenlit later. Flagging as an open item rather than guessing at unrequested UX.

---

## 6. Accessibility Checklist (per-screen)

| Screen | Contrast verified | Touch targets ≥48dp | Content descriptions | Notes |
|---|---|---|---|---|
| HomeFragment | ✅ (§DS.1) | ✅ rows are 72dp tall, full-width | ✅ "Photo of {name}" / "Initials avatar, {name}" | Status dot needs paired text on long-press tooltip, not color alone |
| DashboardFragment | ✅ | ✅ cards/rows ≥48dp | ✅ chart bars get "Week of {date}: {count} contacts" on focus | Bar chart must be reachable via TalkBack linear navigation, not just touch |
| GroupsFragment | ✅ | ✅ 40dp swatches sit inside 48dp tap targets (add 4dp padding each side) | ✅ swatch color names ("Red", "Teal"...) announced, not just color | Emoji icons need descriptions too ("Party icon") |
| ContactDetailFragment | ✅ | ✅ chips, tabs, gear icon all ≥48dp | ✅ streak/reach-rate read as full sentences, not bare numbers, for TalkBack | Tab row must announce selected state |
| OutreachLogDialog | ✅ | ✅ type chips 48dp tall | ✅ each type chip has label read aloud; Save button announces disabled state + reason | Native date/time pickers inherit system accessibility — no custom work needed there |

Full token-level contrast ratios are in `DESIGN-SYSTEM-PHASE2.md` §1 and §7.

---

## 7. Open Items for Handoff

1. **Group color palette exact hex values** — flagged in §3 above; using Material tonal accents as placeholder pending confirmation this doesn't need to visually differ more from the avatar-fallback set.
2. **Stats section sync/async contract** — flagged in §4; needs a one-line confirmation from Architect (FRM-32/Statistics service) on whether Designer's "instant display, background refresh" assumption holds, or whether a loading skeleton is mandatory on every Contact Detail open.
3. **Quick-log-from-home** — explicitly deferred per PRD's own "optional" framing (§5 above); not blocking Publisher's FRM-42–44 critical path.

None of these block Publisher from starting FRM-42 (Material 3 refinement) or FRM-43/44 (backup, migration) — they're scoped to the two lowest-risk, easily-patched visual details plus one explicitly-optional stretch feature.

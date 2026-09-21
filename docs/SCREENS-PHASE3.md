# Friend-Minder — Phase 3 Screen Specs

**Owner:** Designer-P3 · **Epic:** [FRM-93](https://chefcai.atlassian.net/browse/FRM-93)
**Depends on:** `DESIGN-SYSTEM-PHASE3.md` — every token referenced here is defined there.
**Supersedes:** `docs/SCREENS-PHASE2.md` — delete it in the same PR.

| § | Screen | Ticket | Status |
|---|---|---|---|
| 1 | Home (root) | [FRM-99](https://chefcai.atlassian.net/browse/FRM-99) | **Spec complete — awaiting sign-off** |
| 2 | Bottom navigation | [FRM-100](https://chefcai.atlassian.net/browse/FRM-100) | **Spec complete — awaiting sign-off** |
| 3 | Home empty state | [FRM-107](https://chefcai.atlassian.net/browse/FRM-107) | **Spec complete — awaiting sign-off** |
| 4 | Overall History | [FRM-101](https://chefcai.atlassian.net/browse/FRM-101) | Pending FRM-103 pass |
| 5 | Groups / Group Detail | [FRM-103](https://chefcai.atlassian.net/browse/FRM-103) | Pending |
| 6 | Contact Detail | [FRM-103](https://chefcai.atlassian.net/browse/FRM-103) | Pending |
| 7 | Settings / Advanced | [FRM-103](https://chefcai.atlassian.net/browse/FRM-103) | Pending |
| 8 | Dialogs | [FRM-103](https://chefcai.atlassian.net/browse/FRM-103) | Pending |
| 9 | Add-contact flow | [FRM-102](https://chefcai.atlassian.net/browse/FRM-102) | Pending |

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
│  ◯  Alice Johnson       (5)  │  76dp row
│      3 days ago              │
│  ────────────────────────    │  1dp #CFE7EA, inset 24dp both ends
│  ◯  Amir Johnson        (!)  │
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

- **Every tracked contact**, not a filtered subset. There is no "Needs attention" section; urgency lives in the per-row badge.
- **Sorted alphabetically** by display name, using a locale-aware, case-insensitive `Collator` — not `String.compareTo`, which sorts "ashley" after "Zoe" and mis-orders accented names.
- Row height **76dp**, growing if text wraps (it should not — see 1.6).
- Layout across the row: 24dp gutter → 48dp avatar → 16dp → flexible text block → 12dp → 28dp badge → 24dp gutter.
- Text block: contact name in Row Primary (17sp/400, `fm_ink`); beneath it the last-touch string in Row Secondary (14sp/400, `fm_ink_dim`).
- Divider between rows only — **not after the last row**, and not between the header and the first row.
- Row tap → Contact Detail, shared-axis Z, 250ms.
- Row long-press: nothing in v1. No context menu, no swipe actions.
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

### 1.5 The status badge — **final, two states (2026-09-21, rev 3)**

> **Correction, landed by Publisher-P3 while implementing FRM-99:** this
> section as staged in the shared handoff folder still described the
> intermediate three-cool-state revision (filled/hollow arrow + dot).
> That revision was superseded by a second same-day pass that Cai actually
> signed off on (FRM-99 comment, 2026-09-21: _"FRM-99, FRM-100 all signed
> off"_, approving the two-state version below) - the arrows never
> shipped. Flagging this explicitly rather than silently building off the
> stale copy, per the Worker Roster Pattern's own warning about exactly
> this failure mode (the Phase 2 Warm Circle / Cool Rebrand doc collision).
> `DESIGN-SYSTEM-PHASE3.md` §2.5 / §6.5 already had the correct, final
> version - this file just hadn't been updated to match. Designer-P3
> should reconcile the copy in the shared handoff folder so it doesn't
> drift again.

Fixed 28dp slot at the trailing edge, per DESIGN-SYSTEM §2.5 / §6.5. **Two
states, no third.**

| Condition | Fill | Content | `contentDescription` |
|---|---|---|---|
| Streak ≥ 1 | `#1F817D` circle | Numeral, white, 14sp/600; `9+` above 9 | "{n} day streak" |
| No streak | *no fill* - 2dp `#1F817D` ring | none | "No streak, last contact {last-touch string}" |

`fm_status_streak`, `fm_status_due`, `fm_status_overdue`, `fm_status_neutral`
and `fm_status_quiet` are all deleted - status costs exactly one colour now,
and it is the brand primary. There is no "overdue" concept on Home: the
question Home answers is *who should I reach out to*, and that's the daily
notification's job, not a per-row tone. The last-touch line (§1.4) already
says `Over a year ago` more precisely than any badge tone could.

Filled versus open is structural, not chromatic - it survives colour-
blindness, greyscale and a dim screen with no second channel needed as
insurance. The circle stays empty rather than showing a literal `0`; a
zero reads as a score you're failing, which is the wrong register for
this product.

The slot stays a fixed 28dp box in both cases (ring inset, not added, so
it doesn't grow to 32dp) so rows align regardless of state.

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

Removing a tracked contact is **not** on Home — it lives in Contact Detail, specified in the FRM-103 pass. Home is deliberately read-only apart from the FAB: no swipe-to-delete, no long-press menu.

---

## 2. Bottom navigation — FRM-100

**Current state:** there is no bottom nav. Navigation today is two toolbar icons on the Dashboard plus a chevron-list "setup hub" (`03-setup-hub.png`). All of that is retired.

### 2.1 Spec

- **Exactly three destinations, icon-only**, in this order: **Groups · Overall History · Settings**.
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
| 1 | People / circles | "Groups" |
| 2 | Bar chart | "Overall History" |
| 3 | Gear | "Settings" |

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

## 4–9. Remaining screens

Specified in the FRM-103 pass, each as a paired current-screenshot / proposed-redesign / rationale proposal requiring its own sign-off before Publisher implements it. Current-state capture is already complete for all of them and staged alongside this document.

Known issues already found during capture, to be resolved in that pass:

- **New Group dialog:** the eighth colour swatch ("Midnight") is clipped to 44px of 126px — eight 48dp swatches overflow a 360dp row. Needs wrapping or a smaller swatch.
- **Setup hub:** the notification-preview block uses a dashed border, which appears nowhere else in the app and reads as a dropzone. It needs a home — most likely Settings — and a real treatment.
- **Contact Detail:** "Last contact / Streak / Reach rate" are three bare text lines where the mockup's vocabulary is badges and numerals.
- **Settings:** the *Log Outreach* and *Save Settings* buttons use two different fills for the same weight of action; `fm_secondary` as a button fill is retired in DESIGN-SYSTEM §6.2.
- **Missing-contacts banner** is the only pink surface in an otherwise all-teal app and needs a decision: keep as error, or restyle as informational.

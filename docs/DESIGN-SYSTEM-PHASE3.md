# Friend-Minder — Phase 3 Design System

**Owner:** Designer-P3 · **Ticket:** [FRM-98](https://chefcai.atlassian.net/browse/FRM-98) · **Epic:** [FRM-93](https://chefcai.atlassian.net/browse/FRM-93)
**Status:** ✅ Approved by Cai 2026-09-21 (FRM-98, including the rev-3 delta). Cleared for Publisher-P3.
**Supersedes:** `docs/DESIGN-SYSTEM-PHASE2.md` — **delete that file in the same PR that adds this one.** Phase 2 shipped a Warm-Circle/Cool-Rebrand collision precisely because two palette docs coexisted; do not repeat it.

**Source of the aesthetic:** the hero mockup on friendminder.walkowiaks.com (`images/hero-mockup.png`) and the marketing site's own CSS custom properties, which are the only place the intended brand has ever been expressed accurately.

---

## 0. What actually changed, and why

Phase 2's palette was not wrong in its *values* — `fm_primary #1F817D`, `fm_secondary #5ABBEB`, `fm_accent #155D5B` are already the website's exact tokens and carry forward unchanged. Three structural mistakes are what made the built app look unfinished next to the mockup. Phase 3 fixes those three things; it is not a rebrand.

1. **The app used the website's *panel* color as its *page* color.** `fm_surface` was `#E6F5FB` — the site's `--bg-panel-alt`, a tone the site reserves for raised blocks. With the whole page already tinted, there was no lighter tone left to separate content from background, so Phase 2 reached for elevated `CardView`s to create separation it had spent its only surface on. **Phase 3 sets the page to white and keeps the tints for the two or three places that genuinely need to sit above it.** Once the ground is white, the cards have nothing left to do, and the "no card-boxing" directive costs nothing to honour.
2. **All body text was tinted teal.** `colorOnSurface` was `fm_ink #195556`. On the site, `#195556` is `--on-primary-container` — a *heading* color. Body text there is `--text #1B2E30`, a near-neutral. Tinting every word teal is most of why the built screens read as washed out. Phase 3 splits the ink into heading / body / secondary.
3. **Every categorical palette was compressed into one hue band.** The 10 avatar-fallback colors sat inside ~166–212°, giving a minimum pairwise ΔE of **4.8** — below the threshold at which two colors are separable at a glance, which is the *only* job a fallback avatar color has. The four status colors were all dark teals, so contact health was invisible. Phase 3 widens both (see §2.5, §2.6); the avatar palette's minimum pairwise ΔE is now **21.2**.

One more, from the on-device capture: `Theme.FriendMinder` sets `colorSurface` but nothing sets `android:windowBackground`, so every screen currently renders on the stock Material `#FEF7FF` rather than on a brand tone at all. §2.2 fixes this explicitly.

---

## 1. Principles

These are decision rules, not adjectives. When a spec here is ambiguous, resolve it with these, in order.

1. **White is the ground.** Content sits directly on `surface`. A tinted fill must earn its place; at most one per screen.
2. **Separation comes from space and alignment, never from a box.** `MaterialCardView` is not used anywhere in Phase 3. Grouping is a section label, ≥24dp of air, or a 1dp inset hairline — in that order of preference.
3. **Elevation is reserved for things that genuinely float.** Exactly one component has elevation: the FAB. Nothing else, ever.
4. **Colour is never the only signal.** Every state carried by colour is also carried by an icon *shape* (filled vs. outline) or by text.
5. **The header is one block.** The status bar and the title bar are the same colour, with no seam, no shadow, and no divider between the header and the content beneath it.
6. **Light mode only.** There is no `values-night/`, no `*-dark` token, and no dark-mode QA item anywhere in this document. Carried forward from Phase 2 unchanged, by explicit product decision.

---

## 2. Colour

Every pairing below was verified programmatically (WCAG 2.1 relative-luminance formula) and every categorical set was checked for CIELAB ΔE separation. Measured values are printed inline — they are not estimates.

### 2.1 Brand

Unchanged from Phase 2 / the marketing site. These four hexes are the brand and are not up for revision in this phase.

| Token | Hex | Notes |
|---|---|---|
| `fm_primary` | `#1F817D` | Header block, FAB, filled buttons, active nav icon |
| `fm_on_primary` | `#FFFFFF` | 4.67:1 on `fm_primary` ✓ |
| `fm_primary_container` | `#B0E8E6` | Rare. Selected chip only. |
| `fm_accent` | `#155D5B` | Text-weight teal for use **on tinted surfaces** — see the rule in §2.4 |

### 2.2 Surfaces

| Token | Hex | Where it is allowed |
|---|---|---|
| `fm_surface` | `#FFFFFF` | Every screen background, every list row, bottom nav, bottom sheets |
| `fm_surface_sunken` | `#EFF8FA` | Text inputs, search fields, unselected chips. (Segmented controls: unselected segments are `fm_surface`, selected `fm_primary`; see SCREENS Section 6.2, FRM-159.) |
| `fm_surface_accent` | `#E6F5FB` | **One block per screen, maximum.** In practice: the Overall History key-metric block, and the notification-preview block in Settings. Nowhere else. |
| `fm_header` | `#1F817D` | Status bar + title bar, as one continuous fill |

**Publisher — required, this is the fix for the missing brand background:** add `<item name="android:windowBackground">@color/fm_surface</item>` to `Theme.FriendMinder`. Setting `colorSurface` alone does not paint the window, which is why every current screen renders on stock `#FEF7FF`.

### 2.3 Ink

| Token | Hex | Use | Contrast on `fm_surface` |
|---|---|---|---|
| `fm_ink` | `#1B2E30` | Body copy, contact names, list primaries, input text | **14.19:1** |
| `fm_ink_dim` | `#4C6A6E` | Secondary/metadata text, captions, inactive nav icons | **5.84:1** |
| `fm_heading` | `#195556` | Section labels, in-content headings | **8.48:1** |

On `fm_surface_sunken` these measure 13.16 / 5.42 / 7.87 — all pass.

### 2.4 The one colour rule Publisher must not get wrong

`fm_primary #1F817D` as **text** measures **4.67:1 on white** (passes) but **4.33:1 on `fm_surface_sunken`** (fails AA).

> **Rule:** `fm_primary` may be used as a text/label colour only on `fm_surface` (`#FFFFFF`). On any tinted surface, a teal text colour must be `fm_accent #155D5B` (7.09:1 on sunken, 6.85:1 on accent).

This is a real trap: the same text button looks fine on a white screen and silently fails inside a filled block.

### 2.5 Status (contact health) — **two states, revised 2026-09-21**

**Cai's direction, FRM-99:** the numeral explains itself; a glyph does not. Overdue is removed as a concept from the list.

| State | Treatment | Contrast |
|---|---|---|
| Streak ≥ 1 | 28dp `fm_primary` `#1F817D` circle, white numeral 14sp/600, `9+` above 9 | 4.67:1 |
| No streak | 28dp circle, **no fill**, 2dp `fm_primary` `#1F817D` ring | 4.67:1 — well clear of the 3:1 a graphical object needs |

There is no third state. New, quiet and long-overdue contacts all carry the open ring. `fm_status_streak`, `fm_status_due`, `fm_status_overdue`, `fm_status_neutral` and `fm_status_quiet` are all **deleted** — status costs exactly one colour now, and it is the brand primary.

Three things this gets right that the earlier four-tone and three-tone versions did not:

- **Filled versus open is structural, not chromatic.** It survives colour-blindness, greyscale and a low-brightness screen without needing a second channel to back it up. Every previous version leaned on tone and needed a glyph as insurance.
- **Urgency was already on the row, in words.** `Over a year ago` says more, more precisely, than any badge tone could. A second tone was buying a signal the last-touch line carries better.
- **The circle stays empty rather than showing a literal `0`.** A zero reads as a score you are failing, which is the wrong register for this product.

`contentDescription` carries what the shape cannot: "{n} day streak" for the filled state, "No streak, last contact {last-touch string}" for the open one.

### 2.6 Avatar fallback palette — **redefined** (resolves FRM-108 / PRD open question #1)

**Decision: redefined, not carried over.** Cai signed off on widening the band on 2026-09-20.

Phase 2's 10 colours sat in a ~166–212° band with a minimum pairwise ΔE of **4.8**. On the real *Edit Friends* screen (screenshot `02-manage-friends.png` in the handoff folder) eight of the ten read as the same blue-green. A fallback avatar colour exists to make a specific person findable in a scrolling list; at ΔE 4.8 it was decorative only.

Phase 3 keeps teal/cyan as the centre of gravity — six of ten are still cool — but admits warm and neutral tones. The hero mockup itself shows a warm red initials avatar, so this is a return to the reference, not a departure from it.

| Index | Name | Fill | Initials | Contrast |
|---|---|---|---|---|
| 1 | Pine | `#2C6E49` | `#FFFFFF` | 6.12:1 |
| 2 | Cyan | `#0B6E92` | `#FFFFFF` | 5.74:1 |
| 3 | Sky | `#9CCBEC` | `#10323F` | 7.85:1 |
| 4 | Indigo | `#3D55A4` | `#FFFFFF` | 6.92:1 |
| 5 | Slate | `#62707C` | `#FFFFFF` | 5.09:1 |
| 6 | Seafoam | `#8ED0C4` | `#0F3B34` | 7.06:1 |
| 7 | Sand | `#DCC084` | `#3A2F14` | 7.46:1 |
| 8 | Clay | `#B85535` | `#FFFFFF` | 4.78:1 |
| 9 | Plum | `#80458A` | `#FFFFFF` | 6.70:1 |
| 10 | Rose | `#E8B3BC` | `#4A2028` | 7.59:1 |

- Assignment is unchanged: `abs(contactId.hashCode()) % 10`, deterministic across sessions.
- **Minimum pairwise ΔE = 21.2** (closest pair: Cyan/Slate). Phase 2's was 4.8.
- **Index 1 changed 2026-09-21**, a consequence of the §2.5 revision. It was `Deep Teal #0F6F6A`, which sits ΔE 7.0 from the brand teal the streak badge now uses — the same row would have carried two near-identical teals at either end. `Pine #2C6E49` restores that gap to 21.7 and leaves the rest of the set untouched. Green is free for an avatar now precisely because status no longer uses it.

**The rule this settles: warmth identifies people, cool communicates state.** Warm tones (Sand, Clay, Rose, Plum) appear only as avatars; the cool band belongs to status and chrome. That division is easier to hold to than the per-value ΔE checks, and it is why the warm avatars survive a cool-only status directive without conflict.
- Each fill is paired with a **fixed** initials colour in the table — `AvatarPalette.kt` stays the single place that holds the pairing. Five pairs use white, five use a dark ink; do not compute this at runtime and do not assume white.
- Four of the ten (Sky, Seafoam, Sand, Rose) are light fills measuring 1.7–1.8:1 against white. That is intentional — the initials carry the contrast, not the circle. A light avatar therefore needs an edge: see §4.3.

### 2.7 Group identity palette

Carried forward from Phase 2 (`fm_group_color_1…8`) **unchanged**. Group colours are user-chosen, appear as chips rather than as circles next to avatars, and were not part of the confusability problem. Keeping them stable avoids silently recolouring groups users already created.

One bug to fix while touching this, found on device: in the New Group dialog the eighth swatch ("Midnight") is clipped to 44px of its 126px width — eight 48dp swatches plus gaps overflow a 360dp-wide row. See SCREENS §Dialogs.

### 2.8 Lines and borders

| Token | Hex | Use |
|---|---|---|
| `fm_divider` | `#CFE7EA` | 1dp hairlines between list rows, above the bottom nav |
| `fm_outline` | `#5A8A92` | 1dp border on text inputs and other bounded controls — **3.83:1 on white**, which is what WCAG 1.4.11 requires of a control whose boundary identifies it |
| `fm_error` | `#B3261E` | Unchanged from Phase 2 |

`fm_divider` measures 1.29:1 and is deliberately decorative: list rows are identified by their content, not by their separators, so the hairline is not a "boundary required to identify a control". Input fields *are*, which is why they get `fm_outline` and a filled ground rather than a hairline.

---

## 3. Typography

**Roboto**, system-supplied. No custom font — a font file is a build dependency and an app-size cost, for no gain. (The original wording cited F-Droid review; F-Droid was dropped as a channel on 2026-09-21. The decision stands on its own merits.)

| Style | Size / line | Weight | Use |
|---|---|---|---|
| Header Title | 30sp / 36sp | 400 | The title in the teal header. White. |
| Screen Heading | 22sp / 28sp | 500 | In-content headings (Contact Detail name, empty-state headline) |
| Section Label | 13sp / 16sp | 600, +0.08em, UPPERCASE | "GROUPS", "MESSAGE TEMPLATES" — `fm_heading` |
| Row Primary | 17sp / 24sp | 400 | Contact name, group name, settings row label |
| Row Secondary | 14sp / 20sp | 400 | Last-touch string, member count — `fm_ink_dim` |
| Body | 16sp / 24sp | 400 | Prose, input text, dialog copy |
| Button | 16sp / 20sp | 600 | Sentence case. **Never all-caps.** |
| Caption | 13sp / 18sp | 400 | Helper text under controls |

- Minimum size anywhere: **13sp**.
- Header Title auto-shrinks to a 24sp floor before it ellipsizes, so long contact names degrade gracefully rather than truncating at 30sp.
- Phase 2's 28sp/700 "Headline Large" and its all-caps label option are both retired. Bold-heavy headings fight the low-density direction.

---

## 4. Space, size, and shape

### 4.1 Grid

Base unit **4dp**. Screen gutter is **24dp** — up from Phase 2's 16dp, and the single largest contributor to the "less dense" feel. It matches the marketing site's 24px gutter and the ~25dp measured off the mockup.

| Token | Value | Use |
|---|---|---|
| `space_1` | 4dp | Icon-to-label |
| `space_2` | 8dp | Within a tightly-bound pair |
| `space_3` | 12dp | Row internal vertical padding |
| `space_4` | 16dp | Avatar-to-text gap, control padding |
| `space_6` | 24dp | **Screen gutter**, gap between unrelated blocks |
| `space_8` | 32dp | Above a section label |
| `space_12` | 48dp | Empty-state rhythm |

### 4.2 Heights

| Element | Height | Notes |
|---|---|---|
| Header (title bar) | 72dp | **plus** the status-bar inset, same fill, no seam |
| Contact row | 76dp | 48dp avatar + 14dp above/below |
| Settings / nav row | 64dp | |
| Bottom nav | 64dp | plus the bottom system inset |
| Button (filled) | 56dp | |
| Text input | 56dp | |
| FAB | 56dp | |

Every interactive element has a ≥48dp touch target even where the drawn element is smaller — achieved with padding, not by drawing bigger.

### 4.3 Icons, avatars, shape

| Element | Size |
|---|---|
| Header action glyph | 26dp (48dp target) |
| Bottom-nav glyph | 28dp |
| Row status badge | 28dp circle |
| Inline/list glyph | 24dp |
| Avatar — list | 48dp |
| Avatar — contact detail | 96dp |
| Avatar — notification | 64dp |
| Empty-state graphic | 96dp |

- Corner radius: **16dp** for filled blocks and bottom sheets, **12dp** for inputs, **28dp** (pill) for buttons, full circle for avatars and badges.
- Avatars are full circles with **no** border — except the four light fills (Sky, Seafoam, Sand, Rose) **and every photo avatar**, which take a 1dp `fm_divider` hairline so the circle still has an edge against white. See §6.10.
- Photo loading placeholder: `fm_surface_sunken` circle. Never a blank or transparent circle.

### 4.4 Elevation

`0dp` everywhere. The FAB is `3dp`. That is the complete list.

The header has no shadow and no bottom divider — the flat colour boundary against white is the separation, and adding a shadow there is exactly the seam this phase exists to remove. Bottom sheets separate with a scrim, not a shadow.

---

## 5. The header (the defining component)

The single most visible defect in the shipped app is that the status bar and the toolbar are painted as two regions. On the captured Dashboard, `statusBarBackground` occupies `[0,0]–[1080,128]` in teal while the toolbar below it renders near-white — the seam is literally a 48dp band of one colour above 64dp of another.

**Spec:**

- One `#1F817D` fill spanning the status bar and the 72dp title bar as a single view. No divider, no shadow, no elevation, no scroll-based colour change.
- Title: Header Title style, white, left-aligned at the 24dp gutter, vertically centred in the 72dp band.
- Actions: right-aligned 48dp icon buttons, white 26dp glyphs, 8dp inset from the edge so the glyph lands on the 24dp optical gutter.
- Back affordance (non-root screens only): white 26dp arrow at the 24dp gutter; the title then starts at 72dp.
- Status-bar icons are **light** (white). The header is dark enough to require it.

**Publisher — implementation, not decoration:**

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
window.statusBarColor = Color.TRANSPARENT
WindowInsetsControllerCompat(window, window.decorView)
    .isAppearanceLightStatusBars = false
```
…then apply the top system-bar inset as **padding** on the header view, so the teal draws behind the status bar rather than beside it.

Setting `android:statusBarColor` to the same teal also removes the visible seam and is acceptable as a fallback, but it is not the same fix: Android 15 forces edge-to-edge for `targetSdk 35`, and the inset approach is the one that survives that bump. Prefer the inset approach.

---

## 6. Components

### 6.1 Lists
Flat rows on `fm_surface`, separated by a 1dp `fm_divider` hairline **inset 24dp at both ends**, with no divider after the final row. No backgrounds, no borders, no cards. Row press state is a Material ripple at 12% `fm_primary`, nothing else — no elevation change.

**Value rows (FRM-166, ST-4).** Every setting that has a value uses one row shape, `Widget.FM.ValueRow`: label (Row Primary, with optional Row Secondary helper beneath) on the left; value (17sp `fm_ink_dim`, right-aligned, capped at 180dp so a long value wraps instead of squeezing the label) and a 24dp chevron on the right; minHeight 64dp; value and chevron vertically centred on the row. Tapping the row opens that setting's picker. No outlined pill buttons for values.

### 6.2 Buttons
- **Filled** — `fm_primary` ground, white label, 56dp, pill radius. The one primary action on a screen.
- **Text** — label in `fm_primary` on white, `fm_accent` on any tint (§2.4). 48dp target.
- **Icon** — 48dp target, 26dp glyph, `fm_ink_dim` inactive / `fm_primary` active. This is the generic icon *button*; the bottom-nav glyph is 28dp and is specified in §6.9.
- **FAB** — 56dp, `fm_primary`, white 26dp glyph, 3dp elevation, 24dp from the right gutter and 16dp above the bottom nav.

Phase 2's use of `fm_secondary #5ABBEB` as a large button fill is retired: a pale cyan fill with dark text reads as disabled. Primary actions are `fm_primary`. (The button that carried that fill — *Log Outreach* — is itself removed from the UI per FRM-114; the rule outlives it.)

### 6.3 Inputs
`fm_surface_sunken` fill, 1dp `fm_outline` border, 12dp radius, 56dp tall, 16dp internal padding, `fm_ink` text, `fm_ink_dim` hint. Focus: border thickens to 2dp in `fm_primary`. A label above the field in Section Label style, not a floating placeholder.

### 6.4 Chips
32dp tall, pill, `fm_surface_sunken` ground with `fm_ink` label when unselected; `fm_primary_container` ground with `fm_accent` label when selected (5.65:1 ✓ — note this is the tinted-surface case from §2.4). Group chips take the group's own colour as a 8dp leading dot, never as the chip fill.

### 6.5 Status badge
28dp circle at the row's trailing edge, per §2.5. Filled `fm_primary` with a white numeral when a streak is running; otherwise a 2dp `fm_primary` ring on no fill. Same diameter either way, so rows align regardless of state — use `box-sizing: border-box` equivalent insets so the ring does not grow the circle to 32dp.

### 6.6 Bottom sheets (dialogs)
All dialogs are bottom sheets. `fm_surface` ground, 16dp top corners, 24dp gutter, a 4dp × 32dp `fm_divider` drag handle centred at the top, scrim `#1B2E30` at 40%. Title in Screen Heading style. Primary action is a full-width filled button at the bottom; dismissal is the handle, the scrim, and back — **not** a paired Cancel button competing with the primary.

### 6.7 Empty states
See SCREENS-PHASE3 for per-screen copy. Shape: 96dp glyph in `fm_ink_dim` at 40% opacity, 32dp gap, headline in Screen Heading, 8dp gap, one line of Body in `fm_ink_dim`, 48dp gap, one filled button. Vertically centred in the content area, never top-aligned. Never a card, never coloured.

### 6.8 Charts
Bar chart, `fm_primary` bars, 4dp top-corner radius, no gridlines, no axis lines, Caption-style labels in `fm_ink_dim`. Values appear on tap, not persistently. The chart sits directly on the surface; only the key-metric block above it may use `fm_surface_accent`.

---

### 6.9 Bottom-nav glyphs — exact geometry

Three glyphs, each drawn in a **28×28 viewBox** rendered at **28dp**, centred in a 48dp touch target. Inactive is the outline variant in `fm_ink_dim` `#4C6A6E`; active is the filled variant in `fm_primary` `#1F817D`. The outline→filled change is what carries state without relying on colour (§9).

**Slot 1 — Groups.** Three people: one in front, two behind. Approved by Cai 2026-09-21 (GH #119) after the two-circle glyph was found not to read.

Inactive — every stroke `1.9`, `stroke-linecap="round"`, no fill:

```
circle cx=5    cy=8.2  r=2.7
circle cx=23   cy=8.2  r=2.7
path   M1.2 19.2c0-2.9 1.9-4.4 4.4-4.4
path   M26.8 19.2c0-2.9-1.9-4.4-4.4-4.4
circle cx=14   cy=10.2 r=3.5
path   M6.4 22.6c0-3.5 3.4-5.6 7.6-5.6s7.6 2.1 7.6 5.6
```

Active — all fill, no stroke; the two open shoulder arcs become closed wedges and the heads grow to compensate for the lost stroke width:

```
circle cx=5    cy=8.2  r=3
circle cx=23   cy=8.2  r=3
path   M1.2 19.2c0-2.9 1.9-4.4 4.4-4.4v4.4z
path   M26.8 19.2c0-2.9-1.9-4.4-4.4-4.4v4.4z
circle cx=14   cy=10.2 r=3.9
path   M6.4 22.6c0-3.5 3.4-5.6 7.6-5.6s7.6 2.1 7.6 5.6z
```

**Three constraints in that geometry are load-bearing. Do not "tidy" them.**

1. **One stroke weight, 1.9, for all six elements.** An earlier draft put 1.7 on the two back figures. In a glyph this dense the thinnest stroke is the one that disappears first, and the thing that disappears is precisely what makes this read as *three* rather than two.
2. **The 0.9dp channel between the front head and the figures behind it.** Front head r3.5 at cx14 gives an outer edge of 9.55; the back heads' outer edge is 8.65. That 0.9dp of white is the whole depth cue. Enlarging the front head closes it and the glyph becomes a blob.
3. **The outer shoulders are arcs that tuck under the front figure's shoulder line**, not floating stubs. A detached 4dp stub reads as a smudge at 20dp.

Verified legible at 28dp, 24dp and 20dp — 20dp standing in for a dim screen, a reduced display scale and arm's length.

**Slot 2 — Overall History.** Three rounded bars, unchanged; it was the one glyph nobody had trouble with.

```
rect x=4.5  y=15   w=4.6 h=8.4  rx=1.4
rect x=11.7 y=9.4  w=4.6 h=14   rx=1.4
rect x=18.9 y=12.6 w=4.6 h=10.8 rx=1.4
```

Inactive: stroke `1.8`, no fill. Active: fill, no stroke.

**Slot 3 — Settings.** The gear from FRM-100. Its inner circle is centred on the cog body; that centring was a defect Cai caught once already and it is the first thing to re-check if the glyph is ever redrawn.

**The gear must be migrated to a 28×28 viewBox** (GH #160). It currently ships at `android:width="24dp"` / `viewportWidth="24"` with `strokeWidth="1"`, while Groups and History are at 28/28 with 1.9 and 1.8. With `itemIconSize` at 28dp the gear is scaled 1.167×, so its stroke renders at roughly 1.17dp — about a third lighter than its neighbours, in a bar whose design depends on three marks reading as one set.

Rescale the **shipped** path by 7/6 with a script; do not retype the coordinates, and do not simply raise `strokeWidth` in one file — that desynchronises the two states, which is the defect §6.9a exists to prevent. Re-verify the inner circle's centring after the rescale.

### 6.9a The selected-state invariant — applies to every nav glyph

**The selected silhouette must be a strict superset of the unselected one. Zero pixels lost at any edge.**

The filled state is the outline state with the stroke **recoloured, not removed**. Per path, exactly two attributes change:

| Attribute | Inactive | Active |
|---|---|---|
| `strokeColor` | `fm_ink_dim` `#4C6A6E` | `fm_primary` `#1F817D` |
| `fillColor` | `#00000000` | `fm_primary` `#1F817D` |
| `strokeWidth` | *(unchanged)* | *(unchanged)* |

**Why this is a rule and not a preference.** A stroke straddles its path, extending half its width to either side. A filled variant that drops the stroke therefore loses half a stroke-width from *every* edge: the selected glyph becomes strictly **smaller** than the unselected one at the same moment it becomes much heavier. Shrinking and darkening in one frame is what reads as a jump, and it was measurable — the Groups glyph moved 1.00dp at its edges, History 0.90dp, Settings 0.60dp.

**Do not compensate by growing the filled geometry.** That was tried and it cannot work: the stroke is a uniform offset around an arbitrary path, and matching it needs a true path offset, not enlarged radii. Every glyph ends up with hand-tuned numbers that silently drift from its outline as either file is edited. Keeping the stroke makes both states share one geometry by construction.

**One permitted exception,** where a stroke would otherwise hang outside the fill: a subpath whose open ends sit on the shape's baseline may be closed with a trailing `z` in the **filled state only**, with `strokeLineJoin="round"` replacing `strokeLineCap="round"`. The outline state keeps the path open. This is the front figure's body on the Groups glyph and nothing else so far.

**How to verify:** render both states and diff the alpha masks. Expect **0px** difference at any edge and identical bounding boxes. This is cheap, objective, and the only check that actually catches the failure.

---

### 6.10 Avatars — photo before initials

Approved by Cai 2026-09-21 (GH #116). Sizes are §4.3: 48dp in lists, 96dp on Contact Detail, 64dp in the notification.

**The source order is photo, then initials.** Where the phone's own contacts database holds a photo for a tracked contact, that photo *is* the avatar. The coloured-initials fallback of §2.6 is what happens when there isn't one — it was never the goal, only the floor.

| Size | `ContactsContract` field |
|---|---|
| 48dp, 64dp | `PHOTO_THUMBNAIL_URI` |
| 96dp | `PHOTO_URI` |

Read it through the existing `ContactsLoader` query. **Do not introduce the system contact-picker UI for this** — the app queries `ContactsContract` directly and that stays true.

**Rules, in the order they bite:**

1. **Never mix the two.** A photo avatar carries no coloured fill and no initials, not even behind a transparent PNG. One or the other, whole.
2. **Every photo takes the 1dp `fm_divider` `#CFE7EA` hairline**, inset so the diameter is unchanged. This is not optional the way it is for the dark fills: a photo with a pale edge — a bright sky, a white wall, a studio backdrop — has exactly the dissolving-into-the-row problem the hairline exists to solve, and you cannot know in advance which photos those are.
3. **Circular crop, centre-crop, never letterboxed.** A photo's aspect ratio is whatever the contact's camera gave it; fitting it inside the circle leaves bars, and bars inside a circle look like a bug.
4. **Fall back silently.** No photo, an unreadable photo, or `READ_CONTACTS` revoked → the §2.6 initials avatar, with no broken-image glyph, no error text and no gap. A revoked permission is a state the user chose; it is not a failure to report in a list row.
5. **Initials first, photo when it arrives.** Render the fallback immediately and swap once the photo resolves. Do **not** show a grey placeholder, a spinner or an empty circle — at 48dp in a scrolling list those read as flicker, and the initials are a correct answer rather than a waiting state.
6. **Cache by contact lookup URI, not by a bitmap snapshot taken when the contact was added.** Someone who adds a photo in their phone's contacts app expects it to appear here; a snapshot taken at add time never updates and the contact is stuck faceless forever.

**Where it applies:** Home rows, Contact Detail, Overall History's longest-streak rows, both steps of the add-contact flow, and the notification. Everywhere an avatar appears, one rule.

**What it does not touch:** selection mode. The selection control lives in the row's trailing 28dp slot (SCREENS §10.3), not on the avatar, so photos and selection never compete for the same pixels.

---

## 7. Motion

| Animation | Duration | Easing |
|---|---|---|
| List fade-in | 200ms | Decelerate. **No stagger** — staggering reads as lag at this row height. |
| Row press | 100ms | Ripple only |
| Bottom-nav destination change | 200ms | Fade-through |
| Home → Contact Detail | 250ms | Shared-axis Z |
| Bottom sheet in / out | 250ms / 200ms | Decelerate / accelerate |
| Streak badge increment | 200ms | Overshoot to 1.15× |

All animations respect the system "remove animations" setting — check `ValueAnimator.areAnimatorsEnabled()` and jump to the end state. No animation gates input.

---

## 8. Accessibility

- [ ] Every text/background pair ≥4.5:1; every icon and control boundary ≥3:1. The measured values in §2 are the reference — re-verify any value that changes.
- [ ] `fm_primary` never used as text on a tinted surface (§2.4).
- [ ] Every interactive element ≥48×48dp.
- [ ] Every icon-only control has a `contentDescription`, and the three bottom-nav items additionally have `tooltipText` for long-press, since they carry no labels.
- [ ] Active bottom-nav state is carried by a filled-vs-outline glyph, not by colour alone.
- [ ] Status badges pair colour with a distinct glyph or numeral (§6.5).
- [ ] Avatar initials use the fixed paired ink from §2.6, not a computed one.
- [ ] Photo avatars carry the 1dp hairline and fall back silently when absent or unreadable (§6.10).
- [ ] Light status-bar icons set explicitly; verify against the teal header on a device with a notch and one without.
- [ ] Text scaling to 200% does not clip contact rows — the name/last-touch block is the flexible element; the avatar and badge are fixed.

---

## 9. Retirement checklist for Publisher

Phase 3 is not done while any of these is still true:

- [ ] `docs/DESIGN-SYSTEM-PHASE2.md` exists.
- [ ] `docs/SCREENS-PHASE2.md` exists.
- [ ] Any `MaterialCardView` / `CardView` remains in `app/src/main/res/layout/`.
- [ ] Any layout sets a non-zero `cardElevation` or `elevation` other than the FAB.
- [ ] `colors.xml` still contains the Phase 2 avatar palette values.
- [ ] `Theme.FriendMinder` lacks `android:windowBackground`.
- [ ] Any `TextView` renders body copy in `fm_ink` at `#195556` (the old teal-body bug).
- [ ] Any screen still draws a differently-coloured status bar from its header.

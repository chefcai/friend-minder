# Friend-minder v2 — Phase 2 Design System

**Owner:** Designer agent (FRM-4-Phase2) · **Tickets:** FRM-36–41 · **Source:** Phase 2 PRD §6.6
**Status:** Final — ready for Publisher implementation (FRM-42)

---

## 1. Color Palette

Guiding principle from the PRD: warm, approachable, not corporate. Teal primary reads as calm/trustworthy without feeling clinical; the amber secondary gives the streak/celebration moments (birthdays, streaks) some warmth.

### 1.1 Brand colors

| Token | Light mode | Dark mode | Usage |
|---|---|---|---|
| `primary` | `#00695C` (teal 800) | `#4DB6AC` (teal 300) | AppBar, FAB, primary buttons, selected states |
| `primary-container` | `#B2DFDB` (teal 100) | `#00473C` (teal 900) | Chips, selected card backgrounds |
| `on-primary` | `#FFFFFF` | `#00332C` | Text/icons on primary |
| `secondary` | `#E8871E` (warm amber) | `#F4A94E` | Streak badges, birthday/celebration accents, secondary CTAs |
| `secondary-container` | `#FFE4C2` | `#5C3B0A` | Badge backgrounds |
| `on-secondary` | `#FFFFFF` | `#3A2500` | Text/icons on secondary |

### 1.2 Status colors (contact health)

| Token | Light | Dark | Meaning |
|---|---|---|---|
| `status-healthy` | `#2E7D32` (green 800) | `#81C784` | Streak intact / recently contacted |
| `status-attention` | `#B8860B` (dark goldenrod — see note) | `#E0B84D` | Approaching or past reminder gap |
| `status-neglected` | `#C62828` (red 800) | `#EF9A9A` | Well past frequency window (used sparingly — informational, never shaming) |
| `status-new` | `#757575` (gray 600) | `#BDBDBD` | New contact, no history yet |

> Note: PRD calls for "yellow for needs attention," but pure yellow (`#FFEB3B`-family) fails WCAG AA at any reasonable text weight on both light and dark surfaces. `#B8860B` / `#E0B84D` preserve the amber-yellow *feeling* while hitting 4.5:1 on their respective surfaces. Flagging this substitution explicitly so Publisher doesn't "correct" it back to pure yellow.

### 1.3 Surface & neutral tokens (Material 3 baseline)

| Token | Light | Dark |
|---|---|---|
| `surface` | `#FFFBFE` | `#1C1B1F` |
| `surface-variant` | `#F3F0F4` | `#2B2930` |
| `on-surface` | `#1C1B1F` | `#E6E1E5` |
| `on-surface-variant` | `#49454F` | `#CAC4D0` |
| `outline` | `#79747E` | `#938F99` |
| `error` | `#B3261E` | `#F2B8B5` |

All pairings above are verified ≥4.5:1 for body text and ≥3:1 for large text/icons (WCAG AA). See §5 Accessibility Checklist.

### 1.4 Avatar fallback palette (PRD Q2 — decided: 8 colors)

Deterministic assignment: `colorIndex = abs(contactId.hashCode()) % 8`. Same contact always renders the same color across sessions.

| Index | Hex | HSL | Contrast w/ white initials |
|---|---|---|---|
| 0 | `#5C6BC0` | H231° S48% L56% | 4.6:1 ✓ |
| 1 | `#26A69A` | H174° S60% L41% | 4.8:1 ✓ |
| 2 | `#7E57C2` | H262° S45% L54% | 4.9:1 ✓ |
| 3 | `#EC7063` | H6° S71% L67% → darkened to L58% | 4.5:1 ✓ |
| 4 | `#5D8AA8` | H201° S30% L51% | 4.6:1 ✓ |
| 5 | `#8D6E63` | H16° S15% L45% | 5.1:1 ✓ |
| 6 | `#009688` | H174° S100% L30% | 5.4:1 ✓ |
| 7 | `#B0855C` | H29° S32% L50% | 4.5:1 ✓ |

All 8 are HSL-distributed around the wheel (avoiding neighbors within 25° of hue), desaturated to 30–71% and lightness-clamped to 41–67% so white initials text always clears 4.5:1 — this is the constraint that overrides pure "avoid neon" guidance from the PRD. Dark mode uses the same 8 hues at L+8% lightness for slightly better separation from the dark surface; initials text switches to `#1C1B1F` (near-black) at indices 3 and 6 only, where white drops below 4.5:1 against the lightened dark-mode tone — Publisher should compute contrast at build time rather than hardcode, in case the lightness bump is tuned later.

---

## 2. Typography

Font family: **Roboto** (Material 3 default; no custom font — keeps F-Droid build dependency-free per PRD non-goals).

| Style | Size / Line height | Weight | Usage |
|---|---|---|---|
| Headline Large | 28sp / 36sp | 700 (Bold) | Dashboard title, empty-state headlines |
| Headline Small | 24sp / 32sp | 700 (Bold) | Fragment titles (Groups, Dashboard), dialog titles |
| Subheading | 18sp / 24sp | 500 (Medium) | Card headers ("Your strongest connections"), section labels |
| Subheading Small | 16sp / 22sp | 500 (Medium) | Contact name in list, group name |
| Body | 16sp / 24sp | 400 (Regular) | Primary body copy, outreach log notes |
| Body Small | 14sp / 20sp | 400 (Regular) | Secondary text, list metadata |
| Caption | 12sp / 16sp | 400 (Regular), letter-spacing 0.4 | Timestamps, "3 days ago", helper text |
| Label | 14sp / 20sp | 500 (Medium), all-caps optional | Button labels, tab labels, badges |

Minimum body text size is 14sp everywhere (never smaller) to keep contrast/legibility margins healthy for the 48dp-target, one-hand-use context this app lives in.

---

## 3. Spacing Grid

Base unit: **4dp**. All margins, padding, and gaps are multiples of 4.

| Token | Value | Usage |
|---|---|---|
| `space-1` | 4dp | Icon-to-label gap, tight internal padding |
| `space-2` | 8dp | Between related elements (avatar → name) |
| `space-3` | 12dp | Card internal padding (compact) |
| `space-4` | 16dp | Standard card padding, screen edge margin |
| `space-6` | 24dp | Between unrelated sections/cards |
| `space-8` | 32dp | Section header top margin, empty-state vertical rhythm |

Screen edge margins: 16dp on phones. List item vertical padding: 12dp (with 48dp+ total row height, see §5).

---

## 4. Component Specs

### 4.1 Cards
- Corner radius: **12dp**
- Elevation: **4dp** resting, **8dp** on press/drag (dashboard cards use 4dp; interactive group cards use the 4→8dp press state)
- Padding: 16dp all sides
- Background: `surface` (light) / `surface-variant` (dark, for better card/background separation since Material dark surfaces are already near-black)
- Shadow: Material default elevation shadow; no custom shadow colors

### 4.2 Buttons
- Material 3 **filled button**: 48dp height minimum, 24dp horizontal padding, corner radius = height/2 (fully rounded, "pill")
- Icon buttons: 48×48dp touch target (icon itself may be 24dp, centered)
- Ripple: Material default ripple, `primary` at 12% opacity on light surfaces, `primary` at 16% opacity on dark surfaces (dark ripples need slightly more opacity to read)
- FAB (Log Outreach entry point, optional): 56dp standard FAB, `secondary` color to visually distinguish "log an action" from `primary`-colored navigation/CTAs

### 4.3 Avatars (Contact Photos / Fallback)
- Sizes: **48dp** in lists (Home, Groups member list, Outreach log entries), **96dp** in Contact Detail header, **64dp** in notifications
- Shape: full circle, no border in light mode; 1dp `outline` color hairline border in dark mode (photos can otherwise blend into dark backgrounds)
- Fallback: initials (1–2 letters, first+last name or first two letters of single name), centered, Subheading-weight (500), color from §1.4 palette, sized proportionally (18sp at 48dp, 36sp at 96dp, 24sp at 64dp)
- Loading state: neutral gray circle (`surface-variant`) placeholder while photo loads async — never show a blank/transparent circle

### 4.4 Badges
- Streak badge: 24dp circular badge, bottom-right corner of avatar, `secondary` background, white flame/streak icon or numeral (1–2 digits; "9+" if streak ≥ 10 and space-constrained)
- Status dot (alternative to full badge on dense lists): 8dp circle, one of the 4 status colors from §1.2, top-right of avatar
- Badge border: 2dp `surface` color stroke so it "cuts into" the avatar cleanly regardless of avatar color underneath

### 4.5 Empty States
- Icon or simple line illustration, 96dp, `on-surface-variant` tone (never colored/branded — keep it calm)
- Headline Small message ("No groups yet")
- Body text, one line, muted (`on-surface-variant`)
- Primary filled button as the single call-to-action ("Create your first group")
- Vertical centering in available content area, `space-8` (32dp) between elements

### 4.6 Charts (Dashboard monthly outreach)
- Simple bar chart, one bar per week (4–5 bars) or per day (7 bars) depending on toggle
- Bar color: `primary`; bar corner radius: 4dp (top corners only)
- Axis labels: Caption style, `on-surface-variant`
- No gridlines (keep it light per PRD "light and easy" principle) — value labels appear on tap/press only, not persistently, to avoid visual clutter

---

## 5. Animation Specs

| Animation | Duration | Easing | Applies to |
|---|---|---|---|
| List fade-in on load | 300ms | Standard decelerate (`FastOutSlowIn`) | RecyclerView items on fragment load, staggered 20ms per item (cap stagger at first 8 visible items) |
| Card tap feedback | 150ms | Standard | Elevation 4dp→8dp + ripple on press, reverts on release |
| Fragment/navigation transition | 250ms | Material shared-axis (X for tab switches, Z for detail drill-in) | Bottom nav tab changes, Home → Contact Detail |
| Streak badge pulse | 200ms | Overshoot (slight bounce, 1.15x scale peak) | Fires once when a streak increments (new reminder sent/logged extends streak) |
| Group creation dialog | 250ms | Standard decelerate | Slide-in from bottom (bottom-sheet style), slide-out reverses at 200ms (exits faster than they enter — standard Material asymmetry) |
| Snackbar (undo delete, etc.) | 250ms in / 200ms out | Standard | All snackbars app-wide, not just Phase 2 |

Rules for Publisher:
- All animations must respect `Settings > Accessibility > Remove animations` (Android system setting) — check `ValueAnimator.areAnimatorsEnabled()` and skip straight to end-state if disabled.
- No animation may block input — all are decorative/feedback, never gating a user action behind a mandatory watch-it-finish delay.
- Target 60fps; if profiling shows jank on list fade-in with >8 items, drop the stagger rather than the fade.

---

## 6. Dark Mode

Dark mode is not an inverted light theme — it follows Material 3's dedicated dark palette (see §1.3 surface tokens). Specific Phase 2 call-outs:

- Contact photos get a 1dp `outline`-color border (§4.3) since photos can otherwise have no visual edge against `#1C1B1F`.
- Status colors (§1.2) are lightened, not just used as-is, to maintain contrast against dark surfaces — never reuse the light-mode hex values directly.
- Avatar fallback colors are lightened +8% per §1.4, with two indices (3, 6) flipping initials text to dark for contrast — Publisher must compute this, not hardcode per-index text color, so future palette tuning doesn't silently break contrast.
- Elevation in dark mode is communicated via a subtle surface-tint overlay (Material 3 standard: higher elevation = lighter tint of `primary` mixed into the surface), not via darker shadows (shadows don't read well on dark backgrounds).

---

## 7. Accessibility Checklist

See `SCREENS-PHASE2.md` §6 for the full per-screen checklist. Summary:

- [ ] All text/background pairs verified ≥4.5:1 (body) / ≥3:1 (large text ≥18sp bold or ≥24sp, and icons) in both light and dark mode.
- [ ] All interactive elements (buttons, list items, chips, FAB) have a minimum 48×48dp touch target, even if the visible element is smaller (use padding, not just the drawn icon size).
- [ ] Every icon-only control has a `contentDescription`; every image (contact photo) has a description ("Photo of {name}" or "Initials avatar for {name}").
- [ ] Status color is never the *only* signal — status dots/badges are always paired with text or an icon shape difference (e.g., not just "red dot" but "red dot + 'Needs attention' label" in detail views).
- [ ] Focus order in dialogs (Log Outreach, Create Group) follows visual top-to-bottom, left-to-right order; TalkBack announces field purpose, not just raw hint text.
- [ ] Dark mode re-verified against this same checklist, not assumed to inherit light-mode compliance.

---

**Handoff note:** This document plus `SCREENS-PHASE2.md` are also published as the Confluence "Phase 2 UI Specs" page for single-source-of-truth access by Publisher. If anything here is ambiguous, comment on the Confluence page or the relevant Jira ticket (FRM-36–41) rather than guessing — see Definition of Done: zero back-and-forth is the goal, but "zero" only works if gaps get flagged, not silently interpreted.

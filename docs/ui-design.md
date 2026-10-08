# MediSlot — UI Design Plan

**Version:** 1.0.0
**Technical constraints:** Thymeleaf + Bootstrap 5 + custom CSS
**Design direction:** Clinical Clean + Barely-there UI
**Goal:** produce a 2026-grade appointment UI without adopting a professional frontend framework

---

## 1. Design Principles

### 1.1 Three ground rules

**First, trust outranks beauty.** A medical product's first job is to make the patient feel *safe, reliable, and calm*. That means a stable color system, clear hierarchy, and whitespace that doesn't add noise — all of which matter far more than flashy motion.

**Second, restraint reads as sophistication.** One of 2026's core UI trends is "Barely-there UI" — the interface recedes so the content becomes the protagonist. For MediSlot this means **removing every unnecessary border, shadow, and gradient**, and building hierarchy with spacing and font weight instead of decoration.

**Third, every visual element carries information.** Color distinguishes state, icons reduce cognitive load, animation confirms an action's result. Nothing is added just to look good.

### 1.2 One core concept: Clinical Clean

The mainstream direction for medical interfaces in 2026 is "Clinical Clean" — calm and professional, yet human. Its visual traits: a white surface on a light-grey base, a low-saturation blue as the trust color, rounded but restrained corners, and real human imagery rather than illustration.

MediSlot's design language follows this direction, but subtracts wherever possible so Thymeleaf + Bootstrap 5 can cover it.

---

## 2. Color System

### 2.1 Primary: a low-saturation medical blue

The 2026 trend for medical environments points clearly to **low-saturation, indigo-leaning blues**. Pantone's 2026 Color of the Year, *Cloud Dancer* (a soft white), pairs well with indigo — a recommended combination for healthcare. Low-saturation blue is easy on the eyes and softens the anxiety of a visit.

MediSlot's primary color is `#3B5BA5` (a greyish indigo), with `#5B7BC4` and `#E8EDF5` as supporting tints.

```css
:root {
  --medislot-primary: #3B5BA5;
  --medislot-primary-hover: #2E4A8A;
  --medislot-primary-subtle: #E8EDF5;
  --medislot-primary-border: #C5D1E8;
}
```

**Why this value:** Bootstrap's default primary `#0d6efd` is too bright and too "internet" for a medical setting. `#3B5BA5` is calmer, has enough contrast on white (meets WCAG AA), and doesn't feel heavy against a light-grey base.

### 2.2 Status colors

Status colors appear only where necessary — **red is reserved for cancellation and errors, never for emphasis.**

| State | Color | Usage |
|-------|-------|-------|
| Success / completed | `#2D7D5A` deep green | Booking success, completion |
| Pending | `#B8860B` amber | "Pending" status badge |
| Error / cancelled | `#B5423E` brick red | Form errors, cancellation |
| Info | `#3B5BA5` primary blue | Hints, links |

### 2.3 Neutrals

```css
:root {
  --medislot-bg: #FAFBFC;             /* page base — very light grey */
  --medislot-surface: #FFFFFF;        /* card surface */
  --medislot-border: #E5E8EC;         /* dividers and borders */
  --medislot-text-primary: #1A1D21;   /* primary text */
  --medislot-text-secondary: #6B7280; /* secondary text */
  --medislot-text-muted: #9CA3AF;     /* muted text */
}
```

**The page background is `#FAFBFC`, not pure white; cards are pure white.** That faint contrast builds the "cards float above the page" hierarchy naturally, without shadows.

### 2.4 Overriding Bootstrap

Bootstrap 5 compiles all theme colors into CSS custom properties (`--bs-primary`, `--bs-primary-bg-subtle`, …), so they can be overridden directly in `:root` — no Sass recompilation needed.

```css
:root {
  --bs-primary: #3B5BA5;
  --bs-primary-rgb: 59, 91, 165;
  --bs-primary-bg-subtle: #E8EDF5;
  --bs-primary-border-subtle: #C5D1E8;
  --bs-body-bg: #FAFBFC;
  --bs-body-color: #1A1D21;
  --bs-border-color: #E5E8EC;
}
```

**Key point:** `--bs-primary-rgb` must be overridden too, because Bootstrap's `rgba()` utilities depend on it. Skip it and `bg-primary` with opacity falls back to the default blue.

---

## 3. Typography

### 3.1 Typeface

**Headings and body share one font family; hierarchy comes from weight and size.** The 2026 "typography-led hierarchy" trend favors large headings (48–96px) and generous line height over decorative fonts.

Recommended stack:

```css
:root {
  --medislot-font-sans: 'Inter', -apple-system, BlinkMacSystemFont,
    'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

body {
  font-family: var(--medislot-font-sans);
  font-size: 15px;
  line-height: 1.6;
  color: var(--medislot-text-primary);
}
```

**Inter is the first choice**, loaded from Google Fonts, falling back to system fonts (`PingFang SC`, `Microsoft YaHei`) for CJK coverage. Only four weights are needed: 400 / 500 / 600 / 700.

### 3.2 Type scale

| Usage | Size | Weight | Line height |
|-------|------|--------|-------------|
| Page title | 28px | 600 | 1.3 |
| Section title | 20px | 600 | 1.4 |
| Card title | 16px | 600 | 1.4 |
| Body | 15px | 400 | 1.6 |
| Secondary text | 13px | 400 | 1.5 |
| Label / badge | 12px | 500 | 1.4 |

**Why body text is 15px instead of Bootstrap's default 16px:** appointment forms and lists are information-dense; 15px stays readable while fitting more on screen. The 13px tier is the second level — timestamps, phone numbers, appointment IDs.

---

## 4. Spacing & Layout

### 4.1 Spacing scale

```css
:root {
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 24px;
  --space-6: 32px;
  --space-7: 48px;
  --space-8: 64px;
}
```

**Horizontal page padding:** 32px on desktop, 24px on tablet, 16px on mobile. Bootstrap's `container` default padding is too small, so it's overridden.

**Vertical rhythm:** 48px (`space-7`) between major sections, 16–24px within a section. **Prefer more whitespace over cramped layout.**

### 4.2 Maximum width

```css
.content-wrapper {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 var(--space-5);
}
```

**960px rather than Bootstrap's 1140px / 1320px.** An appointment system doesn't need a wide canvas; 960px is comfortable on a desktop monitor and keeps form fields from stretching too long.

### 4.3 Responsive breakpoints

| Breakpoint | Width | Layout change |
|------------|-------|---------------|
| Mobile | < 576px | Single column; nav collapses to a bottom tab bar |
| Tablet | 576–991px | Doctor list in 2 columns; forms stay single-column |
| Desktop | ≥ 992px | Doctor list in 3 columns; 960px centered |

---

## 5. Radius & Borders

### 5.1 Radius strategy

In 2026's aesthetic, **large radii (16–24px) go to cards and modals, small radii (8–10px) to buttons and inputs, and micro radii (4–6px) to labels and badges.** Fully square corners look dated, but over-rounding (e.g. pill-shaped buttons) isn't serious enough for a medical context.

```css
:root {
  --radius-card: 16px;
  --radius-input: 10px;
  --radius-button: 10px;
  --radius-badge: 6px;
  --radius-avatar: 50%;
}
```

### 5.2 Borders instead of shadows

**Bootstrap's default card shadow is removed entirely in MediSlot.** A card's boundary is a 1px `--medislot-border` line. This is the core Barely-there UI move: build structure with an almost-invisible line rather than depth with shadow.

```css
.card {
  border: 1px solid var(--medislot-border);
  border-radius: var(--radius-card);
  box-shadow: none;
  background: var(--medislot-surface);
}
```

**The only place shadows survive:** dropdowns and modals. Those need to "lift off" the page, so shadow is a meaningful signal.

---

## 6. Page Layouts

### 6.1 Global navigation

**Desktop:** a horizontal top nav — the MediSlot wordmark on the left (a type mark, not a logo image), and "Log in / Register" or the user area on the right. Background `#FFFFFF`, 1px bottom border, 56px tall so it doesn't eat vertical space.

**Mobile:** the nav collapses to a bottom tab bar (three tabs for patients: Home / My Appointments / Profile). This is the common pattern for mobile booking apps and is easier to reach than a hamburger menu.

Bootstrap's `navbar` ships with a shadow and a darker background by default; override it to a plain white surface with no shadow and a thin border.

### 6.2 Home page

**Structure (top to bottom):**

1. **Greeting** — a large heading like "Who would you like to see today?", 28px, weight 600, with a 13px secondary line below: "12 doctors available".
2. **Department filter** — a horizontally scrolling set of pill tags; the selected one uses the primary background with white text, the rest use a white background with a border. **No dropdown** — pills are more intuitive on both mobile and desktop.
3. **Doctor list** — a card grid: 3 columns on desktop, 2 on tablet, 1 on mobile.

### 6.3 Doctor card

This is the system's most important information unit. It has to convey **credentials, availability, and a human touch** at once.

```
┌─────────────────────────────────┐
│  [avatar]  Zhang Minghua · Assoc. Chief Physician │
│            Cardiology                              │
│                                                    │
│          ⭐ 4.8  ·  326 appointments                │
│                                                    │
│  ┌─────────────────────────┐                       │
│  │ 3 slots left this afternoon │  ← status badge   │
│  └─────────────────────────┘                       │
└─────────────────────────────────┘
```

**Visual implementation**

- Avatar 48px, circular, using a real photo (not an illustration). When there's no photo, use the first character of the name on a primary-tinted background.
- Name 16px / weight 600; title 13px secondary color, separated by `·`.
- Rating and appointment count in 13px secondary color — not emphasized.
- Status badge: available uses `--medislot-primary-subtle` background with primary text; unavailable uses a light-grey background with muted text.
- **Hover state:** the border shifts from `#E5E8EC` to the primary `#3B5BA5` with `transition: border-color 0.15s ease`. No movement animation.

### 6.4 Schedule selection page

**Structure**

1. A compact doctor summary (a small card, not the full card).
2. **Date picker** — seven horizontal pill tags, today selected by default. Today uses a solid primary fill; other days use a white background with a border.
3. **Time-slot list** — grouped into morning / afternoon. Each slot is a row: time on the left, status badge on the right.

```
09:00 - 09:30          [3 left]
09:30 - 10:00          [Full]
10:00 - 10:30          [1 left]
```

"Full" rows are dimmed (`opacity: 0.4`) and not clickable. "1 left" uses an amber badge to signal scarcity, but **never manufactures urgency** (no blinking, no red alarms — anxiety-driven design doesn't belong in a medical context).

### 6.5 Booking confirmation page

**Stepwise confirmation, but not a multi-page wizard.** Research suggests the booking flow suits "stepwise confirmation" over a single dense page. A multi-step wizard is expensive in Thymeleaf (session or hidden-field state), so a **single page with clear sections** is the pragmatic choice:

```
┌─ Appointment ───────────────┐
│  Zhang Minghua · Cardiology │
│  Oct 15, 2026 · 09:00-09:30 │
└─────────────────────────────┘

┌─ Patient ───────────────────┐
│  Name   [____________]      │
│  Phone  [____________]      │
└─────────────────────────────┘

┌─ Reason for visit (optional) ┐
│  [________________________]  │
└──────────────────────────────┘

        [Confirm booking]
```

**Key detail:** the submit button is **not full width** — it's centered and moderate (~200px). A full-width button feels clunky on desktop and weakens the signal that "this is an important action."

### 6.6 My appointments

Grouped by status and switched with a **segmented control**: Pending / Completed / Cancelled. This is a familiar mobile pattern; Bootstrap's `btn-group` plus custom styling covers it.

Each record:

```
┌──────────────────────────────────┐
│  Zhang Minghua · Cardiology  [Pending] │
│  Wed, Oct 15   09:00 - 09:30      │
│  Appointment no. A20261015-003    │
│                      [Cancel]     │
└──────────────────────────────────┘
```

Badge colors follow the status-color mapping in §2.2. "Cancel" is a text button (`btn-link`) with no visual weight — cancellation is a low-frequency action.

---

## 7. Component Specs

### 7.1 Buttons

| Type | Background | Text | Border | Usage |
|------|------------|------|--------|-------|
| Primary | `#3B5BA5` | white | none | Book, log in |
| Secondary | white | `#3B5BA5` | 1px `#C5D1E8` | Back, secondary actions |
| Text | transparent | `#6B7280` | none | Cancel, skip |
| Danger | transparent | `#B5423E` | 1px `#E8C5C4` | Confirm cancellation |

```css
.btn-primary {
  --bs-btn-bg: var(--medislot-primary);
  --bs-btn-border-color: var(--medislot-primary);
  --bs-btn-hover-bg: var(--medislot-primary-hover);
  --bs-btn-hover-border-color: var(--medislot-primary-hover);
  --bs-btn-focus-shadow-rgb: 59, 91, 165;
  border-radius: var(--radius-button);
  padding: 10px 20px;
  font-weight: 500;
  font-size: 14px;
}
```

**Bootstrap's default button focus is a blue glow (`box-shadow`), which looks muddy on white.** Replace it with a same-color thin focus ring.

### 7.2 Form inputs

```css
.form-control {
  border: 1px solid var(--medislot-border);
  border-radius: var(--radius-input);
  padding: 10px 14px;
  font-size: 15px;
  background: var(--medislot-surface);
  transition: border-color 0.15s ease;
}
.form-control:focus {
  border-color: var(--medislot-primary);
  box-shadow: 0 0 0 3px rgba(59, 91, 165, 0.12);
}
```

**Bootstrap's default focus shadow is `rgba(13, 110, 253, 0.25)` — too bright.** A low-opacity primary tint is softer but still gives feedback.

### 7.3 Badges

```css
.badge-status {
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: var(--radius-badge);
}
.badge-pending   { background: #FDF6E3; color: #B8860B; }
.badge-completed { background: #E8F5EE; color: #2D7D5A; }
.badge-cancelled { background: #F5F0F0; color: #B5423E; }
```

**Badges use a light background with dark text, never a solid fill.** A solid fill fights for attention in a list; a light background distinguishes state well enough.

### 7.4 Empty state

Never show a blank page. Center a short message with a simple icon:

```
        [ calendar icon, linear, 32px, grey ]

            No appointments yet
          Take a look at the doctors
              [ Find a doctor ]
```

Use Bootstrap Icons (`bi-calendar-x` or `bi-journal`). Text in `--medislot-text-secondary`; the button is the primary button.

---

## 8. Responsive Strategy

### 8.1 Mobile adaptations

| Element | Desktop | Mobile |
|---------|---------|--------|
| Navigation | Top horizontal | Bottom 3-tab bar |
| Doctor list | 3-column grid | Single-column list |
| Date picker | Horizontal pills | Horizontally scrolling pills |
| Form button | Centered 200px | Full width (the only exception) |
| Card padding | 24px | 16px |

**A full-width submit button on mobile is the one justified exception** — the screen is narrow, and a centered 200px button is hard to hit with a thumb.

### 8.2 Touch targets

Every clickable element must have a hit area of **at least 44×44px** (Apple HIG). Time-slot rows are at least 48px tall; pill tags are at least 36px tall with generous horizontal padding.

---

## 9. CSS File Organization

```
static/css/
├── tokens.css              # CSS variables: color, spacing, radius, type
├── base.css                # base styles: body, type, links
├── bootstrap-override.css  # overrides for Bootstrap defaults and components
├── components.css          # custom components: doctor card, slots, badges
└── pages.css               # page-level tweaks (home, schedule, confirm)
```

**Why five files instead of one:** `layout.html` imports them in order, and each has a single responsibility. Changing colors touches only `tokens.css`; changing a component touches only `components.css`. The total stays within **300–400 lines**, so no build tool is needed.

---

## 10. Deliberately Out of Scope

The following are 2026 trends that **MediSlot phase 1 does not implement**, because they exceed the Thymeleaf + Bootstrap 5 boundary:

| Trend | Why not |
|-------|---------|
| Glassmorphism | Needs `backdrop-filter`; reduces text legibility in a medical context |
| Kinetic typography | Needs JS scroll listeners; adds complexity |
| Bento-grid home | Too information-dense for a linear booking flow |
| 3D / inflated graphics | Needs external assets; clashes with Clinical Clean |
| Dark mode | Near-standard by 2026, but a medical system prioritizes clarity on a white base. **Planned as a phase-2 extension** — the CSS-variable architecture already leaves room for it |
| Scroll-driven animation | Needs IntersectionObserver; not a priority for a practice project |
| AI personalization | Beyond the backend's scope |

---

## 11. Reference Implementation (doctor list)

The live template lives at `apps/backend/src/main/resources/templates/doctor/list.html`. A representative excerpt:

```html
<div class="row g-3">
  <div class="col-12 col-sm-6 col-lg-4" th:each="doctor : ${doctors}">
    <a class="doctor-card" th:href="@{/doctors/{id}(id=${doctor.id})}">
      <div class="doctor-card__top">
        <div class="doctor-avatar">
          <img th:if="${doctor.avatarUrl != null}" th:src="${doctor.avatarUrl}" alt="">
          <span th:unless="${doctor.avatarUrl != null}"
                th:text="${#strings.substring(doctor.name, 0, 1)}">Z</span>
        </div>
        <div>
          <div class="doctor-name" th:text="${doctor.name}">Zhang Minghua</div>
          <div class="doctor-meta">
            <span th:text="${doctor.title}">Assoc. Chief Physician</span>
            <span class="dot">·</span>
            <span th:text="${doctor.departmentName}">Cardiology</span>
          </div>
        </div>
      </div>

      <div class="doctor-card__stats">
        <span><i class="bi bi-star-fill"></i> <span th:text="${doctor.rating}">4.8</span></span>
        <span class="dot">·</span>
        <span th:text="${doctor.appointmentCount}">326</span>
      </div>

      <div class="doctor-card__footer">
        <span class="badge-status"
              th:classappend="${doctor.hasAvailableSlot} ? 'badge-available' : 'badge-full'"
              th:text="${doctor.hasAvailableSlot ? 'Available today' : 'Fully booked today'}">
          Available today
        </span>
      </div>
    </a>
  </div>
</div>
```

And the key part of `components.css`:

```css
.doctor-card {
  display: block;
  height: 100%;
  background: var(--medislot-surface);
  border: 1px solid var(--medislot-border);
  border-radius: var(--radius-card);
  padding: var(--space-5);
  color: inherit;
  transition: border-color 0.15s ease;
}
.doctor-card:hover { border-color: var(--medislot-primary); }
.doctor-card__top {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}
.doctor-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-avatar);
  background: var(--medislot-primary-subtle);
  color: var(--medislot-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 18px;
  flex-shrink: 0;
  overflow: hidden;
}
.doctor-avatar img { width: 100%; height: 100%; object-fit: cover; }
.badge-available { background: var(--medislot-primary-subtle); color: var(--medislot-primary); }
.badge-full      { background: #F3F4F6; color: var(--medislot-text-muted); }
```

---

## 12. Implementation Order

| Step | Work | Output |
|------|------|--------|
| 1 | Write `tokens.css` | All color, spacing, radius, and type variables defined |
| 2 | Write `base.css` + `bootstrap-override.css` | Global background, type, button, form, card overrides |
| 3 | Build `layout.html` | Shared frame with nav and footer |
| 4 | Doctor list page | Validate card design, responsive grid, department filter |
| 5 | Schedule selection page | Validate date pills, slot list, status badges |
| 6 | Booking confirmation page | Validate form styling and button layout |
| 7 | My appointments page | Validate the segmented control and badge system |
| 8 | Login / register pages | Validate form-style reuse |

**After each step, open it on a phone.** Catch responsive issues early rather than adjusting everything at the end.

---

## 13. In One Sentence

**MediSlot's UI goal is to feel like a *trustworthy tool*, not a *pretty product*.** Low-saturation blue, generous whitespace, hairline borders, restrained status colors — every visual decision serves "trust" and "efficiency." Bootstrap 5 provides structure, custom CSS provides the aesthetic, and Thymeleaf renders it. **No frontend framework required; the design is fully achievable within the current stack.**

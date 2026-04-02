# AGENTS.md

Primary agent instructions for this repository live here.

This file provides guidance to coding agents when working with code in this repository.

## Project

**Chronicals** — Self-hosted personal health diary with a calendar UI. Logs symptoms (headache, stomachache, allergy, hypo, medication changes) with start/stop times, auto-fetches weather/pollen/pressure from Open-Meteo, and surfaces correlations in an Analysis tab.

## Tech Stack

- **Framework**: SvelteKit (TypeScript, Svelte 5 runes)
- **Database**: SQLite via `better-sqlite3` + Drizzle ORM
- **Styling**: TailwindCSS v4 (`@tailwindcss/vite` plugin)
- **Deployment**: Docker + docker-compose (primary)

## Commands

```bash
npm run dev          # Start dev server (http://localhost:5173)
npm run build        # Production build
npm run preview      # Preview production build

npx drizzle-kit generate   # Generate migration after schema changes
npx drizzle-kit migrate    # Apply migrations (also runs on startup automatically)
npx svelte-kit sync        # Regenerate $types after adding routes

docker compose up -d       # Start self-hosted (port 3000, SQLite in volume)
docker compose up --build  # Rebuild and start
```

> **Build tip**: If `npm run build` fails with `ENOENT: service-worker.js not found`, the real cause is usually a Svelte compile error in a component. The PWA plugin's `closeBundle` hook masks the original error. Check for `{@const}` in invalid positions (must be an immediate child of `{#each}`, `{#if}`, `{:else}`, etc. - not inside HTML elements).

## Architecture

```
src/
├── lib/
│   ├── db/
│   │   ├── schema.ts        ← All Drizzle table definitions (9 tables)
│   │   └── index.ts         ← SQLite connection (WAL + FK), auto-migrate, seed
│   ├── server/
│   │   ├── seed.ts          ← Built-in entry types (headache/stomachache/allergy/hypo/med-change/note) + triggers
│   │   ├── weather.ts       ← Open-Meteo fetch (weather + pollen + pressure delta)
│   │   └── queries/         ← Server-only DB query functions
│   │       ├── entries.ts         ← Entry CRUD + dot data for calendar
│   │       ├── entryTypeLogs.ts   ← Per-log CRUD
│   │       ├── entryTypes.ts      ← Entry type CRUD
│   │       ├── triggers.ts        ← Trigger CRUD
│   │       ├── profile.ts         ← User profile + medications + medication changes
│   │       └── analysis.ts        ← Phi coefficient correlations + stats
│   ├── types/index.ts       ← All shared TypeScript interfaces
│   ├── stores/theme.ts      ← System/light/dark theme (localStorage)
│   ├── utils/date.ts        ← Date helpers + intensityColor()
│   └── components/
│       ├── calendar/        ← CalendarMonth, CalendarWeek, CalendarDay, CalendarCell, DayDot, MonthNav
│       ├── entry/           ← EntryTypePicker, EntryDrawer, EnvironmentalCard
│       ├── symptom/         ← MetricForm (dynamic form from JSON schema)
│       └── trigger/         ← TriggerLogger
├── routes/
│   ├── calendar/            ← Month view (+page.svelte) + week/ + day/[date]/
│   ├── analysis/            ← Correlation analysis tab
│   ├── settings/            ← profile/, medications/, entry-types/, triggers/
│   └── api/                 ← All REST endpoints under /api/*
migrations/                  ← Drizzle-generated SQL (auto-applied on startup)
Dockerfile                   ← Multi-stage Node 22 Alpine, 256MB mem limit
docker-compose.yml           ← Port 3000, named volume for SQLite
```

## Key Design Decisions

- **Flexible metric schema**: Each entry type stores a `metric_schema` JSON column defining what fields to collect. `MetricForm.svelte` renders these dynamically.
- **Pain colors by type, not severity**: Each entry type has a fixed hex color; intensity (1–5) controls opacity via `intensityColor(color, intensity)`.
- **Pressure delta**: `environmental_data.pressure_delta_hpa` = today's pressure minus yesterday's. Computed on fetch.
- **Special event markers**: Hypo (⚡) and Medication Change (💊) rendered as icon markers on calendar cells, not dots.
- **Analysis**: Phi coefficient (φ) for binary correlations. φ > 0.2 = "possible", φ > 0.4 = "strong". Environmental variables binned at median before Phi calculation.

## Environment Variables

```
DATABASE_PATH   Path to SQLite file (default: ./chronicals.db)
ORIGIN          Public URL for CSRF protection (default: http://localhost:3000)
PORT            Server port (default: 3000)
```

---

### 1. Plan Node Default
- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately - don't keep pushing
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

---

### 2. Subagent Strategy
- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One task per subagent for focused execution

---

### 3. Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

---

### 4. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

---

### 5. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes - don't over-engineer
- Challenge your own work before presenting it

---

### 6. Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests - then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

---

## Task Management
1. **Plan First**: Write plan to `tasks/todo.md` with checkable items
2. **Verify Plan**: Check in before starting implementation
3. **Track Progress**: Mark items complete as you go
4. **Explain Changes**: High-level summary at each step
5. **Document Results**: Add review section to `tasks/todo.md`
6. **Capture Lessons**: Update `tasks/lessons.md` after corrections

---

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Impact minimal code
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards

---

## Local Workflow Rules
- Always commit repository changes after completing a change set.
- After committing, always rebuild and restart the `chronicals` web UI container so the latest changes are visible locally.

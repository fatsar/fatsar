# Architecture

## Goals

1. **Correctness must be inspectable.** Every number a purchasing manager acts on
   is produced by a pure, unit-tested function that can explain itself.
2. **One repository, no extra infrastructure.** Next.js serves UI and server
   logic; SQLite is a file. Nothing else is required to run the product.
3. **Portable to PostgreSQL** without rewriting business logic.
4. **Integrations stay at the edges** so the core never depends on a vendor.

## Layers

```
┌─────────────────────────────────────────────────────────────┐
│ src/app          Next.js App Router                         │
│                  server components fetch, client components │
│                  ("use client") handle interaction only     │
├─────────────────────────────────────────────────────────────┤
│ src/components   presentation: ui/* primitives,             │
│                  layout/*, domain-badges                    │
├─────────────────────────────────────────────────────────────┤
│ src/server       material-insights · dashboard · settings   │
│                  actions/* ("use server", zod-validated)    │
├─────────────────────────────────────────────────────────────┤
│ src/domain       PURE logic — no prisma, next or react      │
│                  the only place formulas exist              │
├─────────────────────────────────────────────────────────────┤
│ src/lib          prisma client · validation · format · cn   │
├─────────────────────────────────────────────────────────────┤
│ Prisma 7 + better-sqlite3 driver adapter → prisma/dev.db    │
└─────────────────────────────────────────────────────────────┘
        src/services/*  external seams (ai/ only, unimplemented)
```

The dependency arrow points one way: `app → server → domain`. `domain` imports
nothing from the layers above it, which is what makes it testable in isolation
and portable if the UI framework ever changes.

## Why the read side is centralized

`src/server/material-insights.ts` loads materials with their relations,
quotations and active intelligence entries, then runs **every** engine once per
material and returns a `MaterialInsight[]`.

Dashboard, Materials, Purchasing Plan and material detail all consume that same
function. This is the mechanism that guarantees the coverage shown on the
dashboard equals the coverage on the material page — the numbers cannot drift
apart because they are computed exactly once, in one place.

It also keeps query volume bounded: three queries plus per-supplier score
memoization, regardless of how many materials are rendered.

## Mutations

Every write is a `"use server"` action in `src/server/actions/`:

1. `formDataToObject(formData)` — normalizes `FormData` (repeated checkbox keys
   become arrays, Next.js internal `$` fields are dropped).
2. `parseForm(schema, data)` — zod parse; on failure returns a
   `FormState { ok: false, fieldErrors }` that the form renders inline per field.
3. Business guards — unique codes, unit convertibility, delete-safety checks.
4. Prisma write, then `revalidatePath` for every affected route.

Client forms use `useActionState`, so validation errors and success messages
round-trip without custom state plumbing. `FormState` is the single contract
between actions and forms.

## Configuration model

Weights and thresholds are **never** hard-coded at a call site:

- `src/domain/config.ts` — typed defaults, documented per field.
- `Setting` table — JSON values, one row per group.
- `src/server/settings.ts` — typed accessors that merge stored values over
  defaults, so adding a new parameter never breaks an existing database, and
  malformed JSON degrades to defaults instead of crashing.
- Settings page writes them back and revalidates the whole layout.

## SQLite → PostgreSQL portability

| Concession to SQLite | Migration step |
| --- | --- |
| Classification columns are `String`, not Prisma enums | Convert to enums; the unions in `src/domain/enums.ts` are already the single source |
| Money/quantities are `Float` | Switch to `Decimal` if exact decimal arithmetic is needed |
| Search uses `LIKE` via `contains` | Move to `ILIKE`/`pg_trgm` or full-text search |
| Driver adapter is `@prisma/adapter-better-sqlite3` | Swap for the Postgres adapter in `src/lib/prisma.ts` (single file) |

No business logic changes in any row of that table.

## Rendering strategy

Every route is `force-dynamic` (declared once in the root layout): procurement
data changes constantly and a stale cached dashboard would be actively
misleading. Only the manifest and icon are static.

Client components are kept to what genuinely needs interactivity — tables
(sort/filter/paginate), forms, dialogs, the search box and charts. Everything
else renders on the server.

## Charts

Recharts, wrapped in `src/app/price-history/price-chart.tsx`, following a
validated visualization spec:

- **Categorical palette** (fixed order, never cycled or re-assigned):
  `#2a78d6` blue, `#eb6834` orange, `#1baf7a` aqua, `#eda100` yellow. Verified
  for colour-vision-deficiency separation and lightness band against the
  `#fcfcfb` chart surface.
- Series colours are keyed to the material's **full supplier list**, so filtering
  suppliers never repaints the survivors.
- 2px lines with round joins; ≥8px dots carrying a 2px surface-coloured ring;
  hairline solid horizontal gridlines; no vertical grid.
- Legend whenever ≥2 series are visible; the target price is a dashed reference
  line labelled with its value.
- Axis and label text uses ink tokens, never a series colour. Every plotted point
  is also present in the table beneath the chart, so the data is never
  colour-gated.

## Security posture

- UI, business logic, persistence and integrations are separate layers; the
  browser never talks to the database.
- Secrets come from environment variables only; `.env` is git-ignored and
  `.env.example` documents every key. Nothing sensitive is `NEXT_PUBLIC_*`.
- No provider SDK may be imported outside `src/services/ai` — that boundary is
  what keeps API keys server-side by construction.
- **Phase 1 has no authentication.** The app assumes a trusted internal network.
  Auth is the first Phase 2 security item.

## Future integration seams

`src/services/ai/index.ts` defines `AiAnalystProvider` (`ask(context) → answer`)
plus a registry function, and ships a `NotConfiguredProvider` that throws a clear
message. A future analyst receives **pre-computed** facts from
`material-insights` — it explains and prioritizes, it never replaces the
deterministic engines, and it can be swapped by changing one env var.

Outlook, Mikro ERP, FX, commodity, freight and news feeds get sibling
directories under `src/services/` when they arrive. Core logic must never import
them directly; adapters map external shapes onto domain types at the boundary.

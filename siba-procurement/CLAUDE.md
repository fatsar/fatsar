@AGENTS.md

# Siba Procurement Command Center — project instructions

Internal procurement intelligence web app for **Siba Kimya** (chemical
manufacturing). Primary user: the **Purchasing Manager**. The app must answer,
within ~30 seconds of opening: what to buy, when, from whom, at what target
price, what needs attention now, and where supply/price/geopolitical risk sits.

**This is a decision tool, not an ERP.** No GL, no invoicing, no production
planning, no warehouse management. If a feature does not help make a purchasing
decision, it does not belong here.

## Commands

```bash
npm run dev            # dev server (http://localhost:3000)
npm run build          # prisma generate + next build
npm run start          # production server
npm run lint           # eslint (must be clean)
npm run typecheck      # tsc --noEmit (must be clean)
npm test               # vitest — domain engine unit tests
npm run test:e2e       # Playwright acceptance walkthrough (server must be up)
npm run db:migrate     # prisma migrate dev
npm run db:seed        # reset + reseed SAMPLE data
npm run db:reset       # drop, re-migrate, reseed
```

## Architecture (hard rules)

```
src/domain/    PURE business logic — no imports from prisma/next/react. Unit-tested.
src/server/    DB reads (material-insights, dashboard, settings) + server actions.
src/lib/       prisma client, zod validation, formatting, cn.
src/app/       Next.js App Router pages + colocated client components.
src/components/ Reusable UI (ui/*) and domain badges.
src/services/  External integration seams (ai/ only, unimplemented by design).
```

Data flow is one-directional: **page (server component) → `src/server` → `src/domain` → render.**

- **Never put a calculation in a React component.** Coverage, risk,
  recommendations, scoring and price statistics live in `src/domain` only.
- **Never read Prisma from a client component.** Server components and server
  actions only.
- **`src/server/material-insights.ts` is the single read-side source** for
  per-material numbers. Dashboard, Materials, Purchasing Plan and material
  detail all consume it, so figures can never disagree between screens.
- **Every mutation goes through zod** (`src/lib/validation.ts`) inside a
  `"use server"` action returning `FormState`. No exceptions.
- **No hard-coded weights or thresholds.** Defaults live in
  `src/domain/config.ts`; live values come from the `Setting` table via
  `src/server/settings.ts` and are editable in Settings.
- **Enums:** SQLite can't enforce them, so classification columns are `String`
  with unions defined once in `src/domain/enums.ts` and enforced by zod.
  Add a value there and nowhere else.

## Procurement domain rules

Full derivations: `docs/procurement-rules.md`. The essentials:

- `usableStock = currentStock − reservedStock`
- `coverageDays = usableStock / avgDailyConsumption` (daily falls back to monthly ÷ 30)
- **Projected coverage** is a day-by-day simulation: arrivals credited at the
  start of their day (inbound at its ETA, open PO after one lead time),
  consumption at the end, at `max(average, forecast)` demand.
- `reorderPointQty = avgDailyConsumption × leadTimeDays + safetyStock`,
  compared against `inventoryPosition = usable + openPO + inbound`.
- **Risk** = additive rule points → LOW/MEDIUM/HIGH/CRITICAL. The four stock
  rules are one family: only the worst applies (they must never stack).
- **Recommendation** = first-match decision tree (steps 0→5c). Order matters:
  urgency before price. Below safety stock ⇒ buy even if the price is above
  target; comfortable stock + price above target ⇒ WAIT.
- **Confidence is NOT AI.** It starts at 100, subtracts documented penalties for
  missing/stale inputs and borderline comparisons, clamps to 25–95, and returns
  every deduction so the UI can show the arithmetic.
- **Every risk level and every recommendation must ship its reasons** with real
  numbers. An unexplained score is a bug.
- **Normalized price** is the quoted price converted to the material's base unit
  (`convertPrice`). Only mass units interconvert (kg ↔ MT); L/pcs never cross
  dimensions because no density is modelled.
- **No FX conversion exists.** Price statistics use only quotations in the
  material's own currency and the UI states how many were excluded. In offer
  comparison, mixed currencies drop the price dimension and warn.
- **Expired offers stay visible but can never win a badge** (LOWEST PRICE, BEST
  OVERALL, …). An offer that can't be accepted must not be presented as best.

## UI conventions

- Professional, dense, light single-theme industrial styling. Design tokens in
  `src/app/globals.css` (`--color-ink`, `--color-nav`, status families…).
- **Risk and status colors are secondary.** The text level (`HIGH`, `EXPIRED`,
  `OVERDUE`) is always rendered — never color alone.
- Tables use `DataTable` (sort/filter/pagination, `hideOnMobile` columns).
  Numeric cells get `.tnum` for tabular figures.
- Charts follow the validated palette in `docs/architecture.md`: fixed series
  order (never re-assigned when filtering), 2px lines, ≥8px dots with a 2px
  surface ring, hairline solid grid, legend for ≥2 series, text in ink tokens.
- Formatting only via `src/lib/format.ts`. No ad-hoc `toFixed` in pages.
- Desktop-first, but every screen must work at 390px wide with no horizontal
  body scroll (wide tables scroll inside their own container).

## Sample data

The seed marks every row `isSample: true`; the layout shows a persistent SAMPLE
banner and rows carry a SAMPLE badge. **Never present sample figures as real
purchasing data.** Editing a sample record clears its flag (`isSample: false`).

## Prohibited

- Microservices, Kubernetes, Redis, queues, or any infra beyond this repo.
- Business logic in components; duplicated formulas; magic numbers.
- Provider SDKs (OpenAI/Anthropic/…) imported outside `src/services/ai`.
- Secrets in client code or anything `NEXT_PUBLIC_*` that isn't public.
- Hard-deleting records that carry history — deactivate instead.
- Silently dropping invalid rows on import (Phase 2), or unexplained scores.
- Claiming a feature works without running lint, typecheck, tests and the app.

## Implementation status

**Phase 1 — complete and validated.** Dashboard (8 KPIs + priority actions),
Materials CRUD, Suppliers CRUD with score breakdown, Quotations CRUD +
comparison with explainable Best Overall, Price History (chart + metrics),
Purchasing Plan, Action Center, Market Intelligence entry, Settings (live
weights/thresholds), global search, PWA manifest, 42 unit tests, 55-check
Playwright walkthrough.

**Not implemented (by design):** CSV/XLSX import (UI placeholder + documented
format), AI analyst (seam only), Outlook/Mikro/FX/freight integrations, auth
and user management, PDF quotation extraction, FX conversion, service worker.

**Phase 2 candidates:** the import module, FX rates for cross-currency
comparison, purchase orders as first-class records (replacing the
`openPurchaseQty`/`inboundQty` fields), multiple supplier contacts,
authentication, then the AI analyst on top of the existing insight structs.

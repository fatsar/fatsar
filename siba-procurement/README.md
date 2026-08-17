# Siba Procurement Command Center

Internal procurement intelligence web application for **Siba Kimya**, a chemical
manufacturing company. Built for the **Purchasing Manager** to answer six
questions fast:

1. What should we buy?
2. When should we buy it?
3. From which supplier?
4. At what target price?
5. Which items need immediate attention?
6. Which raw materials carry supply, price or geopolitical risk?

It is deliberately **not** an ERP. Every screen exists to support a purchasing
decision — stock coverage, supplier intelligence, price history, market risk and
explainable recommendations.

> **Phase 1 ships with SAMPLE data.** Every seeded row is flagged and the UI
> shows a persistent SAMPLE banner. None of the seeded prices, stocks, ratings
> or market notes are real Siba Kimya or supplier data.

## Stack

Next.js 16 (App Router, React 19) · TypeScript (strict) · Tailwind CSS v4 ·
Prisma 7 ORM · SQLite in development (PostgreSQL-ready) · Recharts · Vitest ·
Playwright. Frontend and backend live in this one repository; no extra
infrastructure is required.

## Quick start

```bash
cd siba-procurement
npm install                 # postinstall runs `prisma generate`
cp .env.example .env        # DATABASE_URL="file:./prisma/dev.db"
npm run db:migrate          # create the SQLite schema
npm run db:seed             # load SAMPLE data
npm run dev                 # http://localhost:3000
```

The database file is `prisma/dev.db` (git-ignored). It persists across restarts —
stopping and restarting the server never loses data.

## Commands

| Command | Purpose |
| --- | --- |
| `npm run dev` | Development server |
| `npm run build` / `npm run start` | Production build / serve |
| `npm run lint` | ESLint |
| `npm run typecheck` | `tsc --noEmit` |
| `npm test` | Vitest — domain engine unit tests |
| `npm run test:e2e` | Playwright acceptance walkthrough (needs a running server) |
| `npm run db:migrate` | `prisma migrate dev` |
| `npm run db:seed` | Wipe and reload SAMPLE data |
| `npm run db:reset` | Drop, re-migrate, reseed |

## Repository structure

```
siba-procurement/
├── prisma/
│   ├── schema.prisma            # Material, Supplier, MaterialSupplier, Quotation,
│   │                            # ActionItem, MarketIntelligence, Setting
│   ├── migrations/              # committed SQL migrations
│   └── seed.ts                  # SAMPLE data (isSample: true on every row)
├── scripts/smoke-test.mjs       # end-to-end acceptance walkthrough
├── src/
│   ├── domain/                  # PURE business logic (no prisma/next/react)
│   │   ├── enums.ts             # every classification union, defined once
│   │   ├── config.ts            # default weights & thresholds
│   │   ├── units.ts             # unit/price conversion (kg ↔ MT)
│   │   ├── stock-coverage.ts    # coverage, projection, reorder point
│   │   ├── risk.ts              # deterministic risk engine + reasons
│   │   ├── recommendation.ts    # decision tree + explainable confidence
│   │   ├── offer-comparison.ts  # Best Overall Offer scoring
│   │   ├── supplier-score.ts    # weighted 0–100 supplier score
│   │   ├── price-stats.ts       # averages, extremes, target deltas
│   │   ├── intel-match.ts       # market alert → material matching
│   │   └── __tests__/           # 42 unit tests
│   ├── server/
│   │   ├── material-insights.ts # single read-side source for all screens
│   │   ├── dashboard.ts         # KPI aggregation
│   │   ├── settings.ts          # typed Setting accessors
│   │   └── actions/             # "use server" mutations (zod-validated)
│   ├── lib/                     # prisma client, validation, formatting
│   ├── components/              # ui/* primitives, layout, domain badges
│   ├── services/ai/             # future AI analyst seam (no provider SDKs)
│   └── app/                     # routes (see navigation below)
└── docs/                        # requirements, architecture, schema, rules, imports
```

## Navigation

| Section | State |
| --- | --- |
| Dashboard | 8 KPI cards, Priority Purchasing Actions, open actions, market alerts |
| Materials | Full CRUD, live coverage/risk/recommendation per material |
| Suppliers | Full CRUD, 0–100 score with per-dimension breakdown |
| Quotations | CRUD, normalized prices, comparison screen with winner badges |
| Purchasing Plan | All materials ranked by urgency with reorder-point maths |
| Price History | Filterable chart + deterministic metrics table |
| Market Intelligence | Manual entry of market/macro/geopolitical/freight signals |
| Action Center | Procurement tasks with overdue highlighting |
| Files / Imports | Placeholder — format documented, module is Phase 2 |
| Settings | Live editing of scoring weights and engine thresholds |

## How decisions are computed

All calculations are **deterministic** — no LLM is involved anywhere in Phase 1.

- `usableStock = currentStock − reservedStock`
- `coverageDays = usableStock / avgDailyConsumption`
- **Projected coverage** simulates day by day, crediting confirmed inbound at its
  ETA and open purchase orders after one lead time, consuming at
  `max(average, forecast)` demand.
- `reorderPoint = avgDailyConsumption × leadTime + safetyStock`
- **Risk** (LOW/MEDIUM/HIGH/CRITICAL) sums rule points and always lists the
  triggered reasons with real numbers.
- **Recommendation** (URGENT PURCHASE, BUY NOW, SECURE SUPPLY, REQUEST
  QUOTATION, NEGOTIATE, WAIT, MONITOR) follows a documented first-match decision
  tree and lists its reasons.
- **Confidence** is a data-quality measure, not an AI score: it starts at 100 and
  subtracts documented penalties for missing/stale inputs and borderline
  comparisons (clamped 25–95). The UI shows every deduction.
- **Best Overall Offer** weights price, payment terms, lead time and supplier
  score; the full per-dimension arithmetic is displayed on the comparison screen.

Weights and thresholds are editable in **Settings** and take effect immediately.
Derivations live in [`docs/procurement-rules.md`](docs/procurement-rules.md).

## Testing

```bash
npm run lint && npm run typecheck && npm test    # static + unit
npm run build && npm run start                   # then, in another shell:
npm run test:e2e                                 # browser walkthrough
npm run db:seed                                  # restore clean SAMPLE data
```

`npm test` covers unit conversion, coverage/projection, risk, the recommendation
tree, supplier scoring, offer comparison, price statistics and intel matching.
`npm run test:e2e` drives a real browser through the Phase 1 acceptance criteria
(create/edit/deactivate a material, create a supplier and a quotation, compare
offers, price trend, settings validation, global search, mobile layout) and
writes screenshots to `./shots`.

## PWA

A web manifest and icon are served, so the app installs from a browser on
Windows, Android and tablets. Offline support (service worker) is intentionally
out of Phase 1 scope. No native Android app is planned.

## Security

Procurement data is confidential company information.

- UI, business logic, database access and external integrations are separated.
- All secrets come from environment variables; `.env` is git-ignored and
  `.env.example` documents every key.
- No secret is ever exposed to the browser (nothing sensitive is `NEXT_PUBLIC_*`).
- Phase 1 has **no authentication** — deploy it only on a trusted internal
  network until auth is added (Phase 2).

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — persistent instructions for future Claude Code sessions
- [`docs/product-requirements.md`](docs/product-requirements.md)
- [`docs/architecture.md`](docs/architecture.md)
- [`docs/database-schema.md`](docs/database-schema.md)
- [`docs/procurement-rules.md`](docs/procurement-rules.md)
- [`docs/import-format.md`](docs/import-format.md)

## Known limitations

- No authentication or user management — single trusted-user assumption.
- No FX conversion: price statistics use one currency per material and the UI
  reports how many quotations were excluded; mixed-currency comparisons drop the
  price dimension with a warning.
- Purchase orders are scalar fields on the material (`openPurchaseQty`,
  `inboundQty`, `inboundEta`) rather than first-class records.
- One contact person per supplier.
- CSV/XLSX import, PDF extraction, Outlook/Mikro/market-API integrations and the
  AI analyst are not implemented — only their architectural seams exist.
- SQLite stores money/quantities as `Float`; move to `Decimal` when migrating to
  PostgreSQL if exact decimal arithmetic becomes a requirement.

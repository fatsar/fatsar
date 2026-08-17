# Database schema

Source of truth: `prisma/schema.prisma`. Migrations are committed under
`prisma/migrations/`. Development database: `prisma/dev.db` (SQLite, git-ignored).

## Conventions

- IDs are `cuid()` strings.
- Every table carries `createdAt` / `updatedAt`; the business tables also carry
  **`isSample`**, which flags development fixtures. Editing a record through the
  UI sets it to `false`.
- Classification columns are `String`, because SQLite cannot enforce Prisma
  enums. The allowed values live once in `src/domain/enums.ts` and are enforced
  by zod on every write. On PostgreSQL these become real enums.
- Quantities and money are `Float` (SQLite has no `Decimal`). All quantities for
  a material are expressed in its `unit`.

## Entity relationships

```
Supplier ──1:N──────────────► Quotation ◄──────────────N:1── Material
    │  ▲                          │                            │  │
    │  └── preferredFor ──────────┼──── preferredSupplier ─────┘  │
    │                             │                               │
    └────── MaterialSupplier ─────┼───────────────────────────────┘
              (approved list)     │
                                  ▼
Material ──1:N──► ActionItem ◄──1:N── Supplier / Quotation

MarketIntelligence  — linked to materials by TEXT tokens, not FK (see below)
Setting             — key/value JSON, no relations
```

## Material

Master record plus the live operational figures the engines read.

| Group | Fields |
| --- | --- |
| Identity | `code` (unique), `name`, `category`, `unit`, `specification`, `isActive`, `notes` |
| Suppliers | `preferredSupplierId` → Supplier, `suppliers` → MaterialSupplier[] |
| Consumption | `avgMonthlyConsumption`, `avgDailyConsumption`, `forecastMonthlyDemand` |
| Stock | `currentStock`, `reservedStock`, `safetyStock`, `minimumStock` |
| Replenishment | `leadTimeDays`, `openPurchaseQty`, `inboundQty`, `inboundEta` |
| Pricing | `lastPurchasePrice`, `lastPurchaseDate`, `targetPrice`, `currency`, `incoterm` |

Indexed on `category` and `isActive`.

**Derived, never stored** (recomputed on every read so they can never go stale):
usable stock, coverage days, projected coverage, reorder point, inventory
position, risk level, recommendation, price statistics.

`usableStock = currentStock − reservedStock` is intentionally not a column —
storing it would allow it to disagree with its inputs.

## Supplier

| Group | Fields |
| --- | --- |
| Identity | `code` (unique), `name`, `country`, `city`, `website`, `isActive`, `notes` |
| Contact | `contactName`, `contactEmail`, `contactPhone` |
| Terms | `preferredCurrency`, `defaultIncoterm`, `paymentTerms` (text), `paymentTermDays` (numeric), `normalLeadTimeDays` |
| Ratings 0–10 | `qualityRating`, `pricingRating`, `deliveryRating`, `responsivenessRating`, `technicalRating`, `commercialRating` |

Payment terms are stored twice on purpose: the text is what the contract says
("LC 60 days"), the number is what the comparison engine can compute with.

The 0–100 supplier score is **not** a column — it depends on weights that are
editable in Settings, so it is always derived.

## MaterialSupplier

Join table for the approved-supplier relationship, unique on
`(materialId, supplierId)`, cascading on delete. Carries `isApproved` and an
optional `note` so an approval can later gain qualification metadata without a
schema change.

## Quotation

| Group | Fields |
| --- | --- |
| Identity | `quotationNumber`, `supplierId`, `materialId`, `quotationDate`, `status` |
| Quantity | `quantity`, `quantityUnit`, `containerCount`, `qtyPerContainer` |
| Price | `price`, `currency`, `priceUnit`, **`normalizedPrice`** |
| Terms | `incoterm`, `destinationPort`, `paymentTerms`, `paymentTermDays`, `freightIncluded` |
| Timing | `leadTimeDays`, `productionLeadTimeDays`, `validUntil` |
| Misc | `remarks`, `attachmentRef` |

`quotationNumber` is **not** globally unique — two suppliers may legitimately use
the same document number. It is indexed for search.

`normalizedPrice` is the one stored derived value in the schema: it is computed
at write time from `price`/`priceUnit` into the material's base unit. Storing it
keeps historical price series stable and makes comparison queries trivial;
`updateQuotation` always recomputes it.

Indexed on `(materialId, quotationDate)` — the access pattern of every price
chart — plus `supplierId` and `quotationNumber`.

## ActionItem

`title`, optional `materialId` / `supplierId` / `quotationId`, `actionType`,
`owner`, `dueDate`, `priority`, `status`, `notes`. Indexed on `status` and
`dueDate` (overdue queries). All three relations are optional: a market check may
belong to no supplier, a general task to no material.

## MarketIntelligence

`date`, `title`, `category`, **`affectedMaterials`**, `geography`, `summary`,
`expectedImpact`, `priceDirection`, `supplyImpact`, `riskLevel`, `source`, `url`,
`notes`, `isActive`. Indexed on `(isActive, date)`.

`affectedMaterials` is deliberately **text, not a foreign key**: a real signal is
usually about a category or a region ("Chinese silicone", "Gulf shipping"), not a
material ID, and it must be recordable in seconds while the news is fresh. It
accepts a comma-separated list of material codes and/or category names, or `ALL`;
matching is exact, case-insensitive tokens (`src/domain/intel-match.ts`). A
first-class link table is a Phase 2 option if curation gets heavier.

Only `isActive` entries with `riskLevel` HIGH or CRITICAL feed the risk engine.

## Setting

`key` (PK), `value` (JSON string), `updatedAt`. Three rows in Phase 1:
`supplierScoreWeights`, `offerScoreWeights`, `recommendationConfig`. Stored
values are merged over the code defaults in `src/domain/config.ts`, so adding a
parameter never invalidates an existing database.

## Sample data

`prisma/seed.ts` wipes and recreates: 9 suppliers, 19 materials (PMDI, Polyol 56
/120/160/250-2/250-3/400, HS3130 Transparent/White/Black, OH Polymer 20K/50K/80K,
CP52, CP45, DME, Methanol, Isobutane, Hexane), ~92 quotations including 12-month
series, 10 actions and 7 intelligence entries — everything `isSample: true`.

The fixtures are shaped to exercise each engine path: PMDI has an expired latest
offer (→ REQUEST QUOTATION), OH Polymer 80K sits below safety stock
(→ URGENT PURCHASE), DME has inbound cargo shifting its projection, CP52 is
single-sourced under a HIGH market alert (→ SECURE SUPPLY), HS3130 Transparent is
comfortable but above target (→ WAIT), and CP52/CP45 are quoted in USD/MT to
exercise price normalization.

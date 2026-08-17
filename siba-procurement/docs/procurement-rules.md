# Procurement rules

The authoritative description of every calculation in the system. All of it is
**deterministic** — the same inputs always give the same output, and no LLM is
involved. Code: `src/domain/*`. Tests: `src/domain/__tests__/*`.

Tunable values live in `src/domain/config.ts` (defaults) and the `Setting` table
(live values, edited in **Settings**). Values shown below are the defaults.

---

## 1. Units and normalization

`src/domain/units.ts`

Each material has a **base unit** (`kg`, `MT`, `L`, `pcs`). Stock, consumption
and normalized prices are all expressed in it.

- Mass units interconvert: `1 MT = 1000 kg`.
- `L` and `pcs` never convert across dimensions — that would need a density,
  which Phase 1 does not model. Attempting it throws.
- **Normalized price** = quoted price converted to *money per base unit*:
  `865 USD/MT → 0.865 USD/kg`. Stored on the quotation at write time, so history
  is stable even if conversion code changes later.
- Quotation forms reject a price or quantity unit that cannot convert to the
  material's base unit.

**No FX conversion exists.** Consequences, both surfaced in the UI:

- Price statistics use only quotations in the material's own currency; the count
  of excluded quotations is displayed.
- Offer comparison with mixed currencies drops the price dimension from the
  overall score and shows a warning.

---

## 2. Stock coverage

`src/domain/stock-coverage.ts`

```
usableStock   = max(currentStock − reservedStock, 0)
dailyDemand   = avgDailyConsumption
                ?? avgMonthlyConsumption / 30
                ?? forecastMonthlyDemand / 30      (first available wins)
coverageDays  = usableStock / dailyDemand
safetyDays    = safetyStock / dailyDemand
```

If no consumption figure exists, coverage is `null` — never zero, never guessed.
The UI shows "—" and the engines raise an explicit "no consumption data" signal.

### Projected coverage

The basic figure ignores replenishment, so a day-by-day simulation runs over a
365-day horizon:

- **Arrivals credited at the start of a day:** confirmed `inboundQty` on its
  `inboundEta` (a past ETA counts as day 1); `openPurchaseQty` — ordered but
  without a confirmed ETA — after one full `leadTimeDays`.
- **Consumption at the end of each day**, at `max(dailyDemand, forecast/30)`, so
  a forecast above the average makes the projection more conservative, never less.
- Two outputs: `projectedStockoutDay` (first day stock ≤ 0) and
  `projectedBelowSafetyDay` (first day stock < safety stock). `null` means "not
  within the horizon".

### Reorder point

```
inventoryPosition = usableStock + openPurchaseQty + inboundQty
reorderPointQty   = dailyDemand × leadTimeDays + safetyStock
reorderPointDays  = leadTimeDays + safetyDays
belowReorderPoint = inventoryPosition < reorderPointQty
```

---

## 3. Risk engine

`src/domain/risk.ts` — rules add points; the total maps to a level. Every
triggered rule contributes a sentence containing the actual numbers.

| Rule | Points | Fires when |
| --- | --- | --- |
| `stockoutBelowSafety` | 60 | Already below safety stock **and** projected stockout inside lead time |
| `stockoutInsideLead` | 45 | Projected stockout inside lead time |
| `belowSafety` | 30 | Below safety stock now, or projected to dip below it inside lead time |
| `belowReorder` | 18 | Inventory position below the reorder point |
| `noDemandData` | 25 | Consumption unknown — coverage not computable |
| `priceTrend` | 10 | Latest price > 3-month average by more than 5% |
| `aboveTarget` | 8 | Latest price > target by more than 3% |
| `forecastSurge` | 8 | Forecast > average consumption by more than 15% |
| `singleSupplier` | 12 | One approved supplier (or none) |
| `weakSupplier` | 8 | Preferred supplier score below 60 |
| `marketIntel` | 10 HIGH / 15 CRITICAL | Active matching intelligence entry (capped at 25 total) |

**The first five rules are one family — only the worst applies.** They all
describe the same shortage; stacking them would double-count it.

| Level | Score |
| --- | --- |
| LOW | < 20 |
| MEDIUM | ≥ 20 |
| HIGH | ≥ 40 |
| CRITICAL | ≥ 65 |

Market-intelligence matching (`src/domain/intel-match.ts`): an entry's
`affectedMaterials` is a comma-separated list of material codes and/or category
names, or `ALL`. Matching is case-insensitive on exact tokens — no fuzzy logic.

---

## 4. Recommendation engine

`src/domain/recommendation.ts` — a first-match decision tree. **Order matters:
urgency is evaluated before price.**

| Step | Condition | Result |
| --- | --- | --- |
| 0 | No consumption data | `MONITOR` — asks for stock/consumption input |
| 1 | Stockout ≤ lead time × 0.5, **or** stockout < lead time while below safety | `URGENT_PURCHASE` |
| 2 | Stockout ≤ lead time | `BUY_NOW` with a valid quote, else `REQUEST_QUOTATION` |
| 3 | Below safety stock now | `BUY_NOW` with a valid quote, else `REQUEST_QUOTATION` |
| 4 | Inventory position below reorder point | no valid/fresh quote → `REQUEST_QUOTATION`; price > target + 3% → `NEGOTIATE`; otherwise → `BUY_NOW` |
| 5a | Comfortable, single supplier, active HIGH/CRITICAL alert, position < reorder × 1.5 | `SECURE_SUPPLY` |
| 5b | Comfortable, price > target + 3% | `WAIT` |
| 5c | Otherwise | `MONITOR` |

Notes on deliberate choices:

- In steps 2 and 3 a shortage outranks an unfavourable price: the engine says buy
  the required volume now and negotiate the next lot.
- A quotation is **valid** when its status is `ACTIVE` and `validUntil` has not
  passed; **stale** when older than 45 days. Step 4 demands fresh offers.
- `SECURE_SUPPLY` exists for the single-sourced-under-market-stress case, where
  covering forward requirements early beats waiting for a better price.

### Confidence (not an AI score)

Starts at **100**, subtracts documented penalties, clamps to **25–95** — the
engine never claims certainty. Every deduction is returned and rendered under
"How confidence was calculated".

| Deduction | Points |
| --- | --- |
| No consumption data | 30 |
| No quotation on file | 20 |
| Lead time not set | 15 |
| Latest quotation expired | 10 |
| Latest quotation stale (> 45 days) | 10 |
| No target price set | 10 |
| Safety stock not set | 5 |
| Decisive comparison within 10% of its threshold (max 2) | 10 each |

The last row is what makes the number honest: a decision resting on a value that
sits right at its threshold is reported as less confident than a clear-cut one.

---

## 5. Supplier score

`src/domain/supplier-score.ts`

Six 0–10 ratings — quality, pricing, delivery reliability, responsiveness,
technical support, commercial relationship — combine into 0–100:

```
score = 100 × Σ(weight_i × rating_i / 10) / Σ(weight_i)
```

summed **only over rated dimensions**, so a missing rating carries no weight
instead of dragging the score down. Default weights: quality 25, pricing 20,
delivery 20, responsiveness 15, technical 10, commercial 10 (must total 100).
The supplier detail page shows rating, weight and points per dimension.

---

## 6. Best Overall Offer

`src/domain/offer-comparison.ts` — the comparison set is the **latest quotation
per supplier** for one material. Each dimension scores 0–100 (best offer = 100):

```
price         = 100 × lowestPrice / offerPrice        (lower is better)
paymentTerms  = 100 × offerDays / longestDays         (longer is better)
leadTime      = 100 × shortestLead / offerLead        (shorter is better)
supplierScore = the supplier's 0–100 score
overall       = Σ(weight_i × score_i) / Σ(active weights)
```

Default weights: price 50, payment terms 15, lead time 15, supplier score 20.

- Missing data scores 0 and is flagged `missing` — an incomplete offer is
  visibly penalized, never silently favoured.
- Mixed currencies remove the price dimension and its weight from the
  denominator, with a warning.
- Badges — LOWEST PRICE, BEST PAYMENT TERMS, SHORTEST LEAD TIME, BEST SUPPLIER
  SCORE, BEST OVERALL OFFER — are shared on ties.
- **Expired offers stay listed and scored but cannot win any badge.** An offer
  that can no longer be accepted must never be shown as the best choice. (If
  every offer is expired, badges fall back to that set and the warning stands.)

The comparison screen prints the whole table of value → score → weight → points
per offer. The scoring logic is never hidden.

---

## 7. Price statistics

`src/domain/price-stats.ts` — over normalized prices in one currency:

- `latest`, `previous` (by quotation date, newest first)
- `changePct = (latest − previous) / previous × 100`
- 3/6/12-month averages over rolling windows from today
- 12-month min and max
- `diffToTargetPct = (latest − target) / target × 100`

Percentages are rounded to 2 decimals, averages to 4. Worked example (the PMDI
sample): latest `$1.79/kg`, previous `$1.84/kg`, change `−2.72%`, target
`$1.72/kg`, difference to target `+4.07%`.

---

## 8. Dashboard KPIs

`src/server/dashboard.ts`

| KPI | Definition |
| --- | --- |
| Materials requiring action | Recommendation in URGENT_PURCHASE, BUY_NOW, SECURE_SUPPLY, REQUEST_QUOTATION, NEGOTIATE |
| High-risk materials | Risk level HIGH or CRITICAL |
| RFQs pending | Open actions of type RFQ (not completed/cancelled) |
| Purchase value under review | Σ quantity × normalized price over valid quotations, grouped by currency (never summed across currencies) |
| Supplier responses pending | Actions in status WAITING_SUPPLIER |
| Average stock coverage | Mean basic coverage across active materials that have demand data |
| Price increase alerts | Materials whose latest quotation is above the previous one |
| Overdue purchasing actions | Past due date and not completed/cancelled |

---

## 9. Data integrity rules

- **Nothing with history is hard-deleted.** Deleting a material that has
  quotations or actions deactivates it instead and says so. Deleting a quotation
  referenced by an action is refused, with a suggestion to mark it REJECTED or
  SUPERSEDED.
- **Editing a sample record clears `isSample`** — once a human has touched it, it
  is real data.
- Material and supplier codes are unique; duplicates are rejected with a field
  error rather than a database exception.

# Product requirements

## Purpose

A procurement command center for **Siba Kimya**, used daily by the **Purchasing
Manager**. It exists to answer six questions quickly:

1. What should we buy?
2. When should we buy it?
3. From which supplier?
4. At what target price?
5. Which purchasing items require immediate attention?
6. Which raw materials carry supply, price or geopolitical risk?

**Core principle:** within roughly 30 seconds of opening the app the user should
know what needs attention, what to purchase, what can wait, where pricing is
unfavourable, where stock risk exists, and which supplier action is required.

**Non-goal:** this is not a generic ERP. No general ledger, invoicing, production
planning or warehouse management. A feature that does not support a purchasing
decision does not belong in the product.

## Platform

Responsive web application, installable as a PWA. Targets Windows desktop,
laptops, Android phones and tablets. Desktop is the primary device; mobile must
be fully usable for dashboard alerts, actions, supplier and quotation review,
search and approvals. **No native Android app.**

## Scope status

### Phase 1 — delivered

| Requirement | Status |
| --- | --- |
| Dashboard with 8 KPI cards | Done |
| Priority Purchasing Actions section | Done |
| Material master + full CRUD | Done |
| Supplier master + CRUD, 0–100 score with configurable weights | Done |
| Quotation management, multiple offers per material | Done |
| Quotation comparison with LOWEST PRICE / BEST PAYMENT / SHORTEST LEAD / BEST SUPPLIER / BEST OVERALL, explainable | Done |
| Price history: chart, filters, deterministic metrics | Done |
| Stock coverage (basic + projected), reorder point | Done |
| Deterministic risk engine with reasons | Done |
| Deterministic recommendation engine with explained confidence | Done |
| Purchasing plan ranked by urgency | Done |
| Action Center with overdue highlighting | Done |
| Market Intelligence manual entry feeding risk | Done |
| Settings for weights and thresholds | Done |
| Global search across all five entities | Done |
| SQLite persistence, sample data, responsive UI, PWA manifest | Done |

### Deliberately not in Phase 1

Outlook integration · Mikro ERP integration · external market/FX/freight APIs ·
AI API integration · automated web scraping · native Android · user management
and authentication · PDF quotation extraction · CSV/XLSX import (format
documented, UI placeholder in place) · FX conversion · offline service worker.

## Functional requirements

### Dashboard

KPI cards: Materials Requiring Action · High-Risk Materials · RFQs Pending ·
Purchase Value Under Review · Supplier Responses Pending · Average Stock Coverage
· Price Increase Alerts · Overdue Purchasing Actions. Definitions in
`procurement-rules.md` §8; each card links to the screen that explains it.

**Priority Purchasing Actions** lists materials the engine says to act on, ranked
by recommendation urgency, then risk, then coverage, showing material, coverage
(basic and projected), latest price vs target, risk level, recommended action with
confidence, and the deciding reason.

Risk states are **LOW / MEDIUM / HIGH / CRITICAL**. Colour is a secondary
indicator only — the text level is always rendered.

### Material master

Fields: code, name, category, unit, specification, preferred supplier, approved
suppliers, average monthly and daily consumption, current/reserved/usable/safety/
minimum stock, lead time, open purchase quantity, inbound quantity and ETA,
forecast requirement, last purchase price and date, latest quotation, target
price, currency, Incoterm, notes, active flag. Usable stock and all analytics are
derived, never stored.

Categories: PMDI · Polyether Polyols · Silicone · OH Polymer · Chlorinated
Paraffin · DME · Methanol · Hydrocarbons · Additives · Packaging · Other.

### Supplier master

Company name, code, country, city, website, contact person/email/phone, materials
supplied, approved materials, preferred currency, normal Incoterm, payment terms
(text plus numeric days), normal lead time, six 0–10 performance ratings, notes,
active flag, plus a derived 0–100 **Supplier Score** whose weights are editable
in Settings and never hard-coded.

### Quotations

Quotation number, supplier, material, date, quoted quantity, container count,
quantity per container, price, currency, price unit, **normalized price**,
Incoterm, destination port, payment terms, lead time, production lead time,
validity date, freight-included flag, remarks, attachment reference.

Multiple quotations per material are expected. The comparison screen shows
supplier, price, normalized price, payment, Incoterm, lead time, quantity and
date, awards the five badges, and prints the full scoring arithmetic. Expired
offers remain visible but cannot win a badge.

### Price history

Filters: material, supplier, category, date range. Metrics: latest, previous,
percentage change, 3/6/12-month averages, 12-month min and max, target price and
difference to target. All deterministic; every charted point also appears in the
table below the chart.

### Purchasing plan

Inputs: usable stock, average daily consumption, forecast, safety stock, supplier
lead time, open purchase orders, inbound quantity. Outputs: stock coverage days,
projected coverage, reorder point (quantity and days), inventory position, risk
and recommendation. Formulas are configurable, not hard-coded.

### Risk and recommendation engines

Deterministic rules only. **Every classification must carry its reasons with real
numbers** — an unexplained score is a defect. Recommendations: BUY NOW · REQUEST
QUOTATION · NEGOTIATE · WAIT · MONITOR · SECURE SUPPLY · URGENT PURCHASE.
Confidence is an explicitly documented data-quality measure, never an invented
AI-style number.

### Market intelligence

Manual entry only in the MVP — no scraping. Fields: date, title, category,
affected materials, geography, summary, expected impact, price direction (UP /
DOWN / NEUTRAL / UNCERTAIN), supply impact, risk level, source, URL, notes.
Tracks raw materials (PMDI, polyol, silicone DMC, OH polymer, chlorinated
paraffin, methanol, DME, isobutane, hexane), macro variables (Brent, USD/CNY,
EUR/USD, USD/TRY, China market, freight) and geopolitical subjects (Hormuz, Middle
East, Gulf shipping, Red Sea, sanctions, Chinese disruptions, plant shutdowns,
logistics). Active HIGH/CRITICAL entries raise the risk of matching materials.

### Action center

Title, material, supplier, action type, owner, created and due dates, priority,
status, notes, related quotation. Types: RFQ · NEGOTIATION · PURCHASE · SUPPLIER
FOLLOW-UP · SAMPLE · QUALITY · LOGISTICS · DOCUMENTATION · MARKET CHECK · OTHER.
Statuses: OPEN · IN PROGRESS · WAITING SUPPLIER · WAITING INTERNAL · COMPLETED ·
CANCELLED. Overdue items are highlighted.

### Search and filtering

Global search across materials, suppliers, quotations, actions and market
intelligence (e.g. `Hoshine`, `HS3130`, `PMDI`, `CP52`, `Wanhua`). Large tables
provide sorting, filtering, pagination and mobile-appropriate column visibility.

## Non-functional requirements

- **Explainability:** no unexplained number anywhere in the product.
- **Determinism:** identical inputs always yield identical outputs; the core
  never depends on an LLM.
- **Sample-data honesty:** development fixtures are always labelled SAMPLE and
  never presented as real purchasing data.
- **Data safety:** records with history are deactivated, never hard-deleted;
  invalid input is reported per field, never silently dropped.
- **Confidentiality:** procurement data is confidential; secrets live only in
  environment variables and never reach the browser.
- **UX:** modern, professional, clean, information-dense, executive-friendly.
  Excellent tables, clear typography, practical filters, decision-focused
  hierarchy. No excessive animation, giant empty cards, gamification or
  decorative dashboards. Charts only where they add decision value.

## Phase 1 acceptance criteria

All twenty are verified by `npm run test:e2e` (55 checks) plus `npm test`:

start the app · open the dashboard · see sample KPIs · view the material list ·
create a material · edit a material · deactivate/delete a material safely · view
suppliers · create and edit suppliers · create quotations · compare quotations
for one material · view quotation history · see a basic price trend · enter stock
and consumption · see stock coverage · receive a deterministic recommendation ·
see why it was generated · use the app on desktop · use key screens on mobile ·
restart the app without losing data.

## Phase 2 candidates

1. CSV/XLSX import (`import-format.md` is already written).
2. FX rates so cross-currency comparison and consolidated value work.
3. Purchase orders as first-class records, replacing the scalar
   `openPurchaseQty` / `inboundQty` / `inboundEta` fields.
4. Authentication and roles.
5. Multiple contacts per supplier; quotation document attachments.
6. AI Procurement Analyst over the existing insight structures.
7. Outlook quotation extraction, Mikro ERP sync, market data feeds.

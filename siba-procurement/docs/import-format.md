# Import formats

> **Status: specification only.** The import module is **Phase 2**; `/imports`
> currently shows the planned flow. This document fixes the file contracts now so
> that data collection can start immediately and the implementation has nothing
> left to invent.

## Committed process

Imports must never corrupt data silently, so the flow is fixed:

1. **Upload** — CSV first, then XLSX (first worksheet).
2. **Preview** the first ~20 rows exactly as parsed.
3. **Auto-detect columns** by header name (case- and space-insensitive).
4. **Manual column mapping** for anything detection got wrong or that is missing.
5. **Validate every row** against the same zod schemas the forms use
   (`src/lib/validation.ts`) — one code path, so an import can never accept
   something a form would reject.
6. **Display all errors** as row number + column + message.
7. **Import summary**: rows valid, rows invalid, rows that would be created vs
   updated.
8. **Explicit confirmation** before anything is written.
9. **Import valid rows only** in a transaction.
10. **Store an import log** — file name, timestamp, counts, and every rejected
    row with its reason.

**Invalid rows are never silently ignored.** They are reported, excluded, and
recoverable from the log.

## General rules

- Encoding UTF-8; comma-separated; first row is the header.
- Decimal separator is `.`; no thousands separators. `1234.56`, not `1.234,56`.
- Dates are `YYYY-MM-DD`.
- Empty cell = "not provided" (field left null / default), which is different from
  `0`.
- Booleans accept `true/false`, `yes/no`, `1/0`.
- Enum-like columns must use the documented values exactly (they are validated
  against `src/domain/enums.ts`).
- Matching key: `material_code` for materials, `supplier_code` for suppliers. An
  existing key updates that record; a new key creates one.
- All quantities for a material must be expressed in that material's `unit`.

## 1. Material master

`material_code` and `name` are required; everything else is optional.

| Column | Type | Notes |
| --- | --- | --- |
| `material_code` | text | **Required**, unique matching key |
| `name` | text | **Required** |
| `category` | enum | `PMDI`, `POLYETHER_POLYOLS`, `SILICONE`, `OH_POLYMER`, `CHLORINATED_PARAFFIN`, `DME`, `METHANOL`, `HYDROCARBONS`, `ADDITIVES`, `PACKAGING`, `OTHER` |
| `unit` | enum | `kg`, `MT`, `L`, `pcs` (default `kg`) |
| `specification` | text | |
| `preferred_supplier_code` | text | Must exist — import suppliers first |
| `approved_supplier_codes` | text | Semicolon-separated, e.g. `WANHUA;COVESTRO` |
| `avg_monthly_consumption` | number | In `unit` |
| `avg_daily_consumption` | number | Omit to derive monthly ÷ 30 |
| `forecast_monthly_demand` | number | |
| `current_stock` | number | |
| `reserved_stock` | number | |
| `safety_stock` | number | |
| `minimum_stock` | number | |
| `lead_time_days` | integer | |
| `open_purchase_qty` | number | Ordered, no confirmed ETA |
| `inbound_qty` | number | Shipped / ETA confirmed |
| `inbound_eta` | date | |
| `last_purchase_price` | number | Per `unit` |
| `last_purchase_date` | date | |
| `target_price` | number | Per `unit` |
| `currency` | enum | `USD`, `EUR`, `TRY`, `CNY`, `GBP` |
| `incoterm` | enum | `EXW`…`DDP` |
| `notes` | text | |
| `is_active` | boolean | Default `true` |

```csv
material_code,name,category,unit,avg_monthly_consumption,current_stock,reserved_stock,safety_stock,lead_time_days,target_price,currency,preferred_supplier_code,approved_supplier_codes
RM-PMDI-001,PMDI,PMDI,kg,300000,340000,20000,150000,45,1.72,USD,WANHUA,WANHUA;COVESTRO
RM-CP-52,CP52,CHLORINATED_PARAFFIN,kg,60000,122000,2000,30000,35,0.80,USD,XUYE,XUYE
```

## 2. Supplier master

| Column | Type | Notes |
| --- | --- | --- |
| `supplier_code` | text | **Required**, unique matching key |
| `name` | text | **Required** |
| `country` | text | **Required** |
| `city`, `website`, `contact_name`, `contact_email`, `contact_phone` | text | `contact_email` must be a valid address |
| `preferred_currency` | enum | Default `USD` |
| `default_incoterm` | enum | |
| `payment_terms` | text | Human-readable, e.g. `LC 60 days` |
| `payment_term_days` | integer | Numeric equivalent — needed for comparison |
| `normal_lead_time_days` | integer | |
| `quality_rating`, `pricing_rating`, `delivery_rating`, `responsiveness_rating`, `technical_rating`, `commercial_rating` | number | 0–10; blank = unrated (excluded from the score) |
| `notes` | text | |
| `is_active` | boolean | Default `true` |

```csv
supplier_code,name,country,city,payment_terms,payment_term_days,normal_lead_time_days,quality_rating,pricing_rating,delivery_rating
WANHUA,Wanhua Chemical Group,China,Yantai,LC 60 days,60,45,9,8.5,8
```

## 3. Quotation history

| Column | Type | Notes |
| --- | --- | --- |
| `quotation_number` | text | **Required**; not globally unique |
| `supplier_code` | text | **Required**, must exist |
| `material_code` | text | **Required**, must exist |
| `quotation_date` | date | **Required** |
| `price` | number | **Required**, > 0 |
| `currency` | enum | **Required** |
| `price_unit` | enum | **Required**; must convert to the material's unit |
| `quantity` | number | **Required**, > 0 |
| `quantity_unit` | enum | **Required**; must convert to the material's unit |
| `container_count`, `qty_per_container` | number | |
| `incoterm`, `destination_port`, `payment_terms` | text/enum | |
| `payment_term_days`, `lead_time_days`, `production_lead_time_days` | integer | |
| `valid_until` | date | Blank = no expiry |
| `freight_included` | boolean | |
| `status` | enum | `ACTIVE`, `ACCEPTED`, `REJECTED`, `SUPERSEDED` (default `ACTIVE`) |
| `remarks`, `attachment_ref` | text | |

`normalized_price` is **not** imported — it is always computed from `price` and
`price_unit`, so a file can never contain an inconsistent normalized value.

```csv
quotation_number,supplier_code,material_code,quotation_date,price,currency,price_unit,quantity,quantity_unit,payment_term_days,lead_time_days,valid_until
XUYE-Q174,XUYE,RM-CP-52,2026-06-28,865,USD,MT,66,MT,30,35,2026-07-28
WANHUA-Q111,WANHUA,RM-PMDI-001,2026-07-07,1.79,USD,kg,88000,kg,60,45,2026-08-06
```

## 4. Purchasing history

Historical purchase orders. Until purchase orders become first-class records
(Phase 2), an import updates the material's `last_purchase_price` /
`last_purchase_date` from the most recent row per material.

| Column | Type | Notes |
| --- | --- | --- |
| `po_number` | text | **Required** |
| `material_code`, `supplier_code` | text | **Required** |
| `order_date` | date | **Required** |
| `quantity`, `quantity_unit` | number/enum | **Required** |
| `price`, `currency`, `price_unit` | number/enum | **Required** |
| `incoterm`, `payment_terms` | text | |
| `expected_delivery_date`, `actual_delivery_date` | date | |
| `status` | enum | `OPEN`, `PARTIAL`, `RECEIVED`, `CANCELLED` |

## 5. Stock data

The highest-frequency import (typically a daily or weekly ERP extract). Updates
only the stock columns of existing materials; unknown codes are reported, never
created — a stock file must not invent master data.

| Column | Type | Notes |
| --- | --- | --- |
| `material_code` | text | **Required**, must exist |
| `current_stock` | number | **Required** |
| `reserved_stock` | number | |
| `open_purchase_qty`, `inbound_qty` | number | |
| `inbound_eta` | date | |
| `as_of_date` | date | Recorded in the import log |

```csv
material_code,current_stock,reserved_stock,inbound_qty,inbound_eta,as_of_date
RM-PMDI-001,340000,20000,0,,2026-08-17
RM-DME-001,32000,2000,60000,2026-08-22,2026-08-17
```

## Validation errors reported

- Missing required column or required value.
- Unparseable number or date; negative quantity; non-positive price.
- Unknown enum value (the message lists the allowed set).
- `material_code` / `supplier_code` reference that does not exist.
- Price or quantity unit that cannot convert to the material's base unit (e.g.
  `L` priced against a `kg` material).
- Duplicate matching key within the same file.

## PDF quotation extraction

Explicitly **not** in scope for the MVP: supplier PDF layouts vary too much for
reliable parsing, and a silently mis-parsed price is worse than no import. The
architecture keeps the door open — parsing happens at the edge and produces the
same validated row shape as CSV/XLSX, so only a new adapter is needed. Any future
implementation must present extracted values for human confirmation before they
are written.

/**
 * Seed script — SAMPLE DATA ONLY.
 *
 * Every row created here carries `isSample: true` and the UI labels such rows
 * as SAMPLE. Prices, stocks, consumptions, ratings and market notes are
 * realistic-looking development fixtures, NOT real purchasing data of
 * Siba Kimya or any supplier.
 *
 * Run with: npx prisma db seed   (configured in prisma.config.ts)
 * The script is idempotent: it wipes all tables and re-creates the fixtures.
 */
import { PrismaClient } from "../src/generated/prisma/client";
import { PrismaBetterSqlite3 } from "@prisma/adapter-better-sqlite3";
import { convertPrice, roundPrice } from "../src/domain/units";
import type { QuantityUnit } from "../src/domain/enums";
import {
  DEFAULT_SUPPLIER_SCORE_WEIGHTS,
  DEFAULT_OFFER_SCORE_WEIGHTS,
  DEFAULT_RECOMMENDATION_CONFIG,
  SETTING_KEYS,
} from "../src/domain/config";

const adapter = new PrismaBetterSqlite3({
  url: process.env.DATABASE_URL ?? "file:./prisma/dev.db",
});
const prisma = new PrismaClient({ adapter });

// ---------------------------------------------------------------- date utils
const NOW = new Date();

function daysAgo(n: number): Date {
  return new Date(NOW.getTime() - n * 86_400_000);
}
function daysFromNow(n: number): Date {
  return new Date(NOW.getTime() + n * 86_400_000);
}

async function main() {
  console.log("Seeding SAMPLE data (all rows flagged isSample=true)…");

  // Wipe in FK-safe order — the seed owns the whole dev database.
  await prisma.actionItem.deleteMany();
  await prisma.quotation.deleteMany();
  await prisma.materialSupplier.deleteMany();
  await prisma.marketIntelligence.deleteMany();
  await prisma.material.deleteMany();
  await prisma.supplier.deleteMany();
  await prisma.setting.deleteMany();

  // ------------------------------------------------------------- settings
  await prisma.setting.createMany({
    data: [
      {
        key: SETTING_KEYS.supplierScoreWeights,
        value: JSON.stringify(DEFAULT_SUPPLIER_SCORE_WEIGHTS),
      },
      {
        key: SETTING_KEYS.offerScoreWeights,
        value: JSON.stringify(DEFAULT_OFFER_SCORE_WEIGHTS),
      },
      {
        key: SETTING_KEYS.recommendationConfig,
        value: JSON.stringify(DEFAULT_RECOMMENDATION_CONFIG),
      },
    ],
  });

  // ------------------------------------------------------------- suppliers
  const supplierRows = [
    {
      code: "WANHUA",
      name: "Wanhua Chemical Group",
      country: "China",
      city: "Yantai",
      website: "https://www.whchem.com",
      contactName: "Li Wei",
      contactEmail: "sales.sample@wanhua-example.com",
      contactPhone: "+86 535 000 0000",
      preferredCurrency: "USD",
      defaultIncoterm: "CIF",
      paymentTerms: "LC 60 days",
      paymentTermDays: 60,
      normalLeadTimeDays: 45,
      qualityRating: 9,
      pricingRating: 8.5,
      deliveryRating: 8,
      responsivenessRating: 7,
      technicalRating: 8,
      commercialRating: 8,
      notes: "SAMPLE — primary PMDI source, quarterly frame talks.",
    },
    {
      code: "COVESTRO",
      name: "Covestro AG",
      country: "Germany",
      city: "Leverkusen",
      website: "https://www.covestro.com",
      contactName: "Anna Schmidt",
      contactEmail: "sales.sample@covestro-example.com",
      contactPhone: "+49 214 000 0000",
      preferredCurrency: "EUR",
      defaultIncoterm: "DAP",
      paymentTerms: "Net 30",
      paymentTermDays: 30,
      normalLeadTimeDays: 21,
      qualityRating: 9.5,
      pricingRating: 6.5,
      deliveryRating: 9,
      responsivenessRating: 8,
      technicalRating: 9,
      commercialRating: 7,
      notes: "SAMPLE — backup PMDI source, shorter lead time, higher price.",
    },
    {
      code: "HOSHINE",
      name: "Hoshine Silicon Industry",
      country: "China",
      city: "Jiaxing",
      website: "https://www.hoshine.com",
      contactName: "Chen Jing",
      contactEmail: "export.sample@hoshine-example.com",
      contactPhone: "+86 573 000 0000",
      preferredCurrency: "USD",
      defaultIncoterm: "CIF",
      paymentTerms: "TT 30 days",
      paymentTermDays: 30,
      normalLeadTimeDays: 50,
      qualityRating: 8.5,
      pricingRating: 8.5,
      deliveryRating: 7.5,
      responsivenessRating: 7,
      technicalRating: 7.5,
      commercialRating: 8,
      notes: "SAMPLE — HS3130 silicone polymer + OH polymer range.",
    },
    {
      code: "DOW",
      name: "Dow Europe GmbH",
      country: "Switzerland",
      city: "Horgen",
      website: "https://www.dow.com",
      contactName: "Marc Keller",
      contactEmail: "polyols.sample@dow-example.com",
      contactPhone: "+41 44 000 0000",
      preferredCurrency: "USD",
      defaultIncoterm: "DAP",
      paymentTerms: "Net 45",
      paymentTermDays: 45,
      normalLeadTimeDays: 18,
      qualityRating: 9,
      pricingRating: 6,
      deliveryRating: 8.5,
      responsivenessRating: 7.5,
      technicalRating: 9,
      commercialRating: 7,
      notes: "SAMPLE — premium polyether polyols, EU warehouse.",
    },
    {
      code: "INOV",
      name: "Shandong INOV Polyurethane",
      country: "China",
      city: "Zibo",
      website: "https://www.inovpu.com",
      contactName: "Zhang Min",
      contactEmail: "export.sample@inov-example.com",
      contactPhone: "+86 533 000 0000",
      preferredCurrency: "USD",
      defaultIncoterm: "CIF",
      paymentTerms: "LC 90 days",
      paymentTermDays: 90,
      normalLeadTimeDays: 42,
      qualityRating: 7.5,
      pricingRating: 9,
      deliveryRating: 7.5,
      responsivenessRating: 8,
      technicalRating: 7,
      commercialRating: 8.5,
      notes: "SAMPLE — competitive polyol pricing, generous payment terms.",
    },
    {
      code: "XUYE",
      name: "Xuye New Materials",
      country: "China",
      city: "Weifang",
      website: "https://www.xuye-example.com",
      contactName: "Wang Fang",
      contactEmail: "cp.sample@xuye-example.com",
      contactPhone: "+86 536 000 0000",
      preferredCurrency: "USD",
      defaultIncoterm: "CIF",
      paymentTerms: "TT 30 days",
      paymentTermDays: 30,
      normalLeadTimeDays: 35,
      qualityRating: 7.5,
      pricingRating: 8,
      deliveryRating: 7,
      responsivenessRating: 6.5,
      technicalRating: 6.5,
      commercialRating: 7,
      notes: "SAMPLE — only approved chlorinated paraffin source (CP52/CP45).",
    },
    {
      code: "LUXI",
      name: "Luxi Chemical Group",
      country: "China",
      city: "Liaocheng",
      website: "https://www.luxi-example.com",
      contactName: "Zhao Lei",
      contactEmail: "dme.sample@luxi-example.com",
      contactPhone: "+86 635 000 0000",
      preferredCurrency: "USD",
      defaultIncoterm: "CIF",
      paymentTerms: "TT 30 days",
      paymentTermDays: 30,
      normalLeadTimeDays: 30,
      qualityRating: 8,
      pricingRating: 8.5,
      deliveryRating: 7.5,
      responsivenessRating: 7,
      technicalRating: 7,
      commercialRating: 7.5,
      notes: "SAMPLE — DME and methanol volumes.",
    },
    {
      code: "MARPET",
      name: "Marmara Petrokimya",
      country: "Türkiye",
      city: "Kocaeli",
      website: "https://www.marpet-example.com.tr",
      contactName: "Deniz Aksoy",
      contactEmail: "satis.sample@marpet-example.com.tr",
      contactPhone: "+90 262 000 0000",
      preferredCurrency: "USD",
      defaultIncoterm: "FCA",
      paymentTerms: "Net 30",
      paymentTermDays: 30,
      normalLeadTimeDays: 7,
      qualityRating: 8,
      pricingRating: 7,
      deliveryRating: 9,
      responsivenessRating: 9,
      technicalRating: 6.5,
      commercialRating: 8,
      notes: "SAMPLE — local hydrocarbon propellants (isobutane, hexane).",
    },
    {
      code: "KIMPEX",
      name: "Kimpex Chemical Trading",
      country: "Türkiye",
      city: "Istanbul",
      website: "https://www.kimpex-example.com",
      contactName: "Selin Kaya",
      contactEmail: "trade.sample@kimpex-example.com",
      contactPhone: "+90 212 000 0000",
      preferredCurrency: "USD",
      defaultIncoterm: "DAP",
      paymentTerms: "Net 45",
      paymentTermDays: 45,
      normalLeadTimeDays: 10,
      qualityRating: 7,
      pricingRating: 7,
      deliveryRating: 8.5,
      responsivenessRating: 9.5,
      technicalRating: 6,
      commercialRating: 8,
      notes: "SAMPLE — local trader; useful for spot gaps and small lots.",
    },
  ];

  const suppliers = new Map<string, { id: string }>();
  for (const row of supplierRows) {
    const s = await prisma.supplier.create({
      data: { ...row, isActive: true, isSample: true },
    });
    suppliers.set(row.code, s);
  }
  const sup = (code: string) => {
    const s = suppliers.get(code);
    if (!s) throw new Error(`Unknown supplier ${code}`);
    return s.id;
  };

  // ------------------------------------------------------------- materials
  // All quantities in kg. Consumption values are monthly averages.
  type MaterialSeed = {
    code: string;
    name: string;
    category: string;
    specification?: string;
    preferred: string;
    approved: string[];
    avgMonthly: number;
    currentStock: number;
    reservedStock?: number;
    safetyStock: number;
    minimumStock?: number;
    leadTimeDays: number;
    openPurchaseQty?: number;
    inboundQty?: number;
    inboundEtaDays?: number;
    forecastMonthly?: number;
    lastPurchasePrice?: number;
    lastPurchaseDaysAgo?: number;
    targetPrice?: number;
    incoterm?: string;
    notes?: string;
  };

  const materialRows: MaterialSeed[] = [
    {
      code: "RM-PMDI-001",
      name: "PMDI",
      category: "PMDI",
      specification: "Polymeric MDI, NCO 30.5–32.0%, viscosity 150–250 mPa·s",
      preferred: "WANHUA",
      approved: ["WANHUA", "COVESTRO", "KIMPEX"],
      avgMonthly: 300_000,
      currentStock: 340_000,
      reservedStock: 20_000,
      safetyStock: 150_000, // ≈ 15 days
      minimumStock: 100_000,
      leadTimeDays: 45,
      lastPurchasePrice: 1.84,
      lastPurchaseDaysAgo: 55,
      targetPrice: 1.72,
      incoterm: "CIF",
      notes: "SAMPLE — core isocyanate for PU foam lines.",
    },
    {
      code: "RM-POL-056",
      name: "Polyol 56",
      category: "POLYETHER_POLYOLS",
      specification: "Polyether polyol, OH value 56 mgKOH/g",
      preferred: "INOV",
      approved: ["INOV", "DOW"],
      avgMonthly: 120_000,
      currentStock: 190_000,
      reservedStock: 10_000,
      safetyStock: 60_000,
      leadTimeDays: 42,
      openPurchaseQty: 80_000,
      lastPurchasePrice: 1.22,
      lastPurchaseDaysAgo: 40,
      targetPrice: 1.18,
      incoterm: "CIF",
    },
    {
      code: "RM-POL-120",
      name: "Polyol 120",
      category: "POLYETHER_POLYOLS",
      specification: "Polyether polyol, OH value 120 mgKOH/g",
      preferred: "INOV",
      approved: ["INOV", "DOW"],
      avgMonthly: 45_000,
      currentStock: 92_000,
      reservedStock: 2_000,
      safetyStock: 22_500,
      leadTimeDays: 42,
      lastPurchasePrice: 1.31,
      lastPurchaseDaysAgo: 70,
      targetPrice: 1.26,
      incoterm: "CIF",
    },
    {
      code: "RM-POL-160",
      name: "Polyol 160",
      category: "POLYETHER_POLYOLS",
      specification: "Polyether polyol, OH value 160 mgKOH/g",
      preferred: "INOV",
      approved: ["INOV"],
      avgMonthly: 30_000,
      currentStock: 75_000,
      safetyStock: 15_000,
      leadTimeDays: 42,
      lastPurchasePrice: 1.38,
      lastPurchaseDaysAgo: 85,
      targetPrice: 1.32,
      incoterm: "CIF",
    },
    {
      code: "RM-POL-2502",
      name: "Polyol 250-2",
      category: "POLYETHER_POLYOLS",
      specification: "Polyether polyol, OH value 250, difunctional",
      preferred: "INOV",
      approved: ["INOV", "DOW"],
      avgMonthly: 25_000,
      currentStock: 21_000,
      reservedStock: 1_000,
      safetyStock: 12_500,
      leadTimeDays: 42,
      lastPurchasePrice: 1.45,
      lastPurchaseDaysAgo: 95,
      targetPrice: 1.42,
      incoterm: "CIF",
      notes: "SAMPLE — stock ran down after Q2 campaign.",
    },
    {
      code: "RM-POL-2503",
      name: "Polyol 250-3",
      category: "POLYETHER_POLYOLS",
      specification: "Polyether polyol, OH value 250, trifunctional",
      preferred: "INOV",
      approved: ["INOV"],
      avgMonthly: 20_000,
      currentStock: 52_000,
      safetyStock: 10_000,
      leadTimeDays: 42,
      lastPurchasePrice: 1.48,
      lastPurchaseDaysAgo: 60,
      targetPrice: 1.44,
      incoterm: "CIF",
    },
    {
      code: "RM-POL-400",
      name: "Polyol 400",
      category: "POLYETHER_POLYOLS",
      specification: "Polyether polyol, OH value 400 mgKOH/g",
      preferred: "DOW",
      approved: ["DOW", "INOV"],
      avgMonthly: 15_000,
      currentStock: 13_000,
      reservedStock: 1_000,
      safetyStock: 7_500,
      leadTimeDays: 18,
      inboundQty: 20_000,
      inboundEtaDays: 10,
      lastPurchasePrice: 1.62,
      lastPurchaseDaysAgo: 35,
      targetPrice: 1.58,
      incoterm: "DAP",
      notes: "SAMPLE — 20 t inbound from Dow EU warehouse.",
    },
    {
      code: "RM-SIL-3130T",
      name: "HS3130 Transparent",
      category: "SILICONE",
      specification: "Silicone polymer HS3130, transparent grade",
      preferred: "HOSHINE",
      approved: ["HOSHINE"],
      avgMonthly: 57_000,
      currentStock: 132_000,
      reservedStock: 2_000,
      safetyStock: 28_500,
      leadTimeDays: 50,
      forecastMonthly: 57_000,
      lastPurchasePrice: 2.44,
      lastPurchaseDaysAgo: 30,
      targetPrice: 2.3,
      incoterm: "CIF",
      notes: "SAMPLE — next requirement ≈114 MT / 5 FCL; single-sourced.",
    },
    {
      code: "RM-SIL-3130W",
      name: "HS3130 White",
      category: "SILICONE",
      specification: "Silicone polymer HS3130, white grade",
      preferred: "HOSHINE",
      approved: ["HOSHINE"],
      avgMonthly: 28_000,
      currentStock: 56_000,
      reservedStock: 1_000,
      safetyStock: 14_000,
      leadTimeDays: 50,
      lastPurchasePrice: 2.48,
      lastPurchaseDaysAgo: 45,
      targetPrice: 2.35,
      incoterm: "CIF",
    },
    {
      code: "RM-SIL-3130B",
      name: "HS3130 Black",
      category: "SILICONE",
      specification: "Silicone polymer HS3130, black grade",
      preferred: "HOSHINE",
      approved: ["HOSHINE"],
      avgMonthly: 9_000,
      currentStock: 30_000,
      safetyStock: 4_500,
      leadTimeDays: 50,
      lastPurchasePrice: 2.5,
      lastPurchaseDaysAgo: 75,
      targetPrice: 2.4,
      incoterm: "CIF",
    },
    {
      code: "RM-OHP-20K",
      name: "OH Polymer 20K",
      category: "OH_POLYMER",
      specification: "OH-terminated PDMS, viscosity 20,000 cSt",
      preferred: "HOSHINE",
      approved: ["HOSHINE"],
      avgMonthly: 22_000,
      currentStock: 41_000,
      reservedStock: 1_000,
      safetyStock: 11_000,
      leadTimeDays: 50,
      lastPurchasePrice: 2.46,
      lastPurchaseDaysAgo: 50,
      targetPrice: 2.45,
      incoterm: "CIF",
    },
    {
      code: "RM-OHP-50K",
      name: "OH Polymer 50K",
      category: "OH_POLYMER",
      specification: "OH-terminated PDMS, viscosity 50,000 cSt",
      preferred: "HOSHINE",
      approved: ["HOSHINE"],
      avgMonthly: 18_000,
      currentStock: 50_000,
      safetyStock: 9_000,
      leadTimeDays: 50,
      lastPurchasePrice: 2.52,
      lastPurchaseDaysAgo: 65,
      targetPrice: 2.5,
      incoterm: "CIF",
    },
    {
      code: "RM-OHP-80K",
      name: "OH Polymer 80K",
      category: "OH_POLYMER",
      specification: "OH-terminated PDMS, viscosity 80,000 cSt",
      preferred: "HOSHINE",
      approved: ["HOSHINE"],
      avgMonthly: 8_000,
      currentStock: 3_500,
      reservedStock: 500,
      safetyStock: 4_000,
      leadTimeDays: 50,
      lastPurchasePrice: 2.66,
      lastPurchaseDaysAgo: 80,
      targetPrice: 2.6,
      incoterm: "CIF",
      notes: "SAMPLE — stock below safety, no shipment booked.",
    },
    {
      code: "RM-CP-52",
      name: "CP52",
      category: "CHLORINATED_PARAFFIN",
      specification: "Chlorinated paraffin 52%",
      preferred: "XUYE",
      approved: ["XUYE"],
      avgMonthly: 60_000,
      currentStock: 122_000,
      reservedStock: 2_000,
      safetyStock: 30_000,
      leadTimeDays: 35,
      lastPurchasePrice: 0.84,
      lastPurchaseDaysAgo: 50,
      targetPrice: 0.8,
      incoterm: "CIF",
      notes: "SAMPLE — single-sourced; price trending up.",
    },
    {
      code: "RM-CP-45",
      name: "CP45",
      category: "CHLORINATED_PARAFFIN",
      specification: "Chlorinated paraffin 45%",
      preferred: "XUYE",
      approved: ["XUYE"],
      avgMonthly: 15_000,
      currentStock: 40_000,
      safetyStock: 7_500,
      leadTimeDays: 35,
      lastPurchasePrice: 0.78,
      lastPurchaseDaysAgo: 90,
      targetPrice: 0.75,
      incoterm: "CIF",
    },
    {
      code: "RM-DME-001",
      name: "DME",
      category: "DME",
      specification: "Dimethyl ether, propellant grade ≥99.9%",
      preferred: "LUXI",
      approved: ["LUXI"],
      avgMonthly: 90_000,
      currentStock: 32_000,
      reservedStock: 2_000,
      safetyStock: 45_000,
      leadTimeDays: 30,
      inboundQty: 60_000,
      inboundEtaDays: 5,
      lastPurchasePrice: 0.99,
      lastPurchaseDaysAgo: 25,
      targetPrice: 0.92,
      incoterm: "CIF",
      notes: "SAMPLE — 60 t on the water, ETA ~5 days; stock below safety.",
    },
    {
      code: "RM-MEOH-001",
      name: "Methanol",
      category: "METHANOL",
      specification: "Methanol AA grade ≥99.85%",
      preferred: "LUXI",
      approved: ["LUXI", "KIMPEX"],
      avgMonthly: 45_000,
      currentStock: 102_000,
      reservedStock: 2_000,
      safetyStock: 22_500,
      leadTimeDays: 25,
      lastPurchasePrice: 0.36,
      lastPurchaseDaysAgo: 30,
      targetPrice: 0.35,
      incoterm: "CIF",
    },
    {
      code: "RM-HC-IBU",
      name: "Isobutane",
      category: "HYDROCARBONS",
      specification: "Isobutane propellant grade ≥99.5%",
      preferred: "MARPET",
      approved: ["MARPET"],
      avgMonthly: 36_000,
      currentStock: 43_000,
      reservedStock: 1_000,
      safetyStock: 18_000,
      leadTimeDays: 7,
      lastPurchasePrice: 0.74,
      lastPurchaseDaysAgo: 15,
      targetPrice: 0.72,
      incoterm: "FCA",
    },
    {
      code: "RM-HC-HEX",
      name: "Hexane",
      category: "HYDROCARBONS",
      specification: "n-Hexane ≥95%",
      preferred: "MARPET",
      approved: ["MARPET", "KIMPEX"],
      avgMonthly: 12_000,
      currentStock: 13_000,
      safetyStock: 6_000,
      leadTimeDays: 7,
      lastPurchasePrice: 0.86,
      lastPurchaseDaysAgo: 40,
      targetPrice: 0.83,
      incoterm: "FCA",
    },
  ];

  const materials = new Map<string, { id: string; unit: QuantityUnit }>();
  for (const m of materialRows) {
    const created = await prisma.material.create({
      data: {
        code: m.code,
        name: m.name,
        category: m.category,
        unit: "kg",
        specification: m.specification,
        preferredSupplierId: sup(m.preferred),
        avgMonthlyConsumption: m.avgMonthly,
        avgDailyConsumption: roundPrice(m.avgMonthly / 30, 2),
        forecastMonthlyDemand: m.forecastMonthly,
        currentStock: m.currentStock,
        reservedStock: m.reservedStock ?? 0,
        safetyStock: m.safetyStock,
        minimumStock: m.minimumStock ?? Math.round(m.safetyStock * 0.6),
        leadTimeDays: m.leadTimeDays,
        openPurchaseQty: m.openPurchaseQty ?? 0,
        inboundQty: m.inboundQty ?? 0,
        inboundEta:
          m.inboundEtaDays != null ? daysFromNow(m.inboundEtaDays) : null,
        lastPurchasePrice: m.lastPurchasePrice,
        lastPurchaseDate:
          m.lastPurchaseDaysAgo != null ? daysAgo(m.lastPurchaseDaysAgo) : null,
        targetPrice: m.targetPrice,
        currency: "USD",
        incoterm: m.incoterm,
        notes: m.notes ?? "SAMPLE data.",
        isActive: true,
        isSample: true,
        suppliers: {
          create: m.approved.map((code) => ({
            supplierId: sup(code),
            isApproved: true,
          })),
        },
      },
    });
    materials.set(m.code, { id: created.id, unit: "kg" });
  }
  const mat = (code: string) => {
    const m = materials.get(code);
    if (!m) throw new Error(`Unknown material ${code}`);
    return m;
  };

  // ------------------------------------------------------------ quotations
  let quoteSeq = 100;

  type SeriesOpts = {
    /** prices oldest → newest, one per month */
    prices: number[];
    priceUnit?: QuantityUnit; // default kg
    quantity?: number; // in priceUnit terms? No — always in quantityUnit (= priceUnit here)
    containerCount?: number;
    qtyPerContainer?: number;
    paymentTerms?: string;
    paymentTermDays?: number;
    incoterm?: string;
    destinationPort?: string;
    leadTimeDays?: number;
    productionLeadTimeDays?: number;
    freightIncluded?: boolean;
    /** how many days ago the newest quotation was issued (default 5) */
    lastQuoteDaysAgo?: number;
    /** validity window in days from quotation date (default 30) */
    validityDays?: number;
    remarks?: string;
  };

  async function quoteSeries(
    materialCode: string,
    supplierCode: string,
    opts: SeriesOpts
  ) {
    const m = mat(materialCode);
    const unit = opts.priceUnit ?? "kg";
    const n = opts.prices.length;
    // Anchor the whole series to the newest quotation date so entries stay
    // strictly chronological (one per month, walking backwards).
    const newestDate = daysAgo(opts.lastQuoteDaysAgo ?? 5);
    for (let i = 0; i < n; i++) {
      const monthsBack = n - 1 - i;
      let date: Date;
      if (monthsBack === 0) {
        date = newestDate;
      } else {
        date = new Date(newestDate);
        date.setDate(Math.min(date.getDate(), 28));
        date.setMonth(date.getMonth() - monthsBack);
        date.setDate(date.getDate() - ((i * 3) % 7));
      }
      const price = opts.prices[i];
      const validityDays = opts.validityDays ?? 30;
      await prisma.quotation.create({
        data: {
          quotationNumber: `${supplierCode}-Q${String(quoteSeq++)}`,
          supplierId: sup(supplierCode),
          materialId: m.id,
          quotationDate: date,
          quantity: opts.quantity ?? 24_000,
          quantityUnit: unit,
          containerCount: opts.containerCount,
          qtyPerContainer: opts.qtyPerContainer,
          price,
          currency: "USD",
          priceUnit: unit,
          normalizedPrice: roundPrice(convertPrice(price, unit, m.unit)),
          incoterm: opts.incoterm ?? "CIF",
          destinationPort: opts.destinationPort ?? "Gemlik, Türkiye",
          paymentTerms: opts.paymentTerms,
          paymentTermDays: opts.paymentTermDays,
          leadTimeDays: opts.leadTimeDays,
          productionLeadTimeDays: opts.productionLeadTimeDays,
          validUntil: new Date(date.getTime() + validityDays * 86_400_000),
          freightIncluded: (opts.incoterm ?? "CIF") !== "EXW",
          status: "ACTIVE",
          remarks: opts.remarks ?? "SAMPLE quotation.",
          isSample: true,
        },
      });
    }
  }

  // PMDI — 12-month Wanhua series; latest is EXPIRED (validity passed) so the
  // engine asks for a fresh RFQ. Latest 1.79 vs previous 1.84 → -2.72%.
  await quoteSeries("RM-PMDI-001", "WANHUA", {
    prices: [1.92, 1.9, 1.88, 1.86, 1.87, 1.85, 1.83, 1.84, 1.82, 1.8, 1.84, 1.79],
    quantity: 88_000,
    containerCount: 4,
    qtyPerContainer: 22_000,
    paymentTerms: "LC 60 days",
    paymentTermDays: 60,
    incoterm: "CIF",
    leadTimeDays: 45,
    productionLeadTimeDays: 20,
    lastQuoteDaysAgo: 41,
    validityDays: 30, // expired 11 days ago
    remarks: "SAMPLE — monthly offer, 4 FCL drums.",
  });
  // Backup PMDI sources — both quotes are OLDER than Wanhua's newest so the
  // material-level "latest price" stays 1.79 (and both are expired, so the
  // engine correctly demands a fresh RFQ).
  await quoteSeries("RM-PMDI-001", "COVESTRO", {
    prices: [2.02, 1.98, 1.95],
    quantity: 46_000,
    containerCount: 2,
    qtyPerContainer: 23_000,
    paymentTerms: "Net 30",
    paymentTermDays: 30,
    incoterm: "DAP",
    destinationPort: "Istanbul, Türkiye",
    leadTimeDays: 21,
    lastQuoteDaysAgo: 75,
    validityDays: 30,
    remarks: "SAMPLE — EU origin, faster but pricier.",
  });
  await quoteSeries("RM-PMDI-001", "KIMPEX", {
    prices: [1.88],
    quantity: 22_000,
    paymentTerms: "Net 45",
    paymentTermDays: 45,
    incoterm: "DAP",
    destinationPort: "Istanbul, Türkiye",
    leadTimeDays: 10,
    lastQuoteDaysAgo: 95,
    remarks: "SAMPLE — spot lot from bonded warehouse.",
  });

  // HS3130 Transparent — target 2.30, latest 2.41 → +4.8% above target.
  await quoteSeries("RM-SIL-3130T", "HOSHINE", {
    prices: [2.18, 2.22, 2.26, 2.31, 2.35, 2.38, 2.42, 2.45, 2.46, 2.44, 2.44, 2.41],
    quantity: 114_000,
    containerCount: 5,
    qtyPerContainer: 22_800,
    paymentTerms: "TT 30 days",
    paymentTermDays: 30,
    incoterm: "CIF",
    leadTimeDays: 50,
    productionLeadTimeDays: 25,
    lastQuoteDaysAgo: 6,
    validityDays: 21,
    remarks: "SAMPLE — 5 FCL offer for next campaign.",
  });

  await quoteSeries("RM-SIL-3130W", "HOSHINE", {
    prices: [2.52, 2.5],
    quantity: 45_600,
    containerCount: 2,
    qtyPerContainer: 22_800,
    paymentTerms: "TT 30 days",
    paymentTermDays: 30,
    leadTimeDays: 50,
    lastQuoteDaysAgo: 8,
  });
  await quoteSeries("RM-SIL-3130B", "HOSHINE", {
    prices: [2.55],
    quantity: 22_800,
    paymentTerms: "TT 30 days",
    paymentTermDays: 30,
    leadTimeDays: 50,
    lastQuoteDaysAgo: 55,
  });

  // Polyol 56 — two suppliers for comparison screen.
  await quoteSeries("RM-POL-056", "INOV", {
    prices: [1.24, 1.22, 1.21, 1.2, 1.22, 1.21, 1.19, 1.2],
    quantity: 80_000,
    containerCount: 4,
    qtyPerContainer: 20_000,
    paymentTerms: "LC 90 days",
    paymentTermDays: 90,
    incoterm: "CIF",
    leadTimeDays: 42,
    lastQuoteDaysAgo: 7,
  });
  await quoteSeries("RM-POL-056", "DOW", {
    prices: [1.32, 1.3, 1.28, 1.27, 1.26, 1.28],
    quantity: 40_000,
    paymentTerms: "Net 45",
    paymentTermDays: 45,
    incoterm: "DAP",
    destinationPort: "Istanbul, Türkiye",
    leadTimeDays: 18,
    lastQuoteDaysAgo: 12,
    remarks: "SAMPLE — EU warehouse, short lead time.",
  });

  await quoteSeries("RM-POL-120", "INOV", {
    prices: [1.34, 1.32, 1.3],
    quantity: 40_000,
    paymentTerms: "LC 90 days",
    paymentTermDays: 90,
    leadTimeDays: 42,
    lastQuoteDaysAgo: 18,
  });
  await quoteSeries("RM-POL-160", "INOV", {
    prices: [1.4, 1.37],
    quantity: 20_000,
    paymentTermDays: 90,
    paymentTerms: "LC 90 days",
    leadTimeDays: 42,
    lastQuoteDaysAgo: 25,
  });
  // Polyol 250-2 — valid offer below target → BUY NOW candidate.
  await quoteSeries("RM-POL-2502", "INOV", {
    prices: [1.45, 1.41],
    quantity: 20_000,
    paymentTerms: "LC 90 days",
    paymentTermDays: 90,
    leadTimeDays: 42,
    lastQuoteDaysAgo: 5,
    validityDays: 25,
  });
  await quoteSeries("RM-POL-2503", "INOV", {
    prices: [1.5, 1.47],
    quantity: 20_000,
    paymentTermDays: 90,
    paymentTerms: "LC 90 days",
    leadTimeDays: 42,
    lastQuoteDaysAgo: 30,
  });
  await quoteSeries("RM-POL-400", "DOW", {
    prices: [1.66, 1.64, 1.61],
    quantity: 20_000,
    paymentTerms: "Net 45",
    paymentTermDays: 45,
    incoterm: "DAP",
    leadTimeDays: 18,
    lastQuoteDaysAgo: 9,
  });

  // OH polymers.
  await quoteSeries("RM-OHP-20K", "HOSHINE", {
    prices: [2.55, 2.52, 2.5, 2.46, 2.44, 2.42],
    quantity: 22_800,
    paymentTerms: "TT 30 days",
    paymentTermDays: 30,
    leadTimeDays: 50,
    lastQuoteDaysAgo: 6,
  });
  await quoteSeries("RM-OHP-50K", "HOSHINE", {
    prices: [2.6, 2.56],
    quantity: 22_800,
    paymentTermDays: 30,
    paymentTerms: "TT 30 days",
    leadTimeDays: 50,
    lastQuoteDaysAgo: 40,
  });
  await quoteSeries("RM-OHP-80K", "HOSHINE", {
    prices: [2.72, 2.68, 2.66],
    quantity: 16_000,
    paymentTermDays: 30,
    paymentTerms: "TT 30 days",
    leadTimeDays: 50,
    lastQuoteDaysAgo: 15,
  });

  // CP52 quoted in USD/MT to exercise price normalization (865 $/MT = 0.865 $/kg).
  await quoteSeries("RM-CP-52", "XUYE", {
    prices: [820, 810, 825, 840, 865],
    priceUnit: "MT",
    quantity: 66, // 66 MT ≈ 3 FCL flexitanks
    containerCount: 3,
    qtyPerContainer: 22,
    paymentTerms: "TT 30 days",
    paymentTermDays: 30,
    leadTimeDays: 35,
    lastQuoteDaysAgo: 10,
    remarks: "SAMPLE — quoted per MT; chlorine cost pushing price up.",
  });
  await quoteSeries("RM-CP-45", "XUYE", {
    prices: [770, 760],
    priceUnit: "MT",
    quantity: 44,
    paymentTermDays: 30,
    paymentTerms: "TT 30 days",
    leadTimeDays: 35,
    lastQuoteDaysAgo: 50,
  });

  // DME / Methanol.
  await quoteSeries("RM-DME-001", "LUXI", {
    prices: [0.98, 0.96, 0.95, 0.97, 0.99, 1.01],
    quantity: 60_000,
    paymentTerms: "TT 30 days",
    paymentTermDays: 30,
    leadTimeDays: 30,
    lastQuoteDaysAgo: 4,
    validityDays: 20,
  });
  await quoteSeries("RM-MEOH-001", "LUXI", {
    prices: [0.395, 0.385, 0.372, 0.36, 0.352, 0.342],
    quantity: 45_000,
    paymentTerms: "TT 30 days",
    paymentTermDays: 30,
    leadTimeDays: 25,
    lastQuoteDaysAgo: 8,
  });

  // Hydrocarbons — local, short lead.
  await quoteSeries("RM-HC-IBU", "MARPET", {
    prices: [0.75, 0.74, 0.76],
    quantity: 20_000,
    paymentTerms: "Net 30",
    paymentTermDays: 30,
    incoterm: "FCA",
    destinationPort: "Kocaeli, Türkiye",
    leadTimeDays: 7,
    lastQuoteDaysAgo: 11,
  });
  await quoteSeries("RM-HC-HEX", "MARPET", {
    prices: [0.86],
    quantity: 10_000,
    paymentTermDays: 30,
    paymentTerms: "Net 30",
    incoterm: "FCA",
    leadTimeDays: 7,
    lastQuoteDaysAgo: 60,
  });

  // ---------------------------------------------------------------- actions
  const actionRows = [
    {
      title: "Send PMDI RFQ to Wanhua and Covestro",
      materialCode: "RM-PMDI-001",
      supplierCode: "WANHUA",
      actionType: "RFQ",
      owner: "Purchasing Manager",
      dueDate: daysAgo(2),
      priority: "HIGH",
      status: "OPEN",
      notes: "SAMPLE — last Wanhua offer expired; coverage tightening.",
    },
    {
      title: "Request OH Polymer 80K spot offers",
      materialCode: "RM-OHP-80K",
      supplierCode: "HOSHINE",
      actionType: "RFQ",
      owner: "Purchasing Manager",
      dueDate: daysAgo(1),
      priority: "URGENT",
      status: "OPEN",
      notes: "SAMPLE — stock below safety, no shipment booked.",
    },
    {
      title: "Place urgent DME purchase order",
      materialCode: "RM-DME-001",
      supplierCode: "LUXI",
      actionType: "PURCHASE",
      owner: "Purchasing Manager",
      dueDate: daysFromNow(1),
      priority: "URGENT",
      status: "IN_PROGRESS",
      notes: "SAMPLE — top-up beyond the 60 t already on the water.",
    },
    {
      title: "Negotiate CP52 increase with Xuye",
      materialCode: "RM-CP-52",
      supplierCode: "XUYE",
      actionType: "NEGOTIATION",
      owner: "Purchasing Manager",
      dueDate: daysFromNow(5),
      priority: "HIGH",
      status: "OPEN",
      notes: "SAMPLE — +8% vs target; challenge chlorine cost pass-through.",
    },
    {
      title: "Follow up Hoshine HS3130 validity extension",
      materialCode: "RM-SIL-3130T",
      supplierCode: "HOSHINE",
      actionType: "SUPPLIER_FOLLOW_UP",
      owner: "Purchasing Manager",
      dueDate: daysFromNow(3),
      priority: "MEDIUM",
      status: "WAITING_SUPPLIER",
      notes: "SAMPLE — asked to hold 2.41 for 3 more weeks.",
    },
    {
      title: "Book inland freight for Polyol 400 inbound",
      materialCode: "RM-POL-400",
      supplierCode: "DOW",
      actionType: "LOGISTICS",
      owner: "Logistics",
      dueDate: daysFromNow(7),
      priority: "MEDIUM",
      status: "WAITING_INTERNAL",
      notes: "SAMPLE.",
    },
    {
      title: "Collect COA and REACH file from INOV",
      materialCode: "RM-POL-056",
      supplierCode: "INOV",
      actionType: "DOCUMENTATION",
      owner: "Quality",
      dueDate: daysFromNow(10),
      priority: "LOW",
      status: "WAITING_SUPPLIER",
      notes: "SAMPLE.",
    },
    {
      title: "Evaluate second CP source (sample material)",
      materialCode: "RM-CP-52",
      supplierCode: null,
      actionType: "SAMPLE",
      owner: "Quality",
      dueDate: daysFromNow(20),
      priority: "MEDIUM",
      status: "IN_PROGRESS",
      notes: "SAMPLE — reduce single-source dependency.",
    },
    {
      title: "Monthly methanol market check",
      materialCode: "RM-MEOH-001",
      supplierCode: null,
      actionType: "MARKET_CHECK",
      owner: "Purchasing Manager",
      dueDate: daysFromNow(14),
      priority: "LOW",
      status: "OPEN",
      notes: "SAMPLE — price drifting down; verify floor before booking.",
    },
    {
      title: "Confirm Polyol 56 PO quantity with INOV",
      materialCode: "RM-POL-056",
      supplierCode: "INOV",
      actionType: "PURCHASE",
      owner: "Purchasing Manager",
      dueDate: daysAgo(6),
      priority: "MEDIUM",
      status: "COMPLETED",
      notes: "SAMPLE — 80 t confirmed, in open PO.",
    },
  ];
  for (const a of actionRows) {
    await prisma.actionItem.create({
      data: {
        title: a.title,
        materialId: a.materialCode ? mat(a.materialCode).id : null,
        supplierId: a.supplierCode ? sup(a.supplierCode) : null,
        actionType: a.actionType,
        owner: a.owner,
        dueDate: a.dueDate,
        priority: a.priority,
        status: a.status,
        notes: a.notes,
        isSample: true,
      },
    });
  }

  // ------------------------------------------------------- market intel
  const intelRows = [
    {
      date: daysAgo(3),
      title: "Hormuz Strait: tighter tanker inspections announced",
      category: "GEOPOLITICAL",
      affectedMaterials: "RM-DME-001, RM-MEOH-001, HYDROCARBONS",
      geography: "Middle East / Gulf",
      summary:
        "SAMPLE — Inspection regime around Hormuz adds 3–6 days to Gulf loadings and pressures tanker availability.",
      expectedImpact: "Freight and propellant-grade cargo premiums likely.",
      priceDirection: "UP",
      supplyImpact: "MODERATE",
      riskLevel: "HIGH",
      source: "Manual entry",
      url: null,
      notes: "Watch weekly; affects DME and methanol shipping windows.",
    },
    {
      date: daysAgo(6),
      title: "Wanhua Yantai MDI unit maintenance planned next month",
      category: "SUPPLIER",
      affectedMaterials: "RM-PMDI-001, PMDI",
      geography: "China",
      summary:
        "SAMPLE — Scheduled turnaround reduces polymeric MDI availability for 3–4 weeks; allocation possible.",
      expectedImpact: "Spot PMDI may firm during the maintenance window.",
      priceDirection: "UP",
      supplyImpact: "MODERATE",
      riskLevel: "HIGH",
      source: "Manual entry",
      url: null,
      notes: "Secure August–September volumes before the shutdown.",
    },
    {
      date: daysAgo(9),
      title: "Red Sea diversions keep Asia–Mediterranean freight elevated",
      category: "FREIGHT",
      affectedMaterials: "PMDI, SILICONE, OH_POLYMER, CHLORINATED_PARAFFIN",
      geography: "Red Sea / Suez",
      summary:
        "SAMPLE — Continued Cape routing adds ~10–14 days transit and keeps FEU rates high on Asia–Med lanes.",
      expectedImpact: "CIF offers from China embed higher freight; longer lead times.",
      priceDirection: "UP",
      supplyImpact: "MINOR",
      riskLevel: "MEDIUM",
      source: "Manual entry",
      url: null,
      notes: null,
    },
    {
      date: daysAgo(12),
      title: "China DMC (siloxane) prices firming on energy costs",
      category: "MARKET",
      affectedMaterials: "SILICONE, OH_POLYMER",
      geography: "China",
      summary:
        "SAMPLE — DMC upstream costs rose ~5% m/m; silicone polymer producers signalling increases.",
      expectedImpact: "HS3130 and OH polymer offers may rise next round.",
      priceDirection: "UP",
      supplyImpact: "MINOR",
      riskLevel: "MEDIUM",
      source: "Manual entry",
      url: null,
      notes: null,
    },
    {
      date: daysAgo(8),
      title: "Chlorine feedstock tightness in Shandong",
      category: "MARKET",
      affectedMaterials: "CHLORINATED_PARAFFIN",
      geography: "China",
      summary:
        "SAMPLE — Regional chlor-alkali maintenance limits chlorine supply to CP producers; offers already up.",
      expectedImpact: "CP52/CP45 offers likely to rise further next round.",
      priceDirection: "UP",
      supplyImpact: "MODERATE",
      riskLevel: "HIGH",
      source: "Manual entry",
      url: null,
      notes: "Single-sourced CP — consider securing next quarter volume.",
    },
    {
      date: daysAgo(15),
      title: "Methanol supply comfortable; inventories high",
      category: "MARKET",
      affectedMaterials: "RM-MEOH-001, RM-DME-001",
      geography: "Global",
      summary:
        "SAMPLE — Coastal methanol inventories at seasonal highs; buyers holding back.",
      expectedImpact: "Downward drift may continue short term.",
      priceDirection: "DOWN",
      supplyImpact: "NONE",
      riskLevel: "LOW",
      source: "Manual entry",
      url: null,
      notes: "Favourable window for methanol-linked buying.",
    },
    {
      date: daysAgo(20),
      title: "USD/TRY volatility raises import financing costs",
      category: "MACRO",
      affectedMaterials: "ALL",
      geography: "Türkiye",
      summary:
        "SAMPLE — FX volatility widens forward premiums; LC costs up for import-heavy positions.",
      expectedImpact: "Effective landed cost up even at flat USD prices.",
      priceDirection: "UNCERTAIN",
      supplyImpact: "NONE",
      riskLevel: "MEDIUM",
      source: "Manual entry",
      url: null,
      notes: null,
    },
  ];
  for (const i of intelRows) {
    await prisma.marketIntelligence.create({
      data: { ...i, isActive: true, isSample: true },
    });
  }

  const counts = {
    suppliers: await prisma.supplier.count(),
    materials: await prisma.material.count(),
    quotations: await prisma.quotation.count(),
    actions: await prisma.actionItem.count(),
    intel: await prisma.marketIntelligence.count(),
    settings: await prisma.setting.count(),
  };
  console.log("Seed complete:", counts);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());

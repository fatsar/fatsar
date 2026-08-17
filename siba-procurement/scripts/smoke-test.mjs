/**
 * End-to-end smoke test — walks the Phase 1 acceptance criteria in a real
 * browser against a running server.
 *
 *   1. npm run build && npm run start     (in another shell)
 *   2. npm run test:e2e
 *
 * It CREATES records (Test Chemicals GmbH, RM-TEST-001, TESTSUP-Q001,
 * "Smoke-test follow-up") and leaves them behind — run `npm run db:seed`
 * afterwards to restore a clean SAMPLE database.
 *
 * Env:
 *   BASE_URL   default http://localhost:3000
 *   SHOT_DIR   where screenshots are written (default ./shots)
 *   CHROME     browser executable; unset uses Playwright's own download
 */
import { mkdir } from "node:fs/promises";
import { chromium } from "playwright";

const BASE = process.env.BASE_URL ?? "http://localhost:3000";
const SHOTS = process.env.SHOT_DIR ?? "./shots";
let failures = 0;

function check(name, condition) {
  if (condition) {
    console.log(`  ✓ ${name}`);
  } else {
    failures++;
    console.log(`  ✗ FAIL: ${name}`);
  }
}

await mkdir(SHOTS, { recursive: true });
const browser = await chromium.launch(
  process.env.CHROME ? { executablePath: process.env.CHROME } : {}
);
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(15000);

// ---------- 1. Dashboard ----------
console.log("1. Dashboard");
await page.goto(`${BASE}/`, { waitUntil: "networkidle" });
check("title", (await page.title()).includes("Siba Procurement"));
check("KPI: materials requiring action", await page.getByText("Materials requiring action").isVisible());
check("KPI: high-risk materials", await page.getByText("High-risk materials").isVisible());
check("KPI: overdue actions", await page.getByText("Overdue purchasing actions").isVisible());
check("priority table present", await page.getByText("Priority Purchasing Actions").isVisible());
check("PMDI row visible", await page.getByRole("link", { name: "PMDI", exact: true }).first().isVisible());
check("REQUEST QUOTATION badge", (await page.getByText("REQUEST QUOTATION").count()) > 0);
check("URGENT PURCHASE badge", (await page.getByText("URGENT PURCHASE").count()) > 0);
check("sample banner", await page.getByText("SAMPLE development data").isVisible());
check("market alerts panel", await page.getByText("Active Market Alerts").isVisible());
await page.screenshot({ path: `${SHOTS}/01-dashboard.png`, fullPage: true });

// ---------- 2. Materials list ----------
console.log("2. Materials");
await page.goto(`${BASE}/materials`, { waitUntil: "networkidle" });
check("19 materials counted", await page.getByText("19 of 19").isVisible());
check("coverage column", await page.getByRole("button", { name: /Coverage/ }).first().isVisible());
await page.screenshot({ path: `${SHOTS}/02-materials.png` });

// filter by search box
await page.getByLabel("Filter table").fill("HS3130");
check("filter narrows to 3", await page.getByText("3 of 19").isVisible());
await page.getByLabel("Filter table").fill("");

// ---------- 3. Material detail (PMDI) ----------
console.log("3. Material detail — PMDI");
await page.getByLabel("Filter table").fill("PMDI");
await page.getByRole("cell", { name: /RM-PMDI-001/ }).first().click();
await page.waitForURL(/\/materials\/\w+/);
check("recommendation card", await page.getByText("Recommendation", { exact: true }).isVisible());
check("reasons list mentions coverage", (await page.getByText(/stock coverage is 32 days/i).count()) > 0);
check("risk factors with points", (await page.getByText(/inside the 45-day supplier lead time/).count()) > 0);
check("confidence explanation exists", await page.getByText("How confidence was calculated").isVisible());
check("price snapshot latest 1.79", (await page.getByText("$1.79/kg").count()) > 0);
check("change -2.72%", (await page.getByText("-2.72%").count()) > 0);
await page.screenshot({ path: `${SHOTS}/03-material-detail.png`, fullPage: true });

// ---------- 4. Create supplier ----------
console.log("4. Create supplier");
await page.goto(`${BASE}/suppliers/new`, { waitUntil: "networkidle" });
await page.fill("#code", "TESTSUP");
await page.fill("#name", "Test Chemicals GmbH");
await page.fill("#country", "Germany");
await page.fill("#paymentTerms", "Net 60");
await page.fill("#paymentTermDays", "60");
await page.fill("#normalLeadTimeDays", "14");
await page.fill("#qualityRating", "8");
await page.fill("#pricingRating", "7");
await page.getByRole("button", { name: "Create supplier" }).click();
await page.waitForSelector('h1:has-text("Test Chemicals GmbH")', { timeout: 20000 });
check("supplier detail shows name", !page.url().endsWith("/new"));
check("score computed from partial ratings", await page.getByText("/100").first().isVisible());
await page.screenshot({ path: `${SHOTS}/04-supplier-detail.png`, fullPage: true });

// ---------- 5. Create material ----------
console.log("5. Create material");
await page.goto(`${BASE}/materials/new`, { waitUntil: "networkidle" });
await page.fill("#code", "RM-TEST-001");
await page.fill("#name", "Test Additive");
await page.selectOption("#category", "ADDITIVES");
await page.fill("#avgMonthlyConsumption", "9000");
await page.fill("#currentStock", "12000");
await page.fill("#reservedStock", "1000");
await page.fill("#safetyStock", "4500");
await page.fill("#leadTimeDays", "14");
await page.fill("#targetPrice", "2.5");
await page.getByLabel("Test Chemicals GmbH").check();
await page.selectOption("#preferredSupplierId", { label: "Test Chemicals GmbH (TESTSUP)" });
await page.getByRole("button", { name: "Create material" }).click();
await page.waitForSelector('h1:has-text("Test Additive")', { timeout: 20000 });
check("detail page opened", !page.url().endsWith("/new"));
// usable 11,000 / 300 per day = 36.7 days, displayed as "37 d"
check("coverage 37 d computed", (await page.getByText("37 d").count()) > 0);
check("recommendation rendered", (await page.getByText(/REQUEST QUOTATION|MONITOR|WAIT/).count()) > 0);
const materialUrl = page.url();

// ---------- 6. Edit material (stock update → coverage changes) ----------
console.log("6. Edit material");
await page.goto(`${materialUrl}/edit`, { waitUntil: "networkidle" });
await page.fill("#currentStock", "3000");
await page.getByRole("button", { name: "Save changes" }).click();
await page.waitForSelector("text=Material updated.", { timeout: 15000 });
await page.goto(materialUrl, { waitUntil: "networkidle" });
// usable 2,000 / 300 = 6.7 days → urgent/req quotation
check("coverage recalculated 6.7 d", (await page.getByText("6.7 d").count()) > 0);
check("urgency escalated", (await page.getByText(/URGENT PURCHASE|REQUEST QUOTATION/).count()) > 0);

// ---------- 7. Create quotation for the new material ----------
console.log("7. Create quotation");
await page.goto(`${BASE}/quotations/new`, { waitUntil: "networkidle" });
await page.fill("#quotationNumber", "TESTSUP-Q001");
await page.selectOption("#materialId", { label: "Test Additive (base: kg)" });
await page.selectOption("#supplierId", { label: "Test Chemicals GmbH" });
await page.fill("#price", "2400");
await page.selectOption("#priceUnit", "MT");
await page.fill("#quantity", "5");
await page.selectOption("#quantityUnit", "MT");
await page.fill("#paymentTermDays", "60");
await page.fill("#leadTimeDays", "14");
const future = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
await page.fill("#validUntil", future);
await page.getByRole("button", { name: "Save quotation" }).click();
await page.waitForURL(/\/quotations\/compare/, { timeout: 15000 });
check("landed on compare", page.url().includes("compare"));
// normalized price: 2400/MT → 2.40/kg
check("normalized $2.40/kg", (await page.getByText("$2.40/kg").count()) > 0);
check("BEST OVERALL badge", (await page.getByText("BEST OVERALL OFFER").count()) > 0);
check("scoring breakdown visible", await page.getByText("How the overall score is calculated").isVisible());
await page.screenshot({ path: `${SHOTS}/05-compare.png`, fullPage: true });

// ---------- 8. Compare PMDI (multi-supplier) ----------
console.log("8. Compare PMDI offers");
await page.goto(`${BASE}/quotations/compare`, { waitUntil: "networkidle" });
await page.getByRole("link", { name: "PMDI", exact: true }).click();
await page.waitForSelector("text=offer comparison", { timeout: 20000 });
await page.waitForSelector("text=Wanhua Chemical Group", { timeout: 20000 });
check("3 suppliers compared", (await page.getByText("Wanhua Chemical Group").count()) > 0 && (await page.getByText("Covestro AG").count()) > 0);
check("expired offer flagged", (await page.getByText("EXPIRED").count()) > 0);
check("warning about validity", (await page.getByText(/past their validity date/).count()) > 0);

// ---------- 9. Price history ----------
console.log("9. Price history");
await page.goto(`${BASE}/price-history`, { waitUntil: "networkidle" });
check("metrics card", (await page.getByText("price metrics").count()) > 0);
check("chart svg rendered", (await page.locator(".recharts-surface").count()) > 0);
check("12-month average", (await page.getByText("12-month average").count()) > 0);
await page.screenshot({ path: `${SHOTS}/06-price-history.png`, fullPage: true });

// ---------- 10. Purchasing plan ----------
console.log("10. Purchasing plan");
await page.goto(`${BASE}/plan`, { waitUntil: "networkidle" });
check("plan table renders", (await page.getByText("Reorder point").count()) > 0);
check("recommendation column", (await page.getByText(/confidence \d+%/).count()) > 0);
await page.screenshot({ path: `${SHOTS}/07-plan.png`, fullPage: true });

// ---------- 11. Actions ----------
console.log("11. Action center");
await page.goto(`${BASE}/actions`, { waitUntil: "networkidle" });
check("overdue flagged", (await page.getByText(/OVERDUE ·/).count()) > 0);
await page.getByRole("button", { name: "New action" }).click();
await page.fill("#a-title", "Smoke-test follow-up");
await page.selectOption("#a-type", "SUPPLIER_FOLLOW_UP");
await page.fill("#a-owner", "QA");
await page.getByRole("button", { name: "Create action" }).click();
await page.waitForSelector("text=Smoke-test follow-up", { timeout: 15000 });
check("action created & listed", (await page.getByText("Smoke-test follow-up").count()) > 0);

// ---------- 12. Market intelligence ----------
console.log("12. Market intelligence");
await page.goto(`${BASE}/market-intelligence`, { waitUntil: "networkidle" });
check("intel entries listed", (await page.getByText("Hormuz Strait").count()) > 0);

// ---------- 13. Settings ----------
console.log("13. Settings");
await page.goto(`${BASE}/settings`, { waitUntil: "networkidle" });
check("weights form", await page.getByText("Supplier score weights").isVisible());
await page.fill("#w-quality", "30");
await page.fill("#w-pricing", "15");
await page.getByRole("button", { name: "Save supplier weights" }).click();
await page.waitForSelector("text=Supplier score weights saved.", { timeout: 15000 });
check("weights saved", true);
// invalid sum rejected
await page.fill("#w-quality", "90");
await page.getByRole("button", { name: "Save supplier weights" }).click();
await page.waitForSelector("text=sum to 100", { timeout: 15000 });
check("invalid weights rejected", true);
// restore defaults
page.once("dialog", (d) => d.accept());
await page.getByRole("button", { name: "Reset to defaults" }).click();
await page.waitForSelector("text=All settings reset to defaults.", { timeout: 15000 });
check("reset works", true);

// ---------- 14. Global search ----------
console.log("14. Global search");
await page.goto(`${BASE}/`, { waitUntil: "networkidle" });
await page.getByLabel("Global search").fill("Hoshine");
await page.waitForSelector("text=Hoshine Silicon Industry", { timeout: 15000 });
check("search finds supplier", (await page.getByText("Hoshine Silicon Industry").count()) > 0);
await page.getByLabel("Global search").fill("CP52");
await page.waitForSelector("text=RM-CP-52 — CP52", { timeout: 15000 });
check("search finds material by code", true);

// ---------- 15. Deactivate + delete material ----------
console.log("15. Deactivate / delete material safety");
await page.goto(materialUrl, { waitUntil: "networkidle" });
page.once("dialog", (d) => d.accept());
await page.getByRole("button", { name: "Deactivate" }).click();
await page.waitForSelector("text=Material deactivated.", { timeout: 15000 });
check("deactivated", true);
// delete → should be prevented (has a quotation) and deactivate instead
page.once("dialog", (d) => d.accept());
await page.getByRole("button", { name: "Delete", exact: true }).click();
await page.waitForSelector("text=deactivated instead of deleted", { timeout: 15000 });
check("safe delete preserves history", true);

// ---------- 16. Mobile viewport ----------
console.log("16. Mobile");
const mobile = await browser.newPage({ viewport: { width: 390, height: 844 } });
mobile.setDefaultTimeout(15000);
await mobile.goto(`${BASE}/`, { waitUntil: "networkidle" });
check("mobile: KPI cards visible", await mobile.getByText("High-risk materials").isVisible());
check("mobile: nav toggle", await mobile.getByLabel("Open navigation").isVisible());
await mobile.getByLabel("Open navigation").click();
check("mobile: drawer opens", await mobile.getByRole("link", { name: "Purchasing Plan", exact: true }).isVisible());
await mobile.screenshot({ path: `${SHOTS}/08-mobile-dashboard.png`, fullPage: false });
await mobile.getByRole("link", { name: "Purchasing Plan", exact: true }).click();
await mobile.waitForLoadState("networkidle");
check("mobile: plan table usable", (await mobile.getByText("Coverage").count()) > 0);
await mobile.screenshot({ path: `${SHOTS}/09-mobile-plan.png` });
const horizontalOverflow = await mobile.evaluate(
  () => document.body.scrollWidth > window.innerWidth + 2
);
check("mobile: no body horizontal overflow", !horizontalOverflow);
await mobile.close();

await browser.close();
console.log(failures === 0 ? "\nALL SMOKE TESTS PASSED" : `\n${failures} FAILURES`);
process.exit(failures === 0 ? 0 : 1);

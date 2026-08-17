-- CreateTable
CREATE TABLE "Material" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "code" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "unit" TEXT NOT NULL DEFAULT 'kg',
    "specification" TEXT,
    "preferredSupplierId" TEXT,
    "avgMonthlyConsumption" REAL,
    "avgDailyConsumption" REAL,
    "forecastMonthlyDemand" REAL,
    "currentStock" REAL NOT NULL DEFAULT 0,
    "reservedStock" REAL NOT NULL DEFAULT 0,
    "safetyStock" REAL NOT NULL DEFAULT 0,
    "minimumStock" REAL NOT NULL DEFAULT 0,
    "leadTimeDays" INTEGER,
    "openPurchaseQty" REAL NOT NULL DEFAULT 0,
    "inboundQty" REAL NOT NULL DEFAULT 0,
    "inboundEta" DATETIME,
    "lastPurchasePrice" REAL,
    "lastPurchaseDate" DATETIME,
    "targetPrice" REAL,
    "currency" TEXT NOT NULL DEFAULT 'USD',
    "incoterm" TEXT,
    "notes" TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "isSample" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "Material_preferredSupplierId_fkey" FOREIGN KEY ("preferredSupplierId") REFERENCES "Supplier" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Supplier" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "code" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "country" TEXT NOT NULL,
    "city" TEXT,
    "website" TEXT,
    "contactName" TEXT,
    "contactEmail" TEXT,
    "contactPhone" TEXT,
    "preferredCurrency" TEXT NOT NULL DEFAULT 'USD',
    "defaultIncoterm" TEXT,
    "paymentTerms" TEXT,
    "paymentTermDays" INTEGER,
    "normalLeadTimeDays" INTEGER,
    "qualityRating" REAL,
    "pricingRating" REAL,
    "deliveryRating" REAL,
    "responsivenessRating" REAL,
    "technicalRating" REAL,
    "commercialRating" REAL,
    "notes" TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "isSample" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "MaterialSupplier" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "materialId" TEXT NOT NULL,
    "supplierId" TEXT NOT NULL,
    "isApproved" BOOLEAN NOT NULL DEFAULT true,
    "note" TEXT,
    CONSTRAINT "MaterialSupplier_materialId_fkey" FOREIGN KEY ("materialId") REFERENCES "Material" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "MaterialSupplier_supplierId_fkey" FOREIGN KEY ("supplierId") REFERENCES "Supplier" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Quotation" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "quotationNumber" TEXT NOT NULL,
    "supplierId" TEXT NOT NULL,
    "materialId" TEXT NOT NULL,
    "quotationDate" DATETIME NOT NULL,
    "quantity" REAL NOT NULL,
    "quantityUnit" TEXT NOT NULL,
    "containerCount" INTEGER,
    "qtyPerContainer" REAL,
    "price" REAL NOT NULL,
    "currency" TEXT NOT NULL,
    "priceUnit" TEXT NOT NULL,
    "normalizedPrice" REAL NOT NULL,
    "incoterm" TEXT,
    "destinationPort" TEXT,
    "paymentTerms" TEXT,
    "paymentTermDays" INTEGER,
    "leadTimeDays" INTEGER,
    "productionLeadTimeDays" INTEGER,
    "validUntil" DATETIME,
    "freightIncluded" BOOLEAN NOT NULL DEFAULT false,
    "status" TEXT NOT NULL DEFAULT 'ACTIVE',
    "remarks" TEXT,
    "attachmentRef" TEXT,
    "isSample" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "Quotation_supplierId_fkey" FOREIGN KEY ("supplierId") REFERENCES "Supplier" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "Quotation_materialId_fkey" FOREIGN KEY ("materialId") REFERENCES "Material" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ActionItem" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "title" TEXT NOT NULL,
    "materialId" TEXT,
    "supplierId" TEXT,
    "quotationId" TEXT,
    "actionType" TEXT NOT NULL,
    "owner" TEXT,
    "dueDate" DATETIME,
    "priority" TEXT NOT NULL DEFAULT 'MEDIUM',
    "status" TEXT NOT NULL DEFAULT 'OPEN',
    "notes" TEXT,
    "isSample" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ActionItem_materialId_fkey" FOREIGN KEY ("materialId") REFERENCES "Material" ("id") ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "ActionItem_supplierId_fkey" FOREIGN KEY ("supplierId") REFERENCES "Supplier" ("id") ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "ActionItem_quotationId_fkey" FOREIGN KEY ("quotationId") REFERENCES "Quotation" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "MarketIntelligence" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "date" DATETIME NOT NULL,
    "title" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "affectedMaterials" TEXT NOT NULL,
    "geography" TEXT,
    "summary" TEXT NOT NULL,
    "expectedImpact" TEXT,
    "priceDirection" TEXT NOT NULL DEFAULT 'UNCERTAIN',
    "supplyImpact" TEXT,
    "riskLevel" TEXT NOT NULL DEFAULT 'MEDIUM',
    "source" TEXT,
    "url" TEXT,
    "notes" TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "isSample" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "Setting" (
    "key" TEXT NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "updatedAt" DATETIME NOT NULL
);

-- CreateIndex
CREATE UNIQUE INDEX "Material_code_key" ON "Material"("code");

-- CreateIndex
CREATE INDEX "Material_category_idx" ON "Material"("category");

-- CreateIndex
CREATE INDEX "Material_isActive_idx" ON "Material"("isActive");

-- CreateIndex
CREATE UNIQUE INDEX "Supplier_code_key" ON "Supplier"("code");

-- CreateIndex
CREATE INDEX "Supplier_isActive_idx" ON "Supplier"("isActive");

-- CreateIndex
CREATE UNIQUE INDEX "MaterialSupplier_materialId_supplierId_key" ON "MaterialSupplier"("materialId", "supplierId");

-- CreateIndex
CREATE INDEX "Quotation_materialId_quotationDate_idx" ON "Quotation"("materialId", "quotationDate");

-- CreateIndex
CREATE INDEX "Quotation_supplierId_idx" ON "Quotation"("supplierId");

-- CreateIndex
CREATE INDEX "Quotation_quotationNumber_idx" ON "Quotation"("quotationNumber");

-- CreateIndex
CREATE INDEX "ActionItem_status_idx" ON "ActionItem"("status");

-- CreateIndex
CREATE INDEX "ActionItem_dueDate_idx" ON "ActionItem"("dueDate");

-- CreateIndex
CREATE INDEX "MarketIntelligence_isActive_date_idx" ON "MarketIntelligence"("isActive", "date");

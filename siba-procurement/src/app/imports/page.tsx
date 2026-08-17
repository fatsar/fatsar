import type { Metadata } from "next";
import { FileSpreadsheet, Upload } from "lucide-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";

export const metadata: Metadata = { title: "Files / Imports" };

export default function ImportsPage() {
  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <PageHeader
        title="Files / Imports"
        description="CSV / XLSX import module — planned for Phase 2."
      />
      <Card>
        <CardHeader
          title={
            <span className="inline-flex items-center gap-2">
              <Upload size={16} /> Planned import flow
            </span>
          }
          description="The architecture and file formats are already specified in docs/import-format.md."
        />
        <CardContent className="space-y-3 text-[13.5px] leading-6 text-ink-secondary">
          <p>
            Phase 2 adds imports for the material master, supplier master, quotation
            history, purchasing history and stock data (CSV first, then XLSX). The
            committed process:
          </p>
          <ol className="list-decimal space-y-1 pl-5">
            <li>Upload file</li>
            <li>Preview rows and auto-detect columns</li>
            <li>Manual column mapping where detection is wrong</li>
            <li>Validate every row against the same zod schemas the forms use</li>
            <li>Show all errors — invalid rows are never silently ignored</li>
            <li>Import summary and explicit confirmation</li>
            <li>Import valid rows and store an import log</li>
          </ol>
          <p className="flex items-center gap-2 rounded-md border border-line bg-sunken px-3 py-2 text-[13px]">
            <FileSpreadsheet size={15} className="shrink-0" />
            Until then, materials, suppliers and quotations are maintained through
            their forms — every import will reuse exactly the same validation.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

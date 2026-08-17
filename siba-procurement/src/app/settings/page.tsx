import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import {
  getOfferScoreWeights,
  getRecommendationConfig,
  getSupplierScoreWeights,
} from "@/server/settings";
import {
  OfferWeightsForm,
  ResetDefaults,
  SupplierWeightsForm,
  ThresholdsForm,
} from "./settings-forms";

export const metadata: Metadata = { title: "Settings" };

export default async function SettingsPage() {
  const [supplierWeights, offerWeights, config] = await Promise.all([
    getSupplierScoreWeights(),
    getOfferScoreWeights(),
    getRecommendationConfig(),
  ]);

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <PageHeader
        title="Settings"
        description="Business parameters of the deterministic engines. Nothing here is hard-coded — changes apply to every screen immediately."
      />
      <SupplierWeightsForm weights={supplierWeights} />
      <OfferWeightsForm weights={offerWeights} />
      <ThresholdsForm config={config} />
      <ResetDefaults />
    </div>
  );
}

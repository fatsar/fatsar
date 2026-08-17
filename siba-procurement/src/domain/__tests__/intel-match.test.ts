import { describe, expect, it } from "vitest";
import { intelMatchesMaterial } from "../intel-match";

const pmdi = { code: "RM-PMDI-001", category: "PMDI" };

describe("intelMatchesMaterial", () => {
  it("matches by material code, category token, or ALL", () => {
    expect(
      intelMatchesMaterial(
        { affectedMaterials: "RM-PMDI-001, SILICONE", isActive: true },
        pmdi
      )
    ).toBe(true);
    expect(
      intelMatchesMaterial({ affectedMaterials: "pmdi", isActive: true }, pmdi)
    ).toBe(true);
    expect(
      intelMatchesMaterial({ affectedMaterials: "ALL", isActive: true }, pmdi)
    ).toBe(true);
  });

  it("does not match other tokens or inactive entries", () => {
    expect(
      intelMatchesMaterial(
        { affectedMaterials: "SILICONE, RM-CP-52", isActive: true },
        pmdi
      )
    ).toBe(false);
    expect(
      intelMatchesMaterial({ affectedMaterials: "ALL", isActive: false }, pmdi)
    ).toBe(false);
  });
});

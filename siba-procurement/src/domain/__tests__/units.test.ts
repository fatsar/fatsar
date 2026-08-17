import { describe, expect, it } from "vitest";
import { canConvert, convertPrice, convertQuantity, roundPrice } from "../units";

describe("units", () => {
  it("converts MT to kg and back", () => {
    expect(convertQuantity(2, "MT", "kg")).toBe(2000);
    expect(convertQuantity(500, "kg", "MT")).toBe(0.5);
    expect(convertQuantity(7, "kg", "kg")).toBe(7);
  });

  it("converts prices across mass units", () => {
    // 865 USD/MT = 0.865 USD/kg (the CP52 seed case)
    expect(convertPrice(865, "MT", "kg")).toBeCloseTo(0.865, 6);
    expect(convertPrice(1.79, "kg", "MT")).toBeCloseTo(1790, 6);
    expect(convertPrice(3.2, "L", "L")).toBe(3.2);
  });

  it("refuses cross-dimension conversion", () => {
    expect(canConvert("L", "kg")).toBe(false);
    expect(canConvert("pcs", "MT")).toBe(false);
    expect(canConvert("kg", "MT")).toBe(true);
    expect(() => convertPrice(1, "L", "kg")).toThrow();
    expect(() => convertQuantity(1, "pcs", "kg")).toThrow();
  });

  it("rounds prices deterministically", () => {
    expect(roundPrice(1.23456789)).toBe(1.2346);
    expect(roundPrice(1.235, 2)).toBe(1.24);
  });
});

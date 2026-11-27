import { describe, expect, it } from "vitest";
import {
  formatCount,
  formatDays,
  formatHours,
  formatNumber,
  formatPercent,
  metricsErrorMessage,
} from "@/features/metrics/format";
import { httpError } from "@/test/utils";

describe("metrics formats", () => {
  it("shows numbers in Spanish without trailing zeros and at most 2 decimals", () => {
    expect(formatNumber(26)).toBe("26");
    expect(formatNumber(26.5)).toBe("26.5");
    expect(formatNumber(3.456)).toBe("3.46");
    expect(formatNumber(1234.5)).toBe("1,234.5");
  });

  it("shows ratios as percentages without capping them", () => {
    expect(formatPercent(0.3333)).toBe("33.3 %");
    expect(formatPercent(0.5)).toBe("50 %");
    expect(formatPercent(1)).toBe("100 %");
    expect(formatPercent(3)).toBe("300 %");
    expect(formatPercent(0)).toBe("0 %");
  });

  it("never shows a missing value as 0", () => {
    expect(formatPercent(null)).toBe("—");
    expect(formatPercent(undefined)).toBe("—");
    expect(formatHours(null)).toBe("Sin datos");
    expect(formatHours(undefined)).toBe("Sin datos");
  });

  it("keeps a very small average visible", () => {
    expect(formatHours(0.01)).toBe("0.01 h");
    expect(formatHours(0)).toBe("0 h");
  });

  it("uses singular and plural", () => {
    expect(formatDays(1)).toBe("1 día");
    expect(formatDays(2.5)).toBe("2.5 días");
    expect(formatCount(1, "muestra", "muestras")).toBe("1 muestra");
    expect(formatCount(3, "muestra", "muestras")).toBe("3 muestras");
  });

  it("explains the API errors", () => {
    expect(metricsErrorMessage(httpError(404), "el embudo")).toBe(
      "La vacante seleccionada ya no existe.",
    );
    expect(metricsErrorMessage(httpError(400), "el embudo")).toBe(
      "Los filtros no son válidos. Revisa las fechas.",
    );
    expect(metricsErrorMessage(httpError(500), "el embudo")).toBe(
      "No se pudo cargar el embudo.",
    );
  });
});

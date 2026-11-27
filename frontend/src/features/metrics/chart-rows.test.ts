import { describe, expect, it } from "vitest";
import {
  funnelRows,
  hasConversionOverFull,
  isFunnelEmpty,
  isTimeEmpty,
  stageTimeMinPointSize,
  stageTimeRows,
  STAGE_COLORS,
} from "@/features/metrics/chart-rows";
import type { Funnel, TimeInStage } from "@/features/metrics/types";
import { STAGES } from "@/shared/lib/stages";

const funnel: Funnel = {
  stages: [
    { stage: "APPLIED", count: 4, conversionToNext: 0.5 },
    { stage: "INTERVIEW", count: 2, conversionToNext: 1 },
    { stage: "OFFER", count: 2, conversionToNext: 1 },
    { stage: "HIRED", count: 2, conversionToNext: null },
    { stage: "REJECTED", count: 1, conversionToNext: null },
  ],
};

const times: TimeInStage = {
  stages: [
    { stage: "APPLIED", averageHours: 26, samples: 3 },
    { stage: "INTERVIEW", averageHours: 0.01, samples: 1 },
    { stage: "OFFER", averageHours: 33.5, samples: 2 },
    { stage: "HIRED", averageHours: null, samples: 0 },
    { stage: "REJECTED", averageHours: null, samples: 0 },
  ],
};

describe("funnelRows", () => {
  it("keeps the five stages in order with labels, texts and stage colors", () => {
    const rows = funnelRows(funnel);

    expect(rows.map((r) => r.stage)).toEqual([...STAGES]);
    expect(rows.map((r) => r.label)).toEqual([
      "Postulado",
      "Entrevista",
      "Oferta",
      "Contratado",
      "Rechazado",
    ]);
    expect(rows.map((r) => r.conversionText)).toEqual([
      "50 %",
      "100 %",
      "100 %",
      "—",
      "—",
    ]);
    expect(rows.map((r) => r.color)).toEqual([
      "var(--stage-applied)",
      "var(--stage-interview)",
      "var(--stage-offer)",
      "var(--stage-hired)",
      "var(--stage-rejected)",
    ]);
    expect(rows.filter((r) => !r.inSequence).map((r) => r.stage)).toEqual([
      "REJECTED",
    ]);
    expect(Object.values(STAGE_COLORS).join(" ")).not.toMatch(/#|rgb|hsl/);
  });

  it("does not cap a conversion above 100 % and notices it", () => {
    const rows = funnelRows({
      stages: [
        { stage: "APPLIED", count: 3, conversionToNext: 0.3333 },
        { stage: "INTERVIEW", count: 1, conversionToNext: 3 },
        { stage: "OFFER", count: 3, conversionToNext: 0 },
        { stage: "HIRED", count: 0 },
        { stage: "REJECTED", count: 0 },
      ],
    });

    expect(rows.map((r) => r.conversionText)).toEqual([
      "33.3 %",
      "300 %",
      "0 %",
      "—",
      "—",
    ]);
    expect(hasConversionOverFull(rows)).toBe(true);
    expect(hasConversionOverFull(funnelRows(funnel))).toBe(false);
  });

  it("is empty only when every count is 0", () => {
    expect(isFunnelEmpty(funnelRows(funnel))).toBe(false);
    expect(
      isFunnelEmpty(
        funnelRows({
          stages: STAGES.map((stage) => ({ stage, count: 0 })),
        }),
      ),
    ).toBe(true);
  });
});

describe("stageTimeRows", () => {
  it("shows Sin datos for stages without samples, never 0", () => {
    const rows = stageTimeRows(times);

    expect(rows.map((r) => r.hoursText)).toEqual([
      "26 h",
      "0.01 h",
      "33.5 h",
      "Sin datos",
      "Sin datos",
    ]);
    expect(rows.map((r) => r.averageHours)).toEqual([
      26,
      0.01,
      33.5,
      null,
      null,
    ]);
    expect(rows.map((r) => r.samplesText)).toEqual([
      "3 muestras",
      "1 muestra",
      "2 muestras",
      "0 muestras",
      "0 muestras",
    ]);
  });

  it("draws a tiny average with a visible bar and no bar without data", () => {
    expect(stageTimeMinPointSize(0.01)).toBeGreaterThan(0);
    expect(stageTimeMinPointSize(0)).toBeGreaterThan(0);
    expect(stageTimeMinPointSize(null)).toBe(0);
    expect(stageTimeMinPointSize(undefined)).toBe(0);
  });

  it("is empty only when every stage has no samples", () => {
    expect(isTimeEmpty(stageTimeRows(times))).toBe(false);
    expect(
      isTimeEmpty(
        stageTimeRows({
          stages: STAGES.map((stage) => ({
            stage,
            averageHours: null,
            samples: 0,
          })),
        }),
      ),
    ).toBe(true);
  });
});

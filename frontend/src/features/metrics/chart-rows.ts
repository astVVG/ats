import {
  formatCount,
  formatHours,
  formatNumber,
  formatPercent,
} from "@/features/metrics/format";
import type { Funnel, TimeInStage } from "@/features/metrics/types";
import { STAGE_LABELS, STAGES, type Stage } from "@/shared/lib/stages";

/** Bar colors: the stage tokens, so a theme change applies without reloading. */
export const STAGE_COLORS: Record<Stage, string> = {
  APPLIED: "var(--stage-applied)",
  INTERVIEW: "var(--stage-interview)",
  OFFER: "var(--stage-offer)",
  HIRED: "var(--stage-hired)",
  REJECTED: "var(--stage-rejected)",
};

/** Axes, grid, text and tooltip colors, all theme tokens. */
export const CHART_COLORS = {
  grid: "var(--border)",
  axis: "var(--border)",
  text: "var(--muted-foreground)",
  strongText: "var(--foreground)",
  cursor: "var(--muted)",
} as const;

/** REJECTED is not the step after HIRED: it is drawn apart from the sequence. */
export const OUT_OF_SEQUENCE_OPACITY = 0.35;
export const OUT_OF_SEQUENCE_DASH = "4 3";

export type FunnelRow = {
  stage: Stage;
  label: string;
  count: number;
  conversion: number | null;
  countText: string;
  conversionText: string;
  color: string;
  inSequence: boolean;
};

export type StageTimeRow = {
  stage: Stage;
  label: string;
  /** null when the stage has no finished stays; never plotted as 0. */
  averageHours: number | null;
  samples: number;
  hoursText: string;
  samplesText: string;
  color: string;
};

/** The five stages in board order, whatever order the response uses. */
export function funnelRows(funnel: Funnel): FunnelRow[] {
  return STAGES.map((stage) => {
    const entry = funnel.stages.find((s) => s.stage === stage);
    const count = entry?.count ?? 0;
    const conversion = entry?.conversionToNext ?? null;
    return {
      stage,
      label: STAGE_LABELS[stage],
      count,
      conversion,
      countText: formatNumber(count),
      conversionText: formatPercent(conversion),
      color: STAGE_COLORS[stage],
      inSequence: stage !== "REJECTED",
    };
  });
}

export function stageTimeRows(times: TimeInStage): StageTimeRow[] {
  return STAGES.map((stage) => {
    const entry = times.stages.find((s) => s.stage === stage);
    const samples = entry?.samples ?? 0;
    const averageHours = samples > 0 ? (entry?.averageHours ?? null) : null;
    return {
      stage,
      label: STAGE_LABELS[stage],
      averageHours,
      samples,
      hoursText: formatHours(averageHours),
      samplesText: formatCount(samples, "muestra", "muestras"),
      color: STAGE_COLORS[stage],
    };
  });
}

export function isFunnelEmpty(rows: readonly FunnelRow[]) {
  return rows.every((row) => row.count === 0);
}

export function hasConversionOverFull(rows: readonly FunnelRow[]) {
  return rows.some((row) => row.conversion !== null && row.conversion > 1);
}

export function isTimeEmpty(rows: readonly StageTimeRow[]) {
  return rows.every((row) => row.samples === 0);
}

/** Visible bar for any real average, even a tiny one; none without data. */
export function stageTimeMinPointSize(value: number | null | undefined) {
  return value === null || value === undefined ? 0 : 3;
}

/** Height of one bar row in the horizontal bar charts. */
export const CHART_ROW_HEIGHT = 44;

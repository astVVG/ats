import type { ReactNode } from "react";
import type { YAxisTickContentProps } from "recharts";
import { CHART_COLORS } from "@/features/metrics/chart-rows";
import type { Stage } from "@/shared/lib/stages";
import { STAGE_LABELS } from "@/shared/lib/stages";

function tickPosition(props: YAxisTickContentProps) {
  return { x: Number(props.x), y: Number(props.y) };
}

/** Stage name on the left axis, so long labels fit at 375 px. */
export function StageTick(props: YAxisTickContentProps) {
  const { x, y } = tickPosition(props);
  return (
    <text
      x={x}
      y={y}
      dx={-6}
      textAnchor="end"
      dominantBaseline="middle"
      fill={CHART_COLORS.text}
      fontSize={12}
    >
      {STAGE_LABELS[props.payload.value as Stage]}
    </text>
  );
}

type ValueTickProps = YAxisTickContentProps & {
  /** Main and secondary text for each stage, e.g. the count and the conversion. */
  lines: (stage: Stage) => [string, string];
};

/**
 * Values on the right axis. Drawn for every stage, also when its bar is
 * missing, so "Sin datos" is always visible next to the stage.
 */
export function ValueTick({ lines, ...props }: ValueTickProps) {
  const { x, y } = tickPosition(props);
  const [main, secondary] = lines(props.payload.value as Stage);
  return (
    <text x={x} y={y} dx={6} textAnchor="start" fontSize={12}>
      <tspan
        x={x}
        dx={6}
        dy={-2}
        fill={CHART_COLORS.strongText}
        fontWeight={600}
      >
        {main}
      </tspan>
      <tspan x={x} dx={6} dy={14} fill={CHART_COLORS.text} fontSize={11}>
        {secondary}
      </tspan>
    </text>
  );
}

type ChartTooltipProps<T> = {
  // Filled in by the Recharts Tooltip.
  active?: boolean;
  label?: string | number;
  rows: readonly T[];
  stageOf: (row: T) => Stage;
  render: (row: T) => ReactNode;
};

/** Tooltip drawn with theme tokens instead of the Recharts defaults. */
export function ChartTooltip<T>({
  active,
  label,
  rows,
  stageOf,
  render,
}: ChartTooltipProps<T>) {
  const row = rows.find((r) => stageOf(r) === label);
  if (!active || !row) {
    return null;
  }
  return (
    <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      <p className="font-medium">{STAGE_LABELS[stageOf(row)]}</p>
      {render(row)}
    </div>
  );
}

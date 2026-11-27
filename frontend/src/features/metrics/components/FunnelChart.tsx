import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  CHART_COLORS,
  CHART_ROW_HEIGHT,
  hasConversionOverFull,
  OUT_OF_SEQUENCE_DASH,
  OUT_OF_SEQUENCE_OPACITY,
  type FunnelRow,
} from "@/features/metrics/chart-rows";
import {
  ChartTooltip,
  StageTick,
  ValueTick,
} from "@/features/metrics/components/ChartParts";
import { formatNumber } from "@/features/metrics/format";
import type { Stage } from "@/shared/lib/stages";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table";

function conversionLine(row: FunnelRow) {
  return row.inSequence ? `→ ${row.conversionText}` : "fuera de la secuencia";
}

/** Count per stage and conversion to the next one, with the same data as a table. */
export function FunnelChart({ rows }: { rows: FunnelRow[] }) {
  const byStage = new Map(rows.map((row) => [row.stage, row]));
  const lines = (stage: Stage): [string, string] => {
    const row = byStage.get(stage);
    return row ? [row.countText, conversionLine(row)] : ["", ""];
  };

  return (
    <>
      {/* The table below carries the same data for assistive technologies. */}
      <div
        aria-hidden="true"
        style={{ height: rows.length * CHART_ROW_HEIGHT + 32 }}
      >
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={rows}
            layout="vertical"
            margin={{ top: 4, right: 4, bottom: 4, left: 4 }}
            accessibilityLayer={false}
          >
            <CartesianGrid
              horizontal={false}
              stroke={CHART_COLORS.grid}
              strokeDasharray="3 3"
            />
            <XAxis
              type="number"
              allowDecimals={false}
              stroke={CHART_COLORS.axis}
              tick={{ fill: CHART_COLORS.text, fontSize: 12 }}
              tickFormatter={(value: number) => formatNumber(value)}
            />
            <YAxis
              yAxisId="stage"
              type="category"
              dataKey="stage"
              width={84}
              interval={0}
              stroke={CHART_COLORS.axis}
              tickLine={false}
              tick={(props) => <StageTick {...props} />}
            />
            <YAxis
              yAxisId="values"
              orientation="right"
              type="category"
              dataKey="stage"
              width={132}
              interval={0}
              axisLine={false}
              tickLine={false}
              tick={(props) => <ValueTick {...props} lines={lines} />}
            />
            <Tooltip
              cursor={{ fill: CHART_COLORS.cursor, fillOpacity: 0.6 }}
              content={(props) => (
                <ChartTooltip
                  {...props}
                  rows={rows}
                  stageOf={(row) => row.stage}
                  render={(row) => (
                    <p>
                      {row.countText} · {conversionLine(row)}
                    </p>
                  )}
                />
              )}
            />
            <Bar yAxisId="stage" dataKey="count" radius={4} maxBarSize={28}>
              {rows.map((row) => (
                <Cell
                  key={row.stage}
                  fill={row.color}
                  fillOpacity={row.inSequence ? 1 : OUT_OF_SEQUENCE_OPACITY}
                  stroke={row.inSequence ? undefined : row.color}
                  strokeDasharray={
                    row.inSequence ? undefined : OUT_OF_SEQUENCE_DASH
                  }
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <p className="text-xs text-muted-foreground">
        Rechazado no es el paso que sigue a Contratado: reúne las postulaciones
        descartadas en cualquier etapa y se dibuja aparte, con borde punteado.
      </p>
      {hasConversionOverFull(rows) && (
        <p className="text-xs text-muted-foreground">
          Una conversión mayor a 100 % puede deberse a postulaciones que se
          saltaron etapas.
        </p>
      )}

      <Table>
        <TableCaption className="sr-only">
          Embudo: postulaciones por etapa y conversión a la siguiente
        </TableCaption>
        <TableHeader>
          <TableRow>
            <TableHead scope="col">Etapa</TableHead>
            <TableHead scope="col" className="text-right">
              Postulaciones
            </TableHead>
            <TableHead scope="col" className="text-right whitespace-normal">
              Conversión a la siguiente
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.stage} data-stage={row.stage}>
              <TableHead scope="row" className="font-normal">
                {row.label}
              </TableHead>
              <TableCell className="text-right tabular-nums">
                {row.countText}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {row.conversionText}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  );
}

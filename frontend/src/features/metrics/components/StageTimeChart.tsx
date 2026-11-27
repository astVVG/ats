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
  stageTimeMinPointSize,
  type StageTimeRow,
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

/** Average hours per stage and how many stays they come from, plus a table. */
export function StageTimeChart({ rows }: { rows: StageTimeRow[] }) {
  const byStage = new Map(rows.map((row) => [row.stage, row]));
  const lines = (stage: Stage): [string, string] => {
    const row = byStage.get(stage);
    if (!row) {
      return ["", ""];
    }
    return row.averageHours === null
      ? [row.hoursText, ""]
      : [row.hoursText, row.samplesText];
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
              width={104}
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
                      {row.averageHours === null
                        ? row.hoursText
                        : `${row.hoursText} · ${row.samplesText}`}
                    </p>
                  )}
                />
              )}
            />
            <Bar
              yAxisId="stage"
              dataKey="averageHours"
              radius={4}
              maxBarSize={28}
              minPointSize={stageTimeMinPointSize}
            >
              {rows.map((row) => (
                <Cell key={row.stage} fill={row.color} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <p className="text-xs text-muted-foreground">
        Solo cuentan las estancias terminadas: el tiempo que una postulación
        lleva en su etapa actual no se incluye.
      </p>

      <Table>
        <TableCaption className="sr-only">
          Tiempo promedio por etapa, en horas, y número de muestras
        </TableCaption>
        <TableHeader>
          <TableRow>
            <TableHead scope="col">Etapa</TableHead>
            <TableHead scope="col" className="text-right">
              Horas promedio
            </TableHead>
            <TableHead scope="col" className="text-right">
              Muestras
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
                {row.hoursText}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {formatNumber(row.samples)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  );
}

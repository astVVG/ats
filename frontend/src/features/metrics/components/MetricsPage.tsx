import { useState } from "react";
import {
  funnelRows,
  isFunnelEmpty,
  isTimeEmpty,
  stageTimeRows,
} from "@/features/metrics/chart-rows";
import { FunnelChart } from "@/features/metrics/components/FunnelChart";
import { MetricSection } from "@/features/metrics/components/MetricSection";
import { MetricsFilters } from "@/features/metrics/components/MetricsFilters";
import { StageTimeChart } from "@/features/metrics/components/StageTimeChart";
import { formatCount, formatDays } from "@/features/metrics/format";
import {
  useFunnel,
  useTimeInStage,
  useTimeToHire,
} from "@/features/metrics/hooks/useMetrics";
import {
  EMPTY_FILTERS,
  toMetricsFilters,
  type TimeToHire,
} from "@/features/metrics/types";

function hasHires(data: TimeToHire) {
  return (
    data.hiredCount > 0 &&
    data.averageDays !== null &&
    data.averageDays !== undefined
  );
}

export function MetricsPage() {
  const [values, setValues] = useState(EMPTY_FILTERS);
  // null while the range is inverted: no request goes out.
  const filters = toMetricsFilters(values);
  const blocked = filters === null;
  // Independent queries: one failing does not hold back the others.
  const timeToHire = useTimeToHire(filters);
  const funnel = useFunnel(filters);
  const timeInStage = useTimeInStage(filters);

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-bold">Métricas</h1>
        <p className="text-muted-foreground">
          Embudo de contratación, tiempo por etapa y tiempo de contratación.
        </p>
      </div>

      <MetricsFilters values={values} onChange={setValues} />

      <div className="grid gap-6 lg:grid-cols-2">
        <MetricSection
          id="metrics-time-to-hire"
          title="Tiempo de contratación"
          description="Días promedio desde que se crea la postulación hasta su primera llegada a Contratado."
          what="el tiempo de contratación"
          query={timeToHire}
          blocked={blocked}
          isEmpty={(data) => !hasHires(data)}
          emptyMessage="No hay contrataciones con estos filtros."
          className="lg:col-span-2"
        >
          {(data) => (
            <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
              <p className="text-4xl font-bold tabular-nums">
                {formatDays(data.averageDays ?? 0)}
              </p>
              <p className="text-muted-foreground">
                {formatCount(data.hiredCount, "contratado", "contratados")}
              </p>
            </div>
          )}
        </MetricSection>

        <MetricSection
          id="metrics-funnel"
          title="Embudo"
          description="Postulaciones que alguna vez llegaron a cada etapa y conversión a la siguiente."
          what="el embudo"
          query={funnel}
          blocked={blocked}
          isEmpty={(data) => isFunnelEmpty(funnelRows(data))}
          emptyMessage="No hay postulaciones con estos filtros."
        >
          {(data) => <FunnelChart rows={funnelRows(data)} />}
        </MetricSection>

        <MetricSection
          id="metrics-time-in-stage"
          title="Tiempo por etapa"
          description="Horas promedio que una postulación pasa en cada etapa."
          what="el tiempo por etapa"
          query={timeInStage}
          blocked={blocked}
          isEmpty={(data) => isTimeEmpty(stageTimeRows(data))}
          emptyMessage="Todavía no hay estancias terminadas con estos filtros."
        >
          {(data) => <StageTimeChart rows={stageTimeRows(data)} />}
        </MetricSection>
      </div>
    </section>
  );
}

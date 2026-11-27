import { useJobs } from "@/features/jobs/hooks/useJobs";
import { JOB_STATUS_LABELS } from "@/features/jobs/types";
import {
  ALL_JOBS,
  EMPTY_FILTERS,
  isRangeInverted,
  MAX_JOB_OPTIONS,
  type MetricsFilterValues,
} from "@/features/metrics/types";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";

export const INVERTED_RANGE_MESSAGE =
  "La fecha Desde no puede ser posterior a la fecha Hasta.";

type MetricsFiltersProps = {
  values: MetricsFilterValues;
  onChange: (values: MetricsFilterValues) => void;
};

function JobFilter({ values, onChange }: MetricsFiltersProps) {
  // The most recent jobs, closed ones included.
  const jobs = useJobs({ page: 0, size: MAX_JOB_OPTIONS });
  const options = jobs.data?.content ?? [];
  const total = jobs.data?.totalElements ?? 0;

  return (
    <div className="min-w-0 space-y-2 sm:col-span-2 lg:col-span-1">
      <Label htmlFor="metrics-job">Vacante</Label>
      <Select
        value={values.job}
        onValueChange={(job) => onChange({ ...values, job })}
      >
        <SelectTrigger id="metrics-job" className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL_JOBS}>Todas las vacantes</SelectItem>
          {options.map((job) => (
            <SelectItem key={job.id} value={String(job.id)}>
              {job.status === "CLOSED"
                ? `${job.title} (${JOB_STATUS_LABELS.CLOSED})`
                : job.title}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {jobs.isPending && (
        <p role="status" className="text-xs text-muted-foreground">
          Cargando vacantes…
        </p>
      )}
      {total > options.length && (
        <p className="text-xs text-muted-foreground">
          Se muestran las {options.length} vacantes más recientes de {total}.
        </p>
      )}
      {jobs.isError && (
        <Alert variant="destructive">
          <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
            <span>No se pudieron cargar las vacantes.</span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={jobs.isFetching}
              onClick={() => void jobs.refetch()}
            >
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}
    </div>
  );
}

export function MetricsFilters({ values, onChange }: MetricsFiltersProps) {
  const inverted = isRangeInverted(values);
  const empty =
    values.job === EMPTY_FILTERS.job &&
    values.from === EMPTY_FILTERS.from &&
    values.to === EMPTY_FILTERS.to;

  return (
    <form
      aria-label="Filtros de métricas"
      className="space-y-3 rounded-xl border bg-card p-4 text-card-foreground"
      onSubmit={(event) => event.preventDefault()}
    >
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_auto] lg:items-start">
        <JobFilter values={values} onChange={onChange} />
        <div className="min-w-0 space-y-2">
          <Label htmlFor="metrics-from">Desde</Label>
          <Input
            id="metrics-from"
            type="date"
            value={values.from}
            max={values.to || undefined}
            aria-invalid={inverted || undefined}
            aria-describedby={cnIds(
              "metrics-dates-hint",
              inverted && "metrics-from-error",
            )}
            onChange={(event) =>
              onChange({ ...values, from: event.target.value })
            }
          />
          {inverted && (
            <p id="metrics-from-error" className="text-sm text-destructive">
              {INVERTED_RANGE_MESSAGE}
            </p>
          )}
        </div>
        <div className="min-w-0 space-y-2">
          <Label htmlFor="metrics-to">Hasta</Label>
          <Input
            id="metrics-to"
            type="date"
            value={values.to}
            min={values.from || undefined}
            aria-describedby="metrics-dates-hint"
            onChange={(event) =>
              onChange({ ...values, to: event.target.value })
            }
          />
        </div>
        <div className="sm:col-span-2 lg:col-span-1 lg:pt-6">
          <Button
            type="button"
            variant="outline"
            className="w-full lg:w-auto"
            disabled={empty}
            onClick={() => onChange(EMPTY_FILTERS)}
          >
            Limpiar filtros
          </Button>
        </div>
      </div>
      <p id="metrics-dates-hint" className="text-xs text-muted-foreground">
        Desde y Hasta filtran por la fecha de creación de la postulación y ambas
        fechas se incluyen.
      </p>
    </form>
  );
}

function cnIds(...ids: (string | false)[]) {
  return ids.filter(Boolean).join(" ");
}

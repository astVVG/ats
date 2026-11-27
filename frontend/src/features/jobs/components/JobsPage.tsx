import { useState } from "react";
import { Link } from "react-router";
import { CloseJobDialog } from "@/features/jobs/components/CloseJobDialog";
import { useJobs } from "@/features/jobs/hooks/useJobs";
import {
  JOB_STATUS_LABELS,
  type Job,
  type JobStatus,
} from "@/features/jobs/types";
import { Pagination } from "@/shared/components/Pagination";
import { useDebouncedValue } from "@/shared/hooks/useDebouncedValue";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Badge } from "@/shared/ui/badge";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table";

const PAGE_SIZE = 20;

type StatusFilter = "ALL" | JobStatus;

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "Todas" },
  { value: "OPEN", label: "Abiertas" },
  { value: "CLOSED", label: "Cerradas" },
];

function isStatusFilter(value: string): value is StatusFilter {
  return STATUS_FILTERS.some((option) => option.value === value);
}

const dateFormat = new Intl.DateTimeFormat("es-MX", { dateStyle: "medium" });

function JobStatusBadge({ status }: { status: JobStatus }) {
  return (
    <Badge variant={status === "OPEN" ? "default" : "outline"}>
      {JOB_STATUS_LABELS[status]}
    </Badge>
  );
}

export function JobsPage() {
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [search, setSearch] = useState("");
  const q = useDebouncedValue(search.trim());
  // The page belongs to the filters it was chosen with, so new filters start at page 0.
  const filters = `${status}|${q}`;
  const [paging, setPaging] = useState({ filters, page: 0 });
  const page = paging.filters === filters ? paging.page : 0;
  const [jobToClose, setJobToClose] = useState<Job | null>(null);

  const { data, isPending, isError, isFetching, refetch } = useJobs({
    page,
    size: PAGE_SIZE,
    status: status === "ALL" ? undefined : status,
    q: q === "" ? undefined : q,
  });

  const hasFilters = status !== "ALL" || q !== "";

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-3xl font-bold">Vacantes</h1>
        <Button asChild>
          <Link to="/jobs/new">Nueva vacante</Link>
        </Button>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="space-y-2">
          <Label htmlFor="jobs-status">Estado</Label>
          <Select
            value={status}
            onValueChange={(value) => {
              if (isStatusFilter(value)) {
                setStatus(value);
              }
            }}
          >
            <SelectTrigger id="jobs-status" className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATUS_FILTERS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="min-w-64 flex-1 space-y-2">
          <Label htmlFor="jobs-search">Buscar</Label>
          <Input
            id="jobs-search"
            type="search"
            placeholder="Título o departamento"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
      </div>

      {isPending ? (
        <p role="status" className="text-muted-foreground">
          Cargando vacantes…
        </p>
      ) : isError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between gap-4">
            <span>No se pudieron cargar las vacantes.</span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={isFetching}
              onClick={() => void refetch()}
            >
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      ) : data.content.length === 0 ? (
        <p className="text-muted-foreground">
          {hasFilters
            ? "No hay vacantes que coincidan con los filtros."
            : "Aún no hay vacantes. Crea la primera."}
        </p>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Título</TableHead>
                <TableHead>Departamento</TableHead>
                <TableHead>Ubicación</TableHead>
                <TableHead>Estado</TableHead>
                <TableHead>Creada</TableHead>
                <TableHead className="text-right">Acciones</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((job) => (
                <TableRow key={job.id}>
                  <TableCell className="font-medium">{job.title}</TableCell>
                  <TableCell>{job.department ?? "—"}</TableCell>
                  <TableCell>{job.location ?? "—"}</TableCell>
                  <TableCell>
                    <JobStatusBadge status={job.status} />
                  </TableCell>
                  <TableCell>
                    {dateFormat.format(new Date(job.createdAt))}
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-2">
                      <Button asChild variant="outline" size="sm">
                        <Link to={`/jobs/${job.id}/board`}>Ver tablero</Link>
                      </Button>
                      <Button asChild variant="outline" size="sm">
                        <Link to={`/jobs/${job.id}/edit`}>Editar</Link>
                      </Button>
                      {job.status === "OPEN" && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => setJobToClose(job)}
                        >
                          Cerrar
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination
            page={page}
            totalPages={data.totalPages}
            onPageChange={(next) => setPaging({ filters, page: next })}
          />
        </>
      )}

      <CloseJobDialog job={jobToClose} onDone={() => setJobToClose(null)} />
    </section>
  );
}

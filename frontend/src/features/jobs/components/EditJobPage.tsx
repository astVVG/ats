import { isAxiosError } from "axios";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { JobForm } from "@/features/jobs/components/JobForm";
import { useJob } from "@/features/jobs/hooks/useJob";
import { useUpdateJob } from "@/features/jobs/hooks/useUpdateJob";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";

function parseId(value: string | undefined) {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function JobNotFound() {
  return (
    <section className="space-y-4">
      <h1 className="text-3xl font-bold">Vacante no encontrada</h1>
      <p className="text-muted-foreground">La vacante que buscas no existe.</p>
      <Button asChild variant="outline">
        <Link to="/jobs">Volver a vacantes</Link>
      </Button>
    </section>
  );
}

function EditJob({ id }: { id: number }) {
  const navigate = useNavigate();
  const job = useJob(id);
  const updateJob = useUpdateJob(id);

  if (job.isPending) {
    return (
      <p role="status" className="text-muted-foreground">
        Cargando vacante…
      </p>
    );
  }

  if (job.isError) {
    if (isAxiosError(job.error) && job.error.response?.status === 404) {
      return <JobNotFound />;
    }
    return (
      <Alert variant="destructive">
        <AlertDescription className="flex items-center justify-between gap-4">
          <span>No se pudo cargar la vacante.</span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={job.isFetching}
            onClick={() => void job.refetch()}
          >
            Reintentar
          </Button>
        </AlertDescription>
      </Alert>
    );
  }

  const { title, department, location, description } = job.data;

  return (
    <section className="space-y-6">
      <h1 className="text-3xl font-bold">Editar vacante</h1>
      <JobForm
        defaultValues={{
          title,
          department: department ?? "",
          location: location ?? "",
          description: description ?? "",
        }}
        submitLabel="Guardar cambios"
        pendingLabel="Guardando…"
        onSubmit={async (request) => {
          await updateJob.mutateAsync(request);
          toast.success("Vacante actualizada");
          await navigate("/jobs");
        }}
      />
    </section>
  );
}

export function EditJobPage() {
  const id = parseId(useParams().id);
  return id === null ? <JobNotFound /> : <EditJob key={id} id={id} />;
}

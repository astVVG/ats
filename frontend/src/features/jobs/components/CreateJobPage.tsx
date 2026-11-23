import { useNavigate } from "react-router";
import { toast } from "sonner";
import { JobForm } from "@/features/jobs/components/JobForm";
import { useCreateJob } from "@/features/jobs/hooks/useCreateJob";

export function CreateJobPage() {
  const navigate = useNavigate();
  const createJob = useCreateJob();

  return (
    <section className="space-y-6">
      <h1 className="text-3xl font-bold">Nueva vacante</h1>
      <JobForm
        submitLabel="Crear vacante"
        pendingLabel="Creando…"
        onSubmit={async (request) => {
          await createJob.mutateAsync(request);
          toast.success("Vacante creada");
          await navigate("/jobs");
        }}
      />
    </section>
  );
}

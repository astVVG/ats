import { isAxiosError } from "axios";
import { useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { CandidateForm } from "@/features/candidates/components/CandidateForm";
import { CandidateNotFound } from "@/features/candidates/components/CandidateNotFound";
import { useCandidate } from "@/features/candidates/hooks/useCandidate";
import { useUpdateCandidate } from "@/features/candidates/hooks/useUpdateCandidate";
import { parseCandidateId } from "@/features/candidates/types";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";

function EditCandidate({ id }: { id: number }) {
  const navigate = useNavigate();
  const candidate = useCandidate(id);
  const updateCandidate = useUpdateCandidate(id);

  if (candidate.isPending) {
    return (
      <p role="status" className="text-muted-foreground">
        Cargando candidato…
      </p>
    );
  }

  if (candidate.isError) {
    if (
      isAxiosError(candidate.error) &&
      candidate.error.response?.status === 404
    ) {
      return <CandidateNotFound />;
    }
    return (
      <Alert variant="destructive">
        <AlertDescription className="flex items-center justify-between gap-4">
          <span>No se pudo cargar el candidato.</span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={candidate.isFetching}
            onClick={() => void candidate.refetch()}
          >
            Reintentar
          </Button>
        </AlertDescription>
      </Alert>
    );
  }

  const { fullName, email, phone, source } = candidate.data;

  return (
    <section className="space-y-6">
      <h1 className="text-3xl font-bold">Editar candidato</h1>
      <CandidateForm
        defaultValues={{
          fullName,
          email,
          phone: phone ?? "",
          source: source ?? "",
        }}
        submitLabel="Guardar cambios"
        pendingLabel="Guardando…"
        cancelTo={`/candidates/${id}`}
        onSubmit={async (request) => {
          await updateCandidate.mutateAsync(request);
          toast.success("Candidato actualizado");
          await navigate(`/candidates/${id}`);
        }}
      />
    </section>
  );
}

export function EditCandidatePage() {
  const id = parseCandidateId(useParams().id);
  return id === null ? (
    <CandidateNotFound />
  ) : (
    <EditCandidate key={id} id={id} />
  );
}

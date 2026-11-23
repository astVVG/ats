import { useNavigate } from "react-router";
import { toast } from "sonner";
import { CandidateForm } from "@/features/candidates/components/CandidateForm";
import { useCreateCandidate } from "@/features/candidates/hooks/useCreateCandidate";

export function CreateCandidatePage() {
  const navigate = useNavigate();
  const createCandidate = useCreateCandidate();

  return (
    <section className="space-y-6">
      <h1 className="text-3xl font-bold">Nuevo candidato</h1>
      <CandidateForm
        submitLabel="Crear candidato"
        pendingLabel="Creando…"
        cancelTo="/candidates"
        onSubmit={async (request) => {
          const created = await createCandidate.mutateAsync(request);
          toast.success("Candidato creado");
          await navigate(`/candidates/${created.id}`);
        }}
      />
    </section>
  );
}

import { useState } from "react";
import { toast } from "sonner";
import { CandidatePicker } from "@/features/board/components/CandidatePicker";
import {
  QuickCandidateForm,
  type QuickCandidateValues,
} from "@/features/board/components/QuickCandidateForm";
import { useCreateApplication } from "@/features/board/hooks/useCreateApplication";
import { createApplicationErrorMessage } from "@/features/board/messages";
import { useCreateCandidate } from "@/features/candidates/hooks/useCreateCandidate";
import type { Candidate } from "@/features/candidates/types";
import { STAGE_LABELS } from "@/shared/lib/stages";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/shared/ui/dialog";

type Mode = "search" | "create";

const MODES: { value: Mode; label: string }[] = [
  { value: "search", label: "Buscar candidato" },
  { value: "create", label: "Crear candidato nuevo" },
];

type AddApplicationDialogProps = {
  jobId: number;
  /** The job is closed and takes no applications. */
  closed: boolean;
};

export function AddApplicationDialog({
  jobId,
  closed,
}: AddApplicationDialogProps) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<Mode>("search");
  // Kept so a retry after a failed application does not create the candidate again.
  const [created, setCreated] = useState<Candidate | null>(null);
  const [error, setError] = useState<string | null>(null);
  const createApplication = useCreateApplication(jobId);
  const createCandidate = useCreateCandidate();
  const pending = createApplication.isPending || createCandidate.isPending;

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (!next) {
      setMode("search");
      setCreated(null);
      setError(null);
    }
  };

  async function apply(candidateId: number) {
    setError(null);
    try {
      await createApplication.mutateAsync(candidateId);
    } catch (failure) {
      setError(createApplicationErrorMessage(failure));
      return;
    }
    toast.success(`Candidato agregado a ${STAGE_LABELS.APPLIED}.`);
    handleOpenChange(false);
  }

  async function createAndApply(values: QuickCandidateValues) {
    setError(null);
    let candidate = created;
    if (candidate === null) {
      // Errors here belong to the form fields, so they are not caught.
      candidate = await createCandidate.mutateAsync(values);
      setCreated(candidate);
    }
    await apply(candidate.id);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button
          type="button"
          disabled={closed}
          title={
            closed
              ? "La vacante está cerrada y no admite nuevas postulaciones."
              : undefined
          }
        >
          Agregar candidato
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Agregar candidato</DialogTitle>
          <DialogDescription>
            Busca un candidato existente o crea uno nuevo. Se agregará al final
            de {STAGE_LABELS.APPLIED}.
          </DialogDescription>
        </DialogHeader>

        <div
          role="group"
          aria-label="Modo"
          className="grid grid-cols-2 gap-1 rounded-lg border bg-muted/40 p-1"
        >
          {MODES.map((option) => (
            <Button
              key={option.value}
              type="button"
              size="sm"
              variant={mode === option.value ? "secondary" : "ghost"}
              aria-pressed={mode === option.value}
              disabled={pending}
              onClick={() => {
                setMode(option.value);
                setError(null);
              }}
            >
              {option.label}
            </Button>
          ))}
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {mode === "search" ? (
          <CandidatePicker
            disabled={pending}
            onPick={(candidate) => void apply(candidate.id)}
          />
        ) : (
          <QuickCandidateForm
            created={created}
            pending={pending}
            onSubmit={createAndApply}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

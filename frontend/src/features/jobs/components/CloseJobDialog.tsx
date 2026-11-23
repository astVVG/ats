import { useState } from "react";
import { toast } from "sonner";
import { useCloseJob } from "@/features/jobs/hooks/useCloseJob";
import type { Job } from "@/features/jobs/types";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/shared/ui/dialog";

type CloseJobDialogProps = {
  /** The job to close; the dialog is open while it is set. */
  job: Job | null;
  onDone: () => void;
};

export function CloseJobDialog({ job, onDone }: CloseJobDialogProps) {
  const closeJob = useCloseJob();
  const [error, setError] = useState<string | null>(null);

  const dismiss = () => {
    if (closeJob.isPending) {
      return;
    }
    setError(null);
    onDone();
  };

  const confirm = async () => {
    if (!job) {
      return;
    }
    setError(null);
    try {
      await closeJob.mutateAsync(job.id);
      toast.success("Vacante cerrada");
      onDone();
    } catch {
      setError("No se pudo cerrar la vacante. Inténtalo de nuevo.");
    }
  };

  return (
    <Dialog open={job !== null} onOpenChange={(open) => !open && dismiss()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cerrar vacante</DialogTitle>
          <DialogDescription>
            Una vacante cerrada no admite postulaciones nuevas.
          </DialogDescription>
        </DialogHeader>
        {job && (
          <p className="text-sm">
            ¿Quieres cerrar la vacante <strong>{job.title}</strong>?
          </p>
        )}
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={dismiss}>
            Cancelar
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={closeJob.isPending}
            onClick={() => void confirm()}
          >
            {closeJob.isPending ? "Cerrando…" : "Cerrar vacante"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

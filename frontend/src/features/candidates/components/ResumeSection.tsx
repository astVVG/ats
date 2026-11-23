import { isAxiosError } from "axios";
import { useRef, useState, type ChangeEvent } from "react";
import { toast } from "sonner";
import { useDownloadResume } from "@/features/candidates/hooks/useDownloadResume";
import { useUploadResume } from "@/features/candidates/hooks/useUploadResume";
import { MAX_RESUME_BYTES, type Candidate } from "@/features/candidates/types";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";

/** Checked before uploading; the backend checks the content again. */
function validateResume(file: File): string | null {
  const isPdf =
    file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
  if (!isPdf) {
    return "El archivo debe ser un PDF.";
  }
  if (file.size === 0) {
    return "El archivo está vacío.";
  }
  if (file.size > MAX_RESUME_BYTES) {
    return "El archivo supera el tamaño máximo de 5 MB.";
  }
  return null;
}

function httpStatus(error: unknown) {
  return isAxiosError(error) ? error.response?.status : undefined;
}

export function ResumeSection({ candidate }: { candidate: Candidate }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const upload = useUploadResume(candidate.id);
  const download = useDownloadResume(candidate.id);

  const clearInput = () => {
    setFile(null);
    if (inputRef.current) {
      inputRef.current.value = "";
    }
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0] ?? null;
    setUploadError(null);
    if (!selected) {
      setFile(null);
      return;
    }
    const problem = validateResume(selected);
    if (problem) {
      setUploadError(problem);
      clearInput();
      return;
    }
    setFile(selected);
  };

  const handleUpload = async () => {
    if (!file) {
      return;
    }
    setUploadError(null);
    try {
      await upload.mutateAsync(file);
      toast.success("CV subido");
      clearInput();
    } catch (error) {
      const status = httpStatus(error);
      setUploadError(
        status === 400
          ? "No se pudo subir el CV. Verifica que sea un PDF de máximo 5 MB."
          : status === 404
            ? "No se encontró el candidato."
            : "No se pudo subir el CV. Inténtalo de nuevo.",
      );
    }
  };

  const handleDownload = async () => {
    setDownloadError(null);
    try {
      await download.mutateAsync();
    } catch (error) {
      setDownloadError(
        httpStatus(error) === 404
          ? "Este candidato no tiene un CV disponible."
          : "No se pudo descargar el CV. Inténtalo de nuevo.",
      );
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">
          {candidate.hasResume
            ? "Este candidato tiene un CV cargado."
            : "Este candidato aún no tiene CV."}
        </p>
        {candidate.hasResume && (
          <Button
            type="button"
            variant="outline"
            disabled={download.isPending}
            onClick={() => void handleDownload()}
          >
            {download.isPending ? "Descargando…" : "Descargar CV"}
          </Button>
        )}
      </div>
      {downloadError && (
        <Alert variant="destructive">
          <AlertDescription>{downloadError}</AlertDescription>
        </Alert>
      )}

      <div className="space-y-2">
        <Label htmlFor="resume-file">Archivo PDF (máximo 5 MB)</Label>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            ref={inputRef}
            id="resume-file"
            type="file"
            accept="application/pdf"
            className="max-w-sm"
            disabled={upload.isPending}
            aria-invalid={uploadError ? true : undefined}
            aria-describedby={uploadError ? "resume-file-error" : undefined}
            onChange={handleFileChange}
          />
          <Button
            type="button"
            disabled={!file || upload.isPending}
            onClick={() => void handleUpload()}
          >
            {upload.isPending
              ? "Subiendo…"
              : candidate.hasResume
                ? "Reemplazar CV"
                : "Subir CV"}
          </Button>
        </div>
        {uploadError && (
          <p id="resume-file-error" className="text-sm text-destructive">
            {uploadError}
          </p>
        )}
      </div>

      {upload.progress !== null && (
        <div
          role="progressbar"
          aria-label="Progreso de subida"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={upload.progress}
          className="h-2 w-full max-w-sm overflow-hidden rounded-full bg-muted"
        >
          <div
            className="h-full bg-primary transition-[width]"
            style={{ width: `${upload.progress}%` }}
          />
        </div>
      )}
    </div>
  );
}

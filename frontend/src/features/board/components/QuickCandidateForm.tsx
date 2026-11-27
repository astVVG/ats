import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { httpStatus } from "@/features/board/messages";
import type { Candidate } from "@/features/candidates/types";
import { getProblemDetail } from "@/shared/api/errors";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";

// Same rules as the candidate form, which does not export its schema.
const quickCandidateSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, "Ingresa el nombre")
    .max(200, "El nombre admite como máximo 200 caracteres"),
  email: z
    .string()
    .trim()
    .min(1, "Ingresa el correo")
    .max(254, "El correo admite como máximo 254 caracteres")
    .pipe(z.email("Ingresa un correo válido")),
});

export type QuickCandidateValues = z.infer<typeof quickCandidateSchema>;
type QuickCandidateField = keyof QuickCandidateValues;

const FIELDS: readonly QuickCandidateField[] = ["fullName", "email"];

function isField(field: string): field is QuickCandidateField {
  return (FIELDS as readonly string[]).includes(field);
}

function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) {
    return null;
  }
  return (
    <p id={id} className="text-sm text-destructive">
      {message}
    </p>
  );
}

type QuickCandidateFormProps = {
  /** Candidate created by an earlier attempt whose application failed. */
  created: Candidate | null;
  pending: boolean;
  /** Rejects only when creating the candidate fails. */
  onSubmit: (values: QuickCandidateValues) => Promise<void>;
};

export function QuickCandidateForm({
  created,
  pending,
  onSubmit,
}: QuickCandidateFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<QuickCandidateValues>({
    resolver: zodResolver(quickCandidateSchema),
    defaultValues: { fullName: "", email: "" },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      const problem = getProblemDetail(error);
      const status = httpStatus(error);
      if (status === 409) {
        setError("email", {
          message:
            "Ya existe un candidato con este correo. Búscalo en la pestaña de búsqueda.",
        });
      } else if (status === 400 && problem?.errors?.length) {
        let unmatched = false;
        for (const { field, message } of problem.errors) {
          if (isField(field)) {
            setError(field, { message });
          } else {
            unmatched = true;
          }
        }
        if (unmatched) {
          setFormError("Revisa los datos del formulario.");
        }
      } else {
        setFormError("No se pudo crear el candidato. Inténtalo de nuevo.");
      }
    }
  });

  const fieldProps = (field: QuickCandidateField) => ({
    id: `quick-${field}`,
    readOnly: created !== null,
    "aria-invalid": errors[field] ? true : undefined,
    "aria-describedby": errors[field] ? `quick-${field}-error` : undefined,
  });

  return (
    <form className="space-y-4" onSubmit={submit} noValidate>
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      {created && (
        <Alert>
          <AlertDescription>
            {created.fullName} ya se creó como candidato. Falta agregarlo a la
            vacante.
          </AlertDescription>
        </Alert>
      )}
      <div className="space-y-2">
        <Label htmlFor="quick-fullName">Nombre completo</Label>
        <Input
          autoComplete="off"
          {...fieldProps("fullName")}
          {...register("fullName")}
        />
        <FieldError
          id="quick-fullName-error"
          message={errors.fullName?.message}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="quick-email">Correo</Label>
        <Input
          type="email"
          autoComplete="off"
          {...fieldProps("email")}
          {...register("email")}
        />
        <FieldError id="quick-email-error" message={errors.email?.message} />
      </div>
      <Button type="submit" className="w-full" disabled={pending}>
        {pending
          ? "Agregando…"
          : created
            ? "Agregar a la vacante"
            : "Crear y agregar"}
      </Button>
    </form>
  );
}

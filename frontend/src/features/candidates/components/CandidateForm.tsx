import { zodResolver } from "@hookform/resolvers/zod";
import { isAxiosError } from "axios";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router";
import { z } from "zod";
import type { CandidateRequest } from "@/features/candidates/types";
import { getProblemDetail } from "@/shared/api/errors";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";

const candidateSchema = z.object({
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
  phone: z
    .string()
    .trim()
    .max(30, "El teléfono admite como máximo 30 caracteres"),
  source: z
    .string()
    .trim()
    .max(100, "El origen admite como máximo 100 caracteres"),
});

export type CandidateFormValues = z.infer<typeof candidateSchema>;
type CandidateField = keyof CandidateFormValues;

const FIELDS: readonly CandidateField[] = [
  "fullName",
  "email",
  "phone",
  "source",
];

function isCandidateField(field: string): field is CandidateField {
  return (FIELDS as readonly string[]).includes(field);
}

const EMPTY_CANDIDATE_FORM: CandidateFormValues = {
  fullName: "",
  email: "",
  phone: "",
  source: "",
};

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

type CandidateFormProps = {
  defaultValues?: CandidateFormValues;
  submitLabel: string;
  pendingLabel: string;
  cancelTo: string;
  onSubmit: (request: CandidateRequest) => Promise<void>;
};

export function CandidateForm({
  defaultValues = EMPTY_CANDIDATE_FORM,
  submitLabel,
  pendingLabel,
  cancelTo,
  onSubmit,
}: CandidateFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CandidateFormValues>({
    resolver: zodResolver(candidateSchema),
    defaultValues,
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      const status = isAxiosError(error) ? error.response?.status : undefined;
      const problem = getProblemDetail(error);
      if (status === 409) {
        setError("email", {
          message: "Ya existe un candidato con ese correo",
        });
      } else if (status === 400 && problem?.errors?.length) {
        let unmatched = false;
        for (const { field, message } of problem.errors) {
          if (isCandidateField(field)) {
            setError(field, { message });
          } else {
            unmatched = true;
          }
        }
        if (unmatched) {
          setFormError("Revisa los datos del formulario.");
        }
      } else {
        setFormError("No se pudo guardar el candidato. Inténtalo de nuevo.");
      }
    }
  });

  const fieldProps = (field: CandidateField) => ({
    id: `candidate-${field}`,
    "aria-invalid": errors[field] ? true : undefined,
    "aria-describedby": errors[field] ? `candidate-${field}-error` : undefined,
  });

  return (
    <form className="max-w-2xl space-y-4" onSubmit={submit} noValidate>
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <div className="space-y-2">
        <Label htmlFor="candidate-fullName">Nombre completo</Label>
        <Input
          autoComplete="off"
          {...fieldProps("fullName")}
          {...register("fullName")}
        />
        <FieldError
          id="candidate-fullName-error"
          message={errors.fullName?.message}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="candidate-email">Correo</Label>
        <Input
          type="email"
          autoComplete="off"
          {...fieldProps("email")}
          {...register("email")}
        />
        <FieldError
          id="candidate-email-error"
          message={errors.email?.message}
        />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="candidate-phone">Teléfono</Label>
          <Input
            type="tel"
            autoComplete="off"
            {...fieldProps("phone")}
            {...register("phone")}
          />
          <FieldError
            id="candidate-phone-error"
            message={errors.phone?.message}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-source">Origen</Label>
          <Input
            autoComplete="off"
            placeholder="LinkedIn, referido…"
            {...fieldProps("source")}
            {...register("source")}
          />
          <FieldError
            id="candidate-source-error"
            message={errors.source?.message}
          />
        </div>
      </div>
      <div className="flex gap-2">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? pendingLabel : submitLabel}
        </Button>
        <Button asChild variant="outline">
          <Link to={cancelTo}>Cancelar</Link>
        </Button>
      </div>
    </form>
  );
}

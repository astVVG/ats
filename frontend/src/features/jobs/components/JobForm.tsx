import { zodResolver } from "@hookform/resolvers/zod";
import { isAxiosError } from "axios";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router";
import { z } from "zod";
import type { JobRequest } from "@/features/jobs/types";
import { getProblemDetail } from "@/shared/api/errors";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { Textarea } from "@/shared/ui/textarea";

const jobSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "Ingresa el título")
    .max(200, "El título admite como máximo 200 caracteres"),
  department: z
    .string()
    .trim()
    .max(100, "El departamento admite como máximo 100 caracteres"),
  location: z
    .string()
    .trim()
    .max(100, "La ubicación admite como máximo 100 caracteres"),
  description: z
    .string()
    .trim()
    .max(5000, "La descripción admite como máximo 5000 caracteres"),
});

export type JobFormValues = z.infer<typeof jobSchema>;
type JobField = keyof JobFormValues;

const FIELDS: readonly JobField[] = [
  "title",
  "department",
  "location",
  "description",
];

function isJobField(field: string): field is JobField {
  return (FIELDS as readonly string[]).includes(field);
}

const EMPTY_JOB_FORM: JobFormValues = {
  title: "",
  department: "",
  location: "",
  description: "",
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

type JobFormProps = {
  defaultValues?: JobFormValues;
  submitLabel: string;
  pendingLabel: string;
  onSubmit: (request: JobRequest) => Promise<void>;
};

export function JobForm({
  defaultValues = EMPTY_JOB_FORM,
  submitLabel,
  pendingLabel,
  onSubmit,
}: JobFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<JobFormValues>({
    resolver: zodResolver(jobSchema),
    defaultValues,
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      const status = isAxiosError(error) ? error.response?.status : undefined;
      const problem = getProblemDetail(error);
      if (status === 400 && problem?.errors?.length) {
        let unmatched = false;
        for (const { field, message } of problem.errors) {
          if (isJobField(field)) {
            setError(field, { message });
          } else {
            unmatched = true;
          }
        }
        if (unmatched) {
          setFormError("Revisa los datos del formulario.");
        }
      } else {
        setFormError("No se pudo guardar la vacante. Inténtalo de nuevo.");
      }
    }
  });

  const fieldProps = (field: JobField) => ({
    id: `job-${field}`,
    "aria-invalid": errors[field] ? true : undefined,
    "aria-describedby": errors[field] ? `job-${field}-error` : undefined,
  });

  return (
    <form className="max-w-2xl space-y-4" onSubmit={submit} noValidate>
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <div className="space-y-2">
        <Label htmlFor="job-title">Título</Label>
        <Input
          autoComplete="off"
          {...fieldProps("title")}
          {...register("title")}
        />
        <FieldError id="job-title-error" message={errors.title?.message} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="job-department">Departamento</Label>
          <Input
            autoComplete="off"
            {...fieldProps("department")}
            {...register("department")}
          />
          <FieldError
            id="job-department-error"
            message={errors.department?.message}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="job-location">Ubicación</Label>
          <Input
            autoComplete="off"
            {...fieldProps("location")}
            {...register("location")}
          />
          <FieldError
            id="job-location-error"
            message={errors.location?.message}
          />
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="job-description">Descripción</Label>
        <Textarea
          rows={6}
          {...fieldProps("description")}
          {...register("description")}
        />
        <FieldError
          id="job-description-error"
          message={errors.description?.message}
        />
      </div>
      <div className="flex gap-2">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? pendingLabel : submitLabel}
        </Button>
        <Button asChild variant="outline">
          <Link to="/jobs">Cancelar</Link>
        </Button>
      </div>
    </form>
  );
}

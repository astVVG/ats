import { zodResolver } from "@hookform/resolvers/zod";
import { isAxiosError } from "axios";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { ROLE_LABELS, ROLES } from "@/features/auth/types";
import { useCreateUser } from "@/features/users/hooks/useCreateUser";
import { getProblemDetail } from "@/shared/api/errors";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/shared/ui/dialog";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";

const PASSWORD_MAX_BYTES = 72;

const utf8Length = (value: string) => new TextEncoder().encode(value).length;

const createUserSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Ingresa el nombre")
    .max(150, "El nombre admite como máximo 150 caracteres"),
  email: z
    .string()
    .trim()
    .min(1, "Ingresa el correo")
    .max(255, "El correo admite como máximo 255 caracteres")
    .pipe(z.email("Ingresa un correo válido")),
  password: z
    .string()
    .min(8, "La contraseña debe tener al menos 8 caracteres")
    .refine(
      (value) => utf8Length(value) <= PASSWORD_MAX_BYTES,
      "La contraseña es demasiado larga (máximo 72 bytes; acentos y emojis ocupan más de uno)",
    ),
  role: z.enum(ROLES, { error: "Selecciona un rol" }),
});

type CreateUserFormValues = z.infer<typeof createUserSchema>;
type CreateUserField = keyof CreateUserFormValues;

const FIELDS: readonly CreateUserField[] = [
  "name",
  "email",
  "password",
  "role",
];

function isCreateUserField(field: string): field is CreateUserField {
  return (FIELDS as readonly string[]).includes(field);
}

const EMPTY_FORM: CreateUserFormValues = {
  name: "",
  email: "",
  password: "",
  role: "RECRUITER",
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

export function CreateUserDialog() {
  const [open, setOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const createUser = useCreateUser();
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: EMPTY_FORM,
  });

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (!next) {
      reset(EMPTY_FORM);
      setFormError(null);
    }
  };

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createUser.mutateAsync(values);
      toast.success("Usuario creado");
      handleOpenChange(false);
    } catch (error) {
      const status = isAxiosError(error) ? error.response?.status : undefined;
      const problem = getProblemDetail(error);
      if (status === 409) {
        setError("email", { message: "Este correo ya está registrado" });
      } else if (status === 400 && problem?.errors?.length) {
        let unmatched = false;
        for (const { field, message } of problem.errors) {
          if (isCreateUserField(field)) {
            setError(field, { message });
          } else {
            unmatched = true;
          }
        }
        if (unmatched) {
          setFormError("Revisa los datos del formulario.");
        }
      } else {
        setFormError("No se pudo crear el usuario. Inténtalo de nuevo.");
      }
    }
  });

  const fieldProps = (field: CreateUserField) => ({
    id: `user-${field}`,
    "aria-invalid": errors[field] ? true : undefined,
    "aria-describedby": errors[field] ? `user-${field}-error` : undefined,
  });

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button type="button">Crear usuario</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Crear usuario</DialogTitle>
          <DialogDescription>
            El usuario podrá iniciar sesión con este correo y contraseña.
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          {formError && (
            <Alert variant="destructive">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          )}
          <div className="space-y-2">
            <Label htmlFor="user-name">Nombre</Label>
            <Input
              autoComplete="off"
              {...fieldProps("name")}
              {...register("name")}
            />
            <FieldError id="user-name-error" message={errors.name?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="user-email">Correo</Label>
            <Input
              type="email"
              autoComplete="off"
              {...fieldProps("email")}
              {...register("email")}
            />
            <FieldError id="user-email-error" message={errors.email?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="user-password">Contraseña</Label>
            <Input
              type="password"
              autoComplete="new-password"
              {...fieldProps("password")}
              {...register("password")}
            />
            <FieldError
              id="user-password-error"
              message={errors.password?.message}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="user-role">Rol</Label>
            <Controller
              name="role"
              control={control}
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger className="w-full" {...fieldProps("role")}>
                    <SelectValue placeholder="Selecciona un rol" />
                  </SelectTrigger>
                  <SelectContent>
                    {ROLES.map((role) => (
                      <SelectItem key={role} value={role}>
                        {ROLE_LABELS[role]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            <FieldError id="user-role-error" message={errors.role?.message} />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={createUser.isPending}>
              {createUser.isPending ? "Creando…" : "Crear"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

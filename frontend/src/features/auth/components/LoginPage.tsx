import { zodResolver } from "@hookform/resolvers/zod";
import { isAxiosError } from "axios";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Navigate, useLocation } from "react-router";
import { z } from "zod";
import { useLogin } from "@/features/auth/hooks/useLogin";
import { useSessionStore } from "@/features/auth/store";
import { getProblemDetail } from "@/shared/api/errors";
import { ThemeToggle } from "@/shared/components/ThemeToggle";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";

const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, "Ingresa tu correo")
    .pipe(z.email("Ingresa un correo válido")),
  password: z
    .string()
    .min(1, "Ingresa tu contraseña")
    .max(128, "La contraseña admite como máximo 128 caracteres"),
});

type LoginFormValues = z.infer<typeof loginSchema>;
type LoginField = keyof LoginFormValues;

const LOGIN_FIELDS: readonly LoginField[] = ["email", "password"];

function isLoginField(field: string): field is LoginField {
  return (LOGIN_FIELDS as readonly string[]).includes(field);
}

/** Path saved by RequireAuth before redirecting here. */
function redirectTarget(state: unknown): string {
  if (typeof state === "object" && state !== null && "from" in state) {
    const { from } = state;
    if (typeof from === "string" && from.startsWith("/") && from !== "/login") {
      return from;
    }
  }
  return "/";
}

export function LoginPage() {
  const location = useLocation();
  const isAuthenticated = useSessionStore(
    (state) => state.token !== null && state.user !== null,
  );
  const loginMutation = useLogin();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  if (isAuthenticated) {
    return <Navigate to={redirectTarget(location.state)} replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await loginMutation.mutateAsync(values);
    } catch (error) {
      const status = isAxiosError(error) ? error.response?.status : undefined;
      const problem = getProblemDetail(error);
      if (status === 401) {
        setFormError("Correo o contraseña incorrectos");
      } else if (status === 400 && problem?.errors?.length) {
        for (const { field, message } of problem.errors) {
          if (isLoginField(field)) {
            setError(field, { message });
          }
        }
      } else {
        setFormError(
          "No se pudo iniciar sesión. Revisa tu conexión e inténtalo de nuevo.",
        );
      }
    }
  });

  const isSubmitting = loginMutation.isPending;

  return (
    <main className="relative flex min-h-screen items-center justify-center px-4">
      <ThemeToggle className="absolute top-4 right-4" />
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl font-semibold">Iniciar sesión</h1>
          </CardTitle>
          <CardDescription>Mini ATS</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onSubmit} noValidate>
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            <div className="space-y-2">
              <Label htmlFor="login-email">Correo</Label>
              <Input
                id="login-email"
                type="email"
                autoComplete="email"
                aria-invalid={errors.email ? true : undefined}
                aria-describedby={
                  errors.email ? "login-email-error" : undefined
                }
                {...register("email")}
              />
              {errors.email && (
                <p id="login-email-error" className="text-sm text-destructive">
                  {errors.email.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="login-password">Contraseña</Label>
              <Input
                id="login-password"
                type="password"
                autoComplete="current-password"
                aria-invalid={errors.password ? true : undefined}
                aria-describedby={
                  errors.password ? "login-password-error" : undefined
                }
                {...register("password")}
              />
              {errors.password && (
                <p
                  id="login-password-error"
                  className="text-sm text-destructive"
                >
                  {errors.password.message}
                </p>
              )}
            </div>
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "Iniciando sesión…" : "Iniciar sesión"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}

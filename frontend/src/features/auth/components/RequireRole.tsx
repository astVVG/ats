import { Navigate, Outlet } from "react-router";
import { useAuth } from "@/features/auth/hooks/useAuth";
import type { Role } from "@/features/auth/types";

type RequireRoleProps = {
  role: Role;
};

/** Must be nested under RequireAuth, which guarantees a loaded user. */
export function RequireRole({ role }: RequireRoleProps) {
  const auth = useAuth();

  if (auth.role !== role) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

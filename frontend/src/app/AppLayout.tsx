import { Link, NavLink, Outlet, useOutletContext } from "react-router";
import type { AuthOutletContext } from "@/features/auth/components/RequireAuth";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { ROLE_LABELS } from "@/features/auth/types";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";

function navLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    "text-sm text-muted-foreground hover:text-foreground",
    isActive && "font-medium text-foreground",
  );
}

export function AppLayout() {
  const { user, role } = useAuth();
  const { logout } = useOutletContext<AuthOutletContext>();

  return (
    <div className="min-h-screen">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
          <Link to="/" className="font-semibold">
            Mini ATS
          </Link>
          <nav aria-label="Principal" className="flex items-center gap-4">
            <NavLink to="/" end className={navLinkClass}>
              Inicio
            </NavLink>
            {role === "ADMIN" && (
              <NavLink to="/users" className={navLinkClass}>
                Usuarios
              </NavLink>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <span className="text-sm">{user?.name}</span>
            {role && <Badge variant="secondary">{ROLE_LABELS[role]}</Badge>}
            <Button type="button" variant="outline" size="sm" onClick={logout}>
              Cerrar sesión
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}

import { Link, NavLink, Outlet, useOutletContext } from "react-router";
import type { AuthOutletContext } from "@/features/auth/components/RequireAuth";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { ROLE_LABELS } from "@/features/auth/types";
import { ThemeToggle } from "@/shared/components/ThemeToggle";
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
        {/* Below lg the row wraps and the links get their own scrolling line,
            so the page never scrolls sideways on a phone. */}
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-2 lg:h-14 lg:flex-nowrap lg:py-0">
          <Link to="/" className="font-semibold">
            Mini ATS
          </Link>
          <nav
            aria-label="Principal"
            className="order-last -mx-4 flex w-[calc(100%+2rem)] items-center gap-4 overflow-x-auto px-4 pb-1 whitespace-nowrap lg:order-none lg:mx-0 lg:w-auto lg:overflow-visible lg:px-0 lg:pb-0"
          >
            <NavLink to="/" end className={navLinkClass}>
              Inicio
            </NavLink>
            <NavLink to="/jobs" className={navLinkClass}>
              Vacantes
            </NavLink>
            <NavLink to="/candidates" className={navLinkClass}>
              Candidatos
            </NavLink>
            <NavLink to="/metrics" className={navLinkClass}>
              Métricas
            </NavLink>
            {role === "ADMIN" && (
              <NavLink to="/users" className={navLinkClass}>
                Usuarios
              </NavLink>
            )}
          </nav>
          <div className="ml-auto flex flex-wrap items-center justify-end gap-x-3 gap-y-2 lg:flex-nowrap">
            <ThemeToggle />
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

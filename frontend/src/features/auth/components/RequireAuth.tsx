import { useCallback, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { useLogout } from "@/features/auth/hooks/useLogout";
import { Button } from "@/shared/ui/button";

/** Available to child routes through useOutletContext. */
export type AuthOutletContext = {
  logout: () => void;
};

export function RequireAuth() {
  const { token, isLoading, isError, retry } = useAuth();
  const endSession = useLogout();
  const location = useLocation();
  const [loggingOut, setLoggingOut] = useState(false);

  const logout = useCallback(() => {
    setLoggingOut(true);
    endSession();
  }, [endSession]);

  if (token === null) {
    // An explicit logout starts fresh; a missing or expired session returns here after login.
    return (
      <Navigate
        to="/login"
        replace
        state={
          loggingOut ? undefined : { from: location.pathname + location.search }
        }
      />
    );
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p role="status" className="text-muted-foreground">
          Cargando sesión…
        </p>
      </div>
    );
  }

  // Network or server failure while loading /me; a 401 already cleared the session.
  if (isError) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4">
        <p role="alert">No se pudo cargar tu sesión.</p>
        <div className="flex gap-2">
          <Button type="button" onClick={() => void retry()}>
            Reintentar
          </Button>
          <Button type="button" variant="outline" onClick={logout}>
            Cerrar sesión
          </Button>
        </div>
      </div>
    );
  }

  return <Outlet context={{ logout } satisfies AuthOutletContext} />;
}

import { useHealth } from "@/shared/hooks/useHealth";
import { cn } from "@/shared/lib/utils";

export function BackendStatus() {
  const { data, isPending, isError } = useHealth();

  const state = isPending
    ? {
        label: "Verificando backend…",
        dot: "bg-muted-foreground animate-pulse",
      }
    : !isError && data.status === "UP"
      ? { label: "Backend disponible", dot: "bg-emerald-500" }
      : { label: "Backend no disponible", dot: "bg-destructive" };

  return (
    <p
      role="status"
      className="inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm text-muted-foreground"
    >
      <span
        aria-hidden="true"
        className={cn("size-2 rounded-full", state.dot)}
      />
      {state.label}
    </p>
  );
}

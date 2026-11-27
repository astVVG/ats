import type { ReactNode } from "react";
import { metricsErrorMessage } from "@/features/metrics/format";
import { cn } from "@/shared/lib/utils";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";

/** The parts of a TanStack query a section needs to pick its state. */
type SectionQuery<T> = {
  data: T | undefined;
  error: unknown;
  isError: boolean;
  isFetching: boolean;
  isPending: boolean;
  refetch: () => unknown;
};

type MetricSectionProps<T> = {
  id: string;
  title: string;
  description: ReactNode;
  /** "el embudo", for the error message. */
  what: string;
  query: SectionQuery<T>;
  /** The filters are invalid, so nothing was requested. */
  blocked: boolean;
  isEmpty: (data: T) => boolean;
  emptyMessage: string;
  children: (data: T) => ReactNode;
  className?: string;
};

/** A metric card with its loading, error, blocked and empty states. */
export function MetricSection<T>({
  id,
  title,
  description,
  what,
  query,
  blocked,
  isEmpty,
  emptyMessage,
  children,
  className,
}: MetricSectionProps<T>) {
  const headingId = `${id}-heading`;

  let body: ReactNode;
  if (blocked) {
    body = (
      <p className="text-sm text-muted-foreground">
        Corrige el rango de fechas para ver los resultados.
      </p>
    );
  } else if (query.isPending) {
    body = (
      <p role="status" className="text-sm text-muted-foreground">
        Cargando…
      </p>
    );
  } else if (query.isError) {
    body = (
      <Alert variant="destructive">
        <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
          <span>{metricsErrorMessage(query.error, what)}</span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={query.isFetching}
            onClick={() => void query.refetch()}
          >
            Reintentar
          </Button>
        </AlertDescription>
      </Alert>
    );
  } else if (query.data === undefined || isEmpty(query.data)) {
    body = (
      <div className="rounded-lg border border-dashed p-6 text-center">
        <p className="font-medium">Sin datos</p>
        <p className="text-sm text-muted-foreground">{emptyMessage}</p>
      </div>
    );
  } else {
    body = children(query.data);
  }

  return (
    <Card
      role="region"
      aria-labelledby={headingId}
      aria-busy={(!blocked && query.isPending) || undefined}
      className={cn("min-w-0", className)}
    >
      <CardHeader>
        <CardTitle>
          <h2 id={headingId}>{title}</h2>
        </CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="min-w-0 space-y-4">{body}</CardContent>
    </Card>
  );
}

import type { ReactNode } from "react";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";

/** Loading, error and empty states shared by the sections of the application panel. */

export function SectionLoading({ children }: { children: ReactNode }) {
  return (
    <p role="status" className="text-sm text-muted-foreground">
      {children}
    </p>
  );
}

type SectionErrorProps = {
  message: string;
  onRetry: () => void;
  retrying: boolean;
};

export function SectionError({
  message,
  onRetry,
  retrying,
}: SectionErrorProps) {
  return (
    <Alert variant="destructive">
      <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
        <span>{message}</span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={retrying}
          onClick={onRetry}
        >
          Reintentar
        </Button>
      </AlertDescription>
    </Alert>
  );
}

export function SectionEmpty({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">
      {children}
    </p>
  );
}

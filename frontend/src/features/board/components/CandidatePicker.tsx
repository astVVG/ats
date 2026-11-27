import { useState } from "react";
import { useCandidates } from "@/features/candidates/hooks/useCandidates";
import type { Candidate } from "@/features/candidates/types";
import { useDebouncedValue } from "@/shared/hooks/useDebouncedValue";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";

const PAGE_SIZE = 10;

type CandidatePickerProps = {
  onPick: (candidate: Candidate) => void;
  /** An application is being created. */
  disabled: boolean;
};

/** Searches existing candidates by name or email and picks one. */
export function CandidatePicker({ onPick, disabled }: CandidatePickerProps) {
  const [search, setSearch] = useState("");
  const q = useDebouncedValue(search.trim());
  const { data, isPending, isError, isFetching, refetch } = useCandidates({
    page: 0,
    size: PAGE_SIZE,
    q: q === "" ? undefined : q,
  });

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="candidate-search">Buscar</Label>
        <Input
          id="candidate-search"
          type="search"
          placeholder="Nombre o correo"
          autoComplete="off"
          // The dialog keeps focus where it already is, so this wins over its default.
          autoFocus
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      {isPending ? (
        <p role="status" className="text-sm text-muted-foreground">
          Buscando candidatos…
        </p>
      ) : isError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between gap-4">
            <span>No se pudieron buscar los candidatos.</span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={isFetching}
              onClick={() => void refetch()}
            >
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      ) : data.content.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No se encontraron candidatos.
        </p>
      ) : (
        <>
          <ul
            aria-label="Candidatos encontrados"
            className="max-h-72 divide-y overflow-y-auto rounded-lg border"
          >
            {data.content.map((candidate) => (
              <li
                key={candidate.id}
                className="flex items-center justify-between gap-3 px-3 py-2"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium">{candidate.fullName}</p>
                  <p
                    className="truncate text-sm text-muted-foreground"
                    title={candidate.email}
                  >
                    {candidate.email}
                  </p>
                </div>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={disabled}
                  aria-label={`Agregar a ${candidate.fullName}`}
                  onClick={() => onPick(candidate)}
                >
                  Agregar
                </Button>
              </li>
            ))}
          </ul>
          {data.totalPages > 1 && (
            <p className="text-sm text-muted-foreground">
              Hay más resultados. Afina la búsqueda.
            </p>
          )}
        </>
      )}
    </div>
  );
}

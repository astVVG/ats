import { useState } from "react";
import { Link } from "react-router";
import { useCandidates } from "@/features/candidates/hooks/useCandidates";
import { Pagination } from "@/shared/components/Pagination";
import { useDebouncedValue } from "@/shared/hooks/useDebouncedValue";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table";

const PAGE_SIZE = 20;

export function CandidatesPage() {
  const [search, setSearch] = useState("");
  const q = useDebouncedValue(search.trim());
  // The page belongs to the search it was chosen with, so a new search starts at page 0.
  const [paging, setPaging] = useState({ q, page: 0 });
  const page = paging.q === q ? paging.page : 0;

  const { data, isPending, isError, isFetching, refetch } = useCandidates({
    page,
    size: PAGE_SIZE,
    q: q === "" ? undefined : q,
  });

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-3xl font-bold">Candidatos</h1>
        <Button asChild>
          <Link to="/candidates/new">Nuevo candidato</Link>
        </Button>
      </div>

      <div className="max-w-md space-y-2">
        <Label htmlFor="candidates-search">Buscar</Label>
        <Input
          id="candidates-search"
          type="search"
          placeholder="Nombre o correo"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      {isPending ? (
        <p role="status" className="text-muted-foreground">
          Cargando candidatos…
        </p>
      ) : isError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between gap-4">
            <span>No se pudieron cargar los candidatos.</span>
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
        <p className="text-muted-foreground">
          {q === ""
            ? "Aún no hay candidatos. Registra el primero."
            : "No hay candidatos que coincidan con la búsqueda."}
        </p>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nombre</TableHead>
                <TableHead>Correo</TableHead>
                <TableHead>Teléfono</TableHead>
                <TableHead>Origen</TableHead>
                <TableHead>CV</TableHead>
                <TableHead className="text-right">Acciones</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((candidate) => (
                <TableRow key={candidate.id}>
                  <TableCell className="font-medium">
                    <Link
                      to={`/candidates/${candidate.id}`}
                      className="cursor-pointer text-primary underline-offset-4 hover:underline"
                    >
                      {candidate.fullName}
                    </Link>
                  </TableCell>
                  <TableCell>{candidate.email}</TableCell>
                  <TableCell>{candidate.phone ?? "—"}</TableCell>
                  <TableCell>{candidate.source ?? "—"}</TableCell>
                  <TableCell>
                    {candidate.hasResume ? (
                      <Badge>Con CV</Badge>
                    ) : (
                      <Badge variant="outline">Sin CV</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-2">
                      <Button asChild variant="outline" size="sm">
                        <Link
                          to={`/candidates/${candidate.id}`}
                          aria-label={`Ver detalle de ${candidate.fullName}`}
                        >
                          Ver detalle
                        </Link>
                      </Button>
                      <Button asChild variant="outline" size="sm">
                        <Link
                          to={`/candidates/${candidate.id}/edit`}
                          aria-label={`Editar a ${candidate.fullName}`}
                        >
                          Editar
                        </Link>
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination
            page={page}
            totalPages={data.totalPages}
            onPageChange={(next) => setPaging({ q, page: next })}
          />
        </>
      )}
    </section>
  );
}

import { useState } from "react";
import { ROLE_LABELS } from "@/features/auth/types";
import { CreateUserDialog } from "@/features/users/components/CreateUserDialog";
import { useUsers } from "@/features/users/hooks/useUsers";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table";

const PAGE_SIZE = 10;

const dateFormat = new Intl.DateTimeFormat("es-MX", { dateStyle: "medium" });

function formatDate(value: string) {
  return dateFormat.format(new Date(value));
}

export function UsersPage() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, isFetching, refetch } = useUsers({
    page,
    size: PAGE_SIZE,
  });

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-3xl font-bold">Usuarios</h1>
        <CreateUserDialog />
      </div>

      {isPending ? (
        <p role="status" className="text-muted-foreground">
          Cargando usuarios…
        </p>
      ) : isError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between gap-4">
            <span>No se pudieron cargar los usuarios.</span>
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
        <p className="text-muted-foreground">No hay usuarios</p>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nombre</TableHead>
                <TableHead>Correo</TableHead>
                <TableHead>Rol</TableHead>
                <TableHead>Estado</TableHead>
                <TableHead>Fecha de alta</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((user) => (
                <TableRow key={user.id}>
                  <TableCell className="font-medium">{user.name}</TableCell>
                  <TableCell>{user.email}</TableCell>
                  <TableCell>{ROLE_LABELS[user.role]}</TableCell>
                  <TableCell>
                    {user.active ? (
                      <Badge>Activo</Badge>
                    ) : (
                      <Badge variant="outline">Inactivo</Badge>
                    )}
                  </TableCell>
                  <TableCell>{formatDate(user.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <div className="flex items-center justify-end gap-4">
            <span className="text-sm text-muted-foreground">
              Página {page + 1} de {Math.max(data.totalPages, 1)}
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((current) => current - 1)}
            >
              Anterior
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Siguiente
            </Button>
          </div>
        </>
      )}
    </section>
  );
}

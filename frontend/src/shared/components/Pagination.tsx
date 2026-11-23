import { Button } from "@/shared/ui/button";

type PaginationProps = {
  /** Zero-based, as the API returns it. */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

export function Pagination({
  page,
  totalPages,
  onPageChange,
}: PaginationProps) {
  const lastPage = Math.max(totalPages, 1) - 1;

  return (
    <nav
      aria-label="Paginación"
      className="flex items-center justify-end gap-4"
    >
      <span className="text-sm text-muted-foreground">
        Página {page + 1} de {lastPage + 1}
      </span>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Anterior
      </Button>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={page >= lastPage}
        onClick={() => onPageChange(page + 1)}
      >
        Siguiente
      </Button>
    </nav>
  );
}

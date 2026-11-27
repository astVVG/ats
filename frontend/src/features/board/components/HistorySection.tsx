import { ArrowRight } from "lucide-react";
import { useState } from "react";
import {
  SectionEmpty,
  SectionError,
  SectionLoading,
} from "@/features/board/components/DetailStates";
import { useHistory } from "@/features/board/hooks/useHistory";
import type { StageHistoryEntry } from "@/features/board/types";
import { Pagination } from "@/shared/components/Pagination";
import { StageBadge } from "@/shared/components/StageBadge";

const dateTimeFormat = new Intl.DateTimeFormat("es-MX", {
  dateStyle: "medium",
  timeStyle: "short",
});

function HistoryItem({ entry }: { entry: StageHistoryEntry }) {
  return (
    <li className="relative space-y-1 pl-5">
      <span
        aria-hidden="true"
        className="absolute top-1.5 -left-[5px] size-2.5 rounded-full bg-primary ring-4 ring-background"
      />
      <div className="flex flex-wrap items-center gap-1.5 text-sm">
        {entry.fromStage ? (
          <>
            <StageBadge stage={entry.fromStage} />
            <ArrowRight
              aria-hidden="true"
              className="size-3.5 text-muted-foreground"
            />
            <span className="sr-only">a</span>
            <StageBadge stage={entry.toStage} />
          </>
        ) : (
          <>
            <span className="font-medium">Postulación creada</span>
            <StageBadge stage={entry.toStage} />
          </>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        Por {entry.movedBy.name}
        {" · "}
        <time dateTime={entry.movedAt}>
          {dateTimeFormat.format(new Date(entry.movedAt))}
        </time>
      </p>
    </li>
  );
}

/** Stage changes, newest first, as the API returns them. */
export function HistorySection({ applicationId }: { applicationId: number }) {
  const [page, setPage] = useState(0);
  const query = useHistory(applicationId, page);
  const data = query.data;

  return (
    <section aria-labelledby="detail-history-heading" className="space-y-3">
      <h3 id="detail-history-heading" className="font-medium">
        Historial
      </h3>
      {query.isPending && <SectionLoading>Cargando historial…</SectionLoading>}
      {query.isError && (
        <SectionError
          message="No se pudo cargar el historial."
          retrying={query.isFetching}
          onRetry={() => void query.refetch()}
        />
      )}
      {data && data.content.length === 0 && (
        <SectionEmpty>Sin movimientos registrados.</SectionEmpty>
      )}
      {data && data.content.length > 0 && (
        <>
          <ol
            aria-label="Historial de etapas"
            aria-busy={query.isPlaceholderData || undefined}
            className="ml-1.5 space-y-4 border-l py-1"
          >
            {data.content.map((entry) => (
              <HistoryItem key={entry.id} entry={entry} />
            ))}
          </ol>
          {data.totalPages > 1 && (
            <Pagination
              page={page}
              totalPages={data.totalPages}
              onPageChange={setPage}
            />
          )}
        </>
      )}
    </section>
  );
}

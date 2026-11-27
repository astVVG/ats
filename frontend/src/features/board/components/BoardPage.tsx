import {
  closestCorners,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type Announcements,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
  type ScreenReaderInstructions,
  type UniqueIdentifier,
} from "@dnd-kit/core";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import { isAxiosError } from "axios";
import { useRef, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { useAuth } from "@/features/auth/hooks/useAuth";
import {
  canMoveBetween,
  findContainer,
  isColumnId,
  previewMove,
  resolveDrop,
} from "@/features/board/board-dnd";
import { findCard } from "@/features/board/board-cache";
import { AddApplicationDialog } from "@/features/board/components/AddApplicationDialog";
import { ApplicationDetailSheet } from "@/features/board/components/ApplicationDetailSheet";
import { BoardCardOverlay } from "@/features/board/components/BoardCard";
import { BoardColumn } from "@/features/board/components/BoardColumn";
import { useJobBoard } from "@/features/board/hooks/useJobBoard";
import { useMoveApplication } from "@/features/board/hooks/useMoveApplication";
import { HIRED_ADMIN_ONLY } from "@/features/board/messages";
import {
  parseJobId,
  type BoardColumn as BoardColumnData,
} from "@/features/board/types";
import { JOB_STATUS_LABELS } from "@/features/jobs/types";
import { STAGE_LABELS, type Stage } from "@/shared/lib/stages";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";

const screenReaderInstructions: ScreenReaderInstructions = {
  draggable:
    "Para mover una postulación, presiona Espacio o Intro para levantarla. " +
    "Usa las flechas para cambiarla de posición o de columna. " +
    "Presiona Espacio o Intro otra vez para soltarla, o Escape para cancelar.",
};

/** Name, column and 1-based place of a card, for screen reader messages. */
function describeCard(
  columns: readonly BoardColumnData[],
  id: UniqueIdentifier,
) {
  for (const column of columns) {
    const index = column.applications.findIndex((a) => a.id === id);
    if (index >= 0) {
      return {
        name: column.applications[index].candidate.fullName,
        stage: column.stage,
        place: index + 1,
        total: column.applications.length,
      };
    }
  }
  return null;
}

function JobNotFound() {
  return (
    <section className="space-y-4">
      <h1 className="text-3xl font-bold">La vacante no existe</h1>
      <p className="text-muted-foreground">
        No encontramos la vacante de este tablero.
      </p>
      <Button asChild variant="outline">
        <Link to="/jobs">Volver a vacantes</Link>
      </Button>
    </section>
  );
}

// One component on purpose: the one that clears the drag copy must also read
// the board query, so the render after a drop already shows the moved card.
function JobBoard({ jobId }: { jobId: number }) {
  const query = useJobBoard(jobId);
  const { role } = useAuth();
  const move = useMoveApplication(jobId);
  // Local copy while dragging; null shows the cached board, moves included.
  const [dragColumns, setDragColumns] = useState<BoardColumnData[] | null>(
    null,
  );
  const [activeId, setActiveId] = useState<UniqueIdentifier | null>(null);
  const [overStage, setOverStage] = useState<Stage | null>(null);
  // Only the id: stage and rating are read from the cached board on each render.
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const lastOpenedId = useRef<number | null>(null);
  const sensors = useSensors(
    // A short distance keeps clicks on the candidate link working.
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  if (query.isPending) {
    return (
      <p role="status" className="text-muted-foreground">
        Cargando tablero…
      </p>
    );
  }

  if (query.isError) {
    if (isAxiosError(query.error) && query.error.response?.status === 404) {
      return <JobNotFound />;
    }
    return (
      <Alert variant="destructive">
        <AlertDescription className="flex items-center justify-between gap-4">
          <span>No se pudo cargar el tablero.</span>
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
  }

  const board = query.data;
  const selected = selectedId === null ? null : findCard(board, selectedId);
  if (selectedId !== null && selected === null) {
    // The application left the board (deleted or moved to another job): close.
    setSelectedId(null);
  }
  const serverColumns = board.columns;
  const columns = dragColumns ?? serverColumns;
  const closed = board.job.status === "CLOSED";
  const dragDisabled = closed;
  const total = serverColumns.reduce(
    (sum, column) => sum + column.applications.length,
    0,
  );
  const activeCard =
    activeId === null
      ? undefined
      : columns
          .flatMap((column) => column.applications)
          .find((card) => card.id === activeId);

  function openDetail(applicationId: number) {
    lastOpenedId.current = applicationId;
    setSelectedId(applicationId);
  }

  function detailTrigger() {
    return document.querySelector<HTMLElement>(
      `[data-detail-trigger="${lastOpenedId.current}"]`,
    );
  }

  function reset() {
    setActiveId(null);
    setOverStage(null);
    setDragColumns(null);
  }

  /** Where the card may go: the column of `overId`, unless the role forbids it. */
  function allowedTarget(
    activeKey: UniqueIdentifier,
    overKey: UniqueIdentifier,
  ) {
    const origin = findContainer(serverColumns, activeKey);
    const target = findContainer(columns, overKey);
    if (origin === null || target === null) {
      return { target: null, forbidden: false };
    }
    return canMoveBetween(origin, target, role)
      ? { target, forbidden: false }
      : { target: null, forbidden: true };
  }

  function handleDragStart({ active }: DragStartEvent) {
    setActiveId(active.id);
    setOverStage(findContainer(serverColumns, active.id));
    setDragColumns(serverColumns);
  }

  function handleDragOver({ active, over }: DragOverEvent) {
    if (!over) {
      setOverStage(null);
      return;
    }
    const { target } = allowedTarget(active.id, over.id);
    setOverStage(target);
    if (target === null || target === findContainer(columns, active.id)) {
      return;
    }
    // Crossing into another column: the sortable list handles reorders inside one.
    const dragged = active.rect.current.translated;
    const insertAfter =
      dragged !== null &&
      !isColumnId(over.id) &&
      dragged.top > over.rect.top + over.rect.height / 2;
    const next = previewMove(columns, active.id, over.id, insertAfter);
    if (next !== columns) {
      setDragColumns(next);
    }
  }

  function handleDragEnd({ active, over }: DragEndEvent) {
    setActiveId(null);
    setOverStage(null);
    if (!over) {
      setDragColumns(null);
      return;
    }
    const { target, forbidden } = allowedTarget(active.id, over.id);
    if (target === null) {
      setDragColumns(null);
      if (forbidden) {
        toast.info(HIRED_ADMIN_ONLY);
      }
      return;
    }
    const final = previewMove(columns, active.id, over.id);
    const drop = resolveDrop(serverColumns, final, active.id);
    if (drop === null) {
      setDragColumns(null);
      return;
    }
    // mutate() writes the move to the cache before returning, so dropping the
    // local copy in the same render shows the new order without a jump.
    move.mutate(drop);
    setDragColumns(null);
  }

  const announcements: Announcements = {
    onDragStart({ active }) {
      const card = describeCard(serverColumns, active.id);
      return card
        ? `Levantaste la postulación de ${card.name}. Está en ${STAGE_LABELS[card.stage]}, posición ${card.place} de ${card.total}.`
        : undefined;
    },
    onDragOver({ active, over }) {
      const card = describeCard(columns, active.id);
      if (!card) {
        return undefined;
      }
      if (!over) {
        return `La postulación de ${card.name} no está sobre ninguna columna.`;
      }
      if (over.id === active.id) {
        // Still in its own place; keep the last message.
        return undefined;
      }
      const { target, forbidden } = allowedTarget(active.id, over.id);
      if (forbidden) {
        return `${HIRED_ADMIN_ONLY} La postulación de ${card.name} no puede ir a esa columna.`;
      }
      if (target === null) {
        return undefined;
      }
      const overCard = describeCard(columns, over.id);
      const targetSize =
        columns.find((c) => c.stage === target)?.applications.length ?? 0;
      const place = overCard ? overCard.place : targetSize + 1;
      return `La postulación de ${card.name} está sobre ${STAGE_LABELS[target]}, posición ${place}.`;
    },
    onDragEnd({ active, over }) {
      const card = describeCard(columns, active.id);
      if (!card) {
        return undefined;
      }
      const target = over ? allowedTarget(active.id, over.id).target : null;
      return target
        ? `Soltaste la postulación de ${card.name} en ${STAGE_LABELS[target]}.`
        : `La postulación de ${card.name} volvió a su lugar.`;
    },
    onDragCancel({ active }) {
      const card = describeCard(serverColumns, active.id);
      return card
        ? `Movimiento cancelado. La postulación de ${card.name} volvió a ${STAGE_LABELS[card.stage]}.`
        : "Movimiento cancelado.";
    },
  };

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <h1 className="text-3xl font-bold">{board.job.title}</h1>
          <div className="flex items-center gap-3 text-sm text-muted-foreground">
            <Badge variant={closed ? "outline" : "default"}>
              {JOB_STATUS_LABELS[board.job.status]}
            </Badge>
            <span>
              {total === 1 ? "1 postulación" : `${total} postulaciones`}
            </span>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <AddApplicationDialog jobId={jobId} closed={closed} />
          <Button asChild variant="outline">
            <Link to="/jobs">Volver a vacantes</Link>
          </Button>
        </div>
      </div>

      {closed && (
        <Alert>
          <AlertDescription>
            Vacante cerrada: no se pueden mover ni agregar postulaciones. Las
            notas y la calificación siguen disponibles.
          </AlertDescription>
        </Alert>
      )}

      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        accessibility={{ screenReaderInstructions, announcements }}
        onDragStart={handleDragStart}
        onDragOver={handleDragOver}
        onDragEnd={handleDragEnd}
        onDragCancel={reset}
      >
        <div className="flex gap-4 overflow-x-auto pb-4">
          {columns.map((column) => (
            <BoardColumn
              key={column.stage}
              column={column}
              highlighted={activeId !== null && overStage === column.stage}
              dragDisabled={dragDisabled}
              onOpenDetail={openDetail}
              jobId={jobId}
            />
          ))}
        </div>
        <DragOverlay>
          {activeCard ? <BoardCardOverlay card={activeCard} /> : null}
        </DragOverlay>
      </DndContext>

      <ApplicationDetailSheet
        jobId={jobId}
        selected={selected}
        onClose={() => setSelectedId(null)}
        returnFocusTo={detailTrigger}
      />
    </section>
  );
}

export function BoardPage() {
  const jobId = parseJobId(useParams().id);
  return jobId === null ? <JobNotFound /> : <JobBoard jobId={jobId} />;
}

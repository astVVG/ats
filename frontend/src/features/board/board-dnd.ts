import type { BoardColumn } from "@/features/board/types";
import type { Role } from "@/features/auth/types";
import { STAGES, type Stage } from "@/shared/lib/stages";

/** dnd-kit ids: cards use the application id, columns use their stage. */
export type DndId = string | number;

export type MoveTarget = {
  applicationId: number;
  stage: Stage;
  position: number;
};

/** Column ids are stages; card ids are application ids. */
export function isColumnId(id: DndId): id is Stage {
  return (STAGES as readonly DndId[]).includes(id);
}

/** Stage of the column that is `id` or that holds the card `id`. */
export function findContainer(
  columns: readonly BoardColumn[],
  id: DndId,
): Stage | null {
  if (isColumnId(id)) {
    return columns.some((column) => column.stage === id) ? id : null;
  }
  const column = columns.find((c) => c.applications.some((a) => a.id === id));
  return column ? column.stage : null;
}

function locate(columns: readonly BoardColumn[], id: DndId) {
  const stage = findContainer(columns, id);
  if (stage === null || isColumnId(id)) {
    return null;
  }
  const column = columns.find((c) => c.stage === stage);
  const index = column ? column.applications.findIndex((a) => a.id === id) : -1;
  return index < 0 ? null : { stage, index };
}

/**
 * Columns as they look with the card `activeId` placed over `overId`: before
 * that card (or after it with `insertAfter`), or at the end when `overId` is a
 * column. Returns the same array when nothing changes; never mutates it.
 */
export function previewMove(
  columns: readonly BoardColumn[],
  activeId: DndId,
  overId: DndId,
  insertAfter = false,
): BoardColumn[] {
  const from = locate(columns, activeId);
  const toStage = findContainer(columns, overId);
  if (from === null || toStage === null || activeId === overId) {
    return columns as BoardColumn[];
  }

  const source = columns.find((c) => c.stage === from.stage)!;
  const card = source.applications[from.index];
  const target = columns.find((c) => c.stage === toStage)!;
  const remaining =
    from.stage === toStage
      ? source.applications.filter((a) => a.id !== activeId)
      : target.applications;

  let index: number;
  if (isColumnId(overId)) {
    if (from.stage === toStage) {
      return columns as BoardColumn[];
    }
    index = remaining.length;
  } else {
    const overIndex = remaining.findIndex((a) => a.id === overId);
    // Within one column, dropping on a card below takes that card's place.
    const below = from.stage === toStage && overIndex >= from.index;
    index = overIndex + (insertAfter || below ? 1 : 0);
  }

  const placed = [
    ...remaining.slice(0, index),
    card,
    ...remaining.slice(index),
  ];
  if (
    from.stage === toStage &&
    placed.every((a, i) => a.id === source.applications[i].id)
  ) {
    return columns as BoardColumn[];
  }

  return columns.map((column) => {
    if (column.stage === toStage) {
      return { ...column, applications: placed };
    }
    if (column.stage === from.stage) {
      return {
        ...column,
        applications: column.applications.filter((a) => a.id !== activeId),
      };
    }
    return column;
  });
}

/**
 * Where the card `activeId` ended up, as the backend expects it: the final
 * index in its column. Null when it is where it started.
 */
export function resolveDrop(
  original: readonly BoardColumn[],
  current: readonly BoardColumn[],
  activeId: DndId,
): MoveTarget | null {
  const before = locate(original, activeId);
  const after = locate(current, activeId);
  if (before === null || after === null) {
    return null;
  }
  if (before.stage === after.stage && before.index === after.index) {
    return null;
  }
  return {
    applicationId: Number(activeId),
    stage: after.stage,
    position: after.index,
  };
}

/** Only an ADMIN can take an application out of HIRED. */
export function canMoveBetween(from: Stage, to: Stage, role: Role | null) {
  return from !== "HIRED" || to === "HIRED" || role === "ADMIN";
}

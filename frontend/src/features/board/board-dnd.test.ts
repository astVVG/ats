import { describe, expect, it } from "vitest";
import {
  applyMove,
  canMoveBetween,
  findContainer,
  previewMove,
  resolveDrop,
} from "@/features/board/board-dnd";
import type { Board, BoardColumn } from "@/features/board/types";
import type { Stage } from "@/shared/lib/stages";
import { buildBoard, buildBoardCard } from "@/test/utils";

/** APPLIED 1,2,3 · INTERVIEW 4,5 · OFFER empty · HIRED 6 · REJECTED empty */
function columns(): BoardColumn[] {
  const cards = (...ids: number[]) => ids.map((id) => buildBoardCard({ id }));
  return buildBoard({
    cards: {
      APPLIED: cards(1, 2, 3),
      INTERVIEW: cards(4, 5),
      HIRED: cards(6),
    },
  }).columns;
}

function ids(cols: readonly BoardColumn[], stage: Stage) {
  return cols.find((c) => c.stage === stage)!.applications.map((a) => a.id);
}

describe("findContainer", () => {
  it("finds the column of a card and of a column id", () => {
    const cols = columns();
    expect(findContainer(cols, 2)).toBe("APPLIED");
    expect(findContainer(cols, 5)).toBe("INTERVIEW");
    expect(findContainer(cols, "OFFER")).toBe("OFFER");
  });

  it("returns null for unknown ids", () => {
    expect(findContainer(columns(), 999)).toBeNull();
    expect(findContainer(columns(), "ONBOARDING")).toBeNull();
  });
});

describe("previewMove", () => {
  it("moves a card before a card of another column", () => {
    const cols = previewMove(columns(), 2, 4);
    expect(ids(cols, "APPLIED")).toEqual([1, 3]);
    expect(ids(cols, "INTERVIEW")).toEqual([2, 4, 5]);
  });

  it("moves a card into the middle of another column", () => {
    const cols = previewMove(columns(), 1, 5);
    expect(ids(cols, "INTERVIEW")).toEqual([4, 1, 5]);
  });

  it("moves a card after a card of another column", () => {
    const cols = previewMove(columns(), 1, 5, true);
    expect(ids(cols, "INTERVIEW")).toEqual([4, 5, 1]);
  });

  it("moves a card to the end when dropped on a column", () => {
    const cols = previewMove(columns(), 3, "INTERVIEW");
    expect(ids(cols, "APPLIED")).toEqual([1, 2]);
    expect(ids(cols, "INTERVIEW")).toEqual([4, 5, 3]);
  });

  it("moves a card into an empty column", () => {
    const cols = previewMove(columns(), 6, "OFFER");
    expect(ids(cols, "HIRED")).toEqual([]);
    expect(ids(cols, "OFFER")).toEqual([6]);
  });

  it("reorders downwards inside a column", () => {
    const cols = previewMove(columns(), 1, 3);
    expect(ids(cols, "APPLIED")).toEqual([2, 3, 1]);
  });

  it("reorders upwards inside a column", () => {
    const cols = previewMove(columns(), 3, 1);
    expect(ids(cols, "APPLIED")).toEqual([3, 1, 2]);
  });

  it("returns the same columns when nothing changes", () => {
    const cols = columns();
    expect(previewMove(cols, 2, 2)).toBe(cols);
    expect(previewMove(cols, 2, "APPLIED")).toBe(cols);
    expect(previewMove(cols, 999, 1)).toBe(cols);
    expect(previewMove(cols, 1, 999)).toBe(cols);
  });

  it("does not mutate the original columns", () => {
    const cols = columns();
    const snapshot = structuredClone(cols);

    previewMove(cols, 2, 4);
    previewMove(cols, 1, 3);
    previewMove(cols, 6, "OFFER");

    expect(cols).toEqual(snapshot);
  });
});

describe("resolveDrop", () => {
  it("returns null when the card is where it started", () => {
    const cols = columns();
    expect(resolveDrop(cols, cols, 2)).toBeNull();
    expect(resolveDrop(cols, previewMove(cols, 2, "APPLIED"), 2)).toBeNull();
  });

  it("returns the final index inside the same column", () => {
    const cols = columns();
    expect(resolveDrop(cols, previewMove(cols, 1, 3), 1)).toEqual({
      applicationId: 1,
      stage: "APPLIED",
      position: 2,
    });
    expect(resolveDrop(cols, previewMove(cols, 3, 1), 3)).toEqual({
      applicationId: 3,
      stage: "APPLIED",
      position: 0,
    });
  });

  it("returns the stage and index in another column", () => {
    const cols = columns();
    expect(resolveDrop(cols, previewMove(cols, 2, 5), 2)).toEqual({
      applicationId: 2,
      stage: "INTERVIEW",
      position: 1,
    });
    expect(resolveDrop(cols, previewMove(cols, 2, "INTERVIEW"), 2)).toEqual({
      applicationId: 2,
      stage: "INTERVIEW",
      position: 2,
    });
    expect(resolveDrop(cols, previewMove(cols, 6, "OFFER"), 6)).toEqual({
      applicationId: 6,
      stage: "OFFER",
      position: 0,
    });
  });

  it("returns null for an unknown card", () => {
    expect(resolveDrop(columns(), columns(), 999)).toBeNull();
  });
});

describe("canMoveBetween", () => {
  it("lets anyone move outside HIRED, into it and inside it", () => {
    expect(canMoveBetween("APPLIED", "OFFER", "RECRUITER")).toBe(true);
    expect(canMoveBetween("OFFER", "HIRED", "RECRUITER")).toBe(true);
    expect(canMoveBetween("HIRED", "HIRED", "RECRUITER")).toBe(true);
  });

  it("lets only an ADMIN take a card out of HIRED", () => {
    expect(canMoveBetween("HIRED", "REJECTED", "RECRUITER")).toBe(false);
    expect(canMoveBetween("HIRED", "REJECTED", null)).toBe(false);
    expect(canMoveBetween("HIRED", "REJECTED", "ADMIN")).toBe(true);
  });
});

describe("applyMove", () => {
  /** APPLIED 1,2,3 · INTERVIEW 4,5 · OFFER empty · HIRED 6 · REJECTED empty */
  function board(): Board {
    const cards = (...ids: number[]) => ids.map((id) => buildBoardCard({ id }));
    return buildBoard({
      cards: {
        APPLIED: cards(1, 2, 3),
        INTERVIEW: cards(4, 5),
        HIRED: cards(6),
      },
    });
  }

  function positions(b: Board, stage: Stage) {
    return b.columns
      .find((c) => c.stage === stage)!
      .applications.map((a) => a.position);
  }

  /** Every column holds exactly positions 0..n-1. */
  function expectContiguous(b: Board) {
    for (const column of b.columns) {
      expect(positions(b, column.stage)).toEqual(
        column.applications.map((_, i) => i),
      );
    }
  }

  it.each([
    [0, [2, 4, 5]],
    [1, [4, 2, 5]],
    [2, [4, 5, 2]],
  ])("moves a card to index %i of another column", (position, expected) => {
    const moved = applyMove(board(), 2, "INTERVIEW", position);

    expect(ids(moved.columns, "APPLIED")).toEqual([1, 3]);
    expect(ids(moved.columns, "INTERVIEW")).toEqual(expected);
    expectContiguous(moved);
  });

  it("moves a card into an empty column", () => {
    const moved = applyMove(board(), 6, "OFFER", 0);

    expect(ids(moved.columns, "HIRED")).toEqual([]);
    expect(ids(moved.columns, "OFFER")).toEqual([6]);
    expectContiguous(moved);
  });

  it("reorders downwards and upwards inside a column", () => {
    const down = applyMove(board(), 1, "APPLIED", 2);
    expect(ids(down.columns, "APPLIED")).toEqual([2, 3, 1]);
    expectContiguous(down);

    const up = applyMove(board(), 3, "APPLIED", 0);
    expect(ids(up.columns, "APPLIED")).toEqual([3, 1, 2]);
    expectContiguous(up);
  });

  it("returns the same board for the same stage and position", () => {
    const original = board();
    expect(applyMove(original, 2, "APPLIED", 1)).toBe(original);
  });

  it("returns the same board for an unknown card", () => {
    const original = board();
    expect(applyMove(original, 999, "OFFER", 0)).toBe(original);
  });

  it("keeps the other columns and does not mutate the original", () => {
    const original = board();
    const snapshot = structuredClone(original);

    const moved = applyMove(original, 2, "INTERVIEW", 0);

    expect(original).toEqual(snapshot);
    expect(moved).not.toBe(original);
    const hired = (b: Board) => b.columns.find((c) => c.stage === "HIRED");
    expect(hired(moved)).toBe(hired(original));
  });
});

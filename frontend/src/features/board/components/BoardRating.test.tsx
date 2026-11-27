import type {
  DndContextProps,
  DragEndEvent,
  DragOverEvent,
  DragStartEvent,
  UniqueIdentifier,
} from "@dnd-kit/core";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { toast } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  getJobBoard,
  listHistory,
  listNotes,
  moveApplication,
  rateApplication,
} from "@/features/board/api";
import { BoardCardOverlay } from "@/features/board/components/BoardCard";
import type { Application, Board, BoardCard } from "@/features/board/types";
import { getCandidate } from "@/features/candidates/api";
import { STAGE_LABELS, type Stage } from "@/shared/lib/stages";
import {
  adminUser,
  buildBoard,
  buildBoardCard,
  httpError,
  pageOf,
  recruiterUser,
  renderRoute,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/board/api", () => ({
  getJobBoard: vi.fn(),
  moveApplication: vi.fn(),
  listNotes: vi.fn(),
  createNote: vi.fn(),
  deleteNote: vi.fn(),
  listHistory: vi.fn(),
  rateApplication: vi.fn(),
}));

vi.mock("@/features/candidates/api", () => ({
  getCandidate: vi.fn(),
  listCandidates: vi.fn(),
  createCandidate: vi.fn(),
}));

// Same approach as BoardPage.test: keep the DndContext props to fire drags.
const dnd = vi.hoisted(() => ({ props: null as DndContextProps | null }));
vi.mock("@dnd-kit/core", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@dnd-kit/core")>();
  function RecordingDndContext(props: DndContextProps) {
    dnd.props = props;
    return <actual.DndContext {...props} />;
  }
  return { ...actual, DndContext: RecordingDndContext };
});

const getJobBoardMock = vi.mocked(getJobBoard);
const rateMock = vi.mocked(rateApplication);
const moveMock = vi.mocked(moveApplication);

function card(id: number, fullName: string, rating: number | null = null) {
  return buildBoardCard({
    id,
    rating,
    candidate: { id: id + 100, fullName, email: `c${id}@example.com` },
  });
}

/** APPLIED: Ana (2 stars), Beto · INTERVIEW: Diego */
function sampleBoard(
  overrides: { ana?: Partial<BoardCard>; status?: Board["job"]["status"] } = {},
): Board {
  return buildBoard({
    job: { status: overrides.status ?? "OPEN" },
    cards: {
      APPLIED: [{ ...card(1, "Ana", 2), ...overrides.ana }, card(2, "Beto")],
      INTERVIEW: [card(4, "Diego")],
    },
  });
}

function application(
  rating: number | null,
  stage: Stage = "APPLIED",
): Application {
  return {
    id: 1,
    jobId: 10,
    candidateId: 101,
    stage,
    position: 0,
    rating,
    createdAt: "2026-04-10T10:00:00Z",
    updatedAt: "2026-04-11T10:00:00Z",
  };
}

/** A request that the test answers when it wants. */
function deferred<T>() {
  let resolve: (value: T) => void = () => {};
  let reject: (error: unknown) => void = () => {};
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function column(stage: Stage) {
  return screen.getByRole("region", { name: STAGE_LABELS[stage] });
}

function names(stage: Stage) {
  return within(column(stage))
    .queryAllByRole("link")
    .map((link) => link.textContent);
}

function cardStars(name: string) {
  return screen.getByRole("group", {
    name: new RegExp(`^Calificación de ${name}:`),
  });
}

function star(group: HTMLElement, label: string) {
  return within(group).getByRole("button", { name: label });
}

async function renderBoard(board: Board = sampleBoard(), user = recruiterUser) {
  getJobBoardMock.mockResolvedValue(board);
  signIn(user);
  renderRoute("/jobs/10/board");
  await screen.findByRole("link", { name: "Ana" });
  return userEvent.setup();
}

function dragEvent(active: UniqueIdentifier, over: UniqueIdentifier) {
  const rect = {
    top: 0,
    left: 0,
    width: 100,
    height: 40,
    right: 100,
    bottom: 40,
  };
  return {
    active: {
      id: active,
      data: { current: {} },
      rect: { current: { initial: rect, translated: rect } },
    },
    over: { id: over, rect, disabled: false, data: { current: {} } },
    delta: { x: 0, y: 0 },
    collisions: null,
    activatorEvent: new Event("pointerdown"),
  };
}

function drag(active: UniqueIdentifier, over: UniqueIdentifier) {
  const event = dragEvent(active, over);
  act(() => dnd.props?.onDragStart?.(event as unknown as DragStartEvent));
  act(() => dnd.props?.onDragOver?.(event as unknown as DragOverEvent));
  act(() =>
    dnd.props?.onDragEnd?.(
      dragEvent(active, active) as unknown as DragEndEvent,
    ),
  );
}

describe("Rating applications on the board", () => {
  beforeEach(() => {
    vi.mocked(listNotes).mockResolvedValue(pageOf([]));
    vi.mocked(listHistory).mockResolvedValue(pageOf([]));
    vi.mocked(getCandidate).mockReturnValue(new Promise(() => {}));
  });

  afterEach(() => {
    vi.resetAllMocks();
    dnd.props = null;
    signOut();
    act(() => {
      toast.dismiss();
    });
  });

  it("changes the stars at once on the card and in the panel, and reloads once saved", async () => {
    const request = deferred<Application>();
    rateMock.mockReturnValue(request.promise);
    const user = await renderBoard();
    await user.click(
      screen.getByRole("button", {
        name: "Ver detalle de la postulación de Ana",
      }),
    );
    const panel = await screen.findByRole("dialog");
    const panelStars = within(panel).getByRole("group", {
      name: "Calificación de Ana: 2 de 5 estrellas",
    });

    await user.click(star(panelStars, "Calificar con 4 estrellas"));

    expect(rateMock).toHaveBeenCalledWith(1, { rating: 4 });
    expect(
      within(panel).getByRole("group", {
        name: "Calificación de Ana: 4 de 5 estrellas",
      }),
    ).toBeInTheDocument();
    // The board behind the modal panel is hidden from the accessibility tree.
    expect(
      screen.getAllByRole("group", {
        name: "Calificación de Ana: 4 de 5 estrellas",
        hidden: true,
      }),
    ).toHaveLength(2);
    expect(getJobBoardMock).toHaveBeenCalledTimes(1);

    getJobBoardMock.mockResolvedValue(sampleBoard({ ana: { rating: 4 } }));
    await act(async () => request.resolve(application(4)));

    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    expect(
      within(panel).getByRole("group", { name: /4 de 5 estrellas/ }),
    ).toBeVisible();
  });

  it("persists after a reload and can be removed", async () => {
    rateMock.mockResolvedValue(application(null));
    const user = await renderBoard(sampleBoard({ ana: { rating: 3 } }));
    getJobBoardMock.mockResolvedValue(sampleBoard({ ana: { rating: null } }));

    await user.click(star(cardStars("Ana"), "Quitar calificación"));

    expect(rateMock).toHaveBeenCalledWith(1, { rating: null });
    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: Sin calificación",
    );
    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: Sin calificación",
    );
  });

  it("puts back only the previous rating and shows a toast when saving fails", async () => {
    rateMock.mockRejectedValue(httpError(500));
    const user = await renderBoard();

    await user.click(star(cardStars("Beto"), "Calificar con 5 estrellas"));

    expect(
      await screen.findByText(
        "No se pudo guardar la calificación. Inténtalo de nuevo.",
      ),
    ).toBeVisible();
    expect(cardStars("Beto")).toHaveAccessibleName(
      "Calificación de Beto: Sin calificación",
    );
    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: 2 de 5 estrellas",
    );
  });

  it("explains a missing application and reloads the board", async () => {
    rateMock.mockRejectedValue(httpError(404));
    const user = await renderBoard();
    getJobBoardMock.mockResolvedValue(
      buildBoard({
        cards: { APPLIED: [card(2, "Beto")], INTERVIEW: [card(4, "Diego")] },
      }),
    );

    await user.click(star(cardStars("Ana"), "Calificar con 1 estrella"));

    expect(
      await screen.findByText("La postulación ya no existe."),
    ).toBeVisible();
    await waitFor(() =>
      expect(screen.queryByRole("link", { name: "Ana" })).toBeNull(),
    );
  });

  it("sends quick changes in order and ends with the server value", async () => {
    const first = deferred<Application>();
    const second = deferred<Application>();
    rateMock
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const user = await renderBoard();

    await user.click(star(cardStars("Ana"), "Calificar con 3 estrellas"));
    await user.click(star(cardStars("Ana"), "Calificar con 5 estrellas"));

    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: 5 de 5 estrellas",
    );
    // The second one waits for the first.
    expect(rateMock).toHaveBeenCalledTimes(1);
    expect(rateMock).toHaveBeenLastCalledWith(1, { rating: 3 });

    await act(async () => first.resolve(application(3)));
    await waitFor(() => expect(rateMock).toHaveBeenCalledTimes(2));
    expect(rateMock).toHaveBeenLastCalledWith(1, { rating: 5 });
    // The first answer does not overwrite the newer value on screen.
    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: 5 de 5 estrellas",
    );
    expect(getJobBoardMock).toHaveBeenCalledTimes(1);

    getJobBoardMock.mockResolvedValue(sampleBoard({ ana: { rating: 5 } }));
    await act(async () => second.resolve(application(5)));

    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: 5 de 5 estrellas",
    );
  });

  it("does not undo a pending move and reloads only after both finish", async () => {
    const moveRequest = deferred<Application>();
    moveMock.mockReturnValue(moveRequest.promise);
    rateMock.mockResolvedValue(application(4, "INTERVIEW"));
    const user = await renderBoard();

    drag(1, "INTERVIEW");
    expect(names("INTERVIEW")).toEqual(["Diego", "Ana"]);

    await user.click(star(cardStars("Ana"), "Calificar con 4 estrellas"));
    await waitFor(() =>
      expect(rateMock).toHaveBeenCalledWith(1, { rating: 4 }),
    );

    expect(names("INTERVIEW")).toEqual(["Diego", "Ana"]);
    expect(names("APPLIED")).toEqual(["Beto"]);
    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: 4 de 5 estrellas",
    );
    // The move is still pending, so the board is not reloaded yet.
    expect(getJobBoardMock).toHaveBeenCalledTimes(1);

    getJobBoardMock.mockResolvedValue(
      buildBoard({
        cards: {
          APPLIED: [card(2, "Beto")],
          INTERVIEW: [card(4, "Diego"), card(1, "Ana", 4)],
        },
      }),
    );
    await act(async () => moveRequest.resolve(application(4, "INTERVIEW")));

    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    expect(names("INTERVIEW")).toEqual(["Diego", "Ana"]);
    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: 4 de 5 estrellas",
    );
  });

  it("corrects a star brought back by a failed move with the final reload", async () => {
    const moveRequest = deferred<Application>();
    moveMock.mockReturnValue(moveRequest.promise);
    rateMock.mockResolvedValue(application(5));
    const user = await renderBoard();

    drag(1, "INTERVIEW");
    await user.click(star(cardStars("Ana"), "Calificar con 5 estrellas"));
    await waitFor(() => expect(rateMock).toHaveBeenCalled());
    getJobBoardMock.mockResolvedValue(sampleBoard({ ana: { rating: 5 } }));

    await act(async () => moveRequest.reject(httpError(500)));

    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    expect(names("APPLIED")).toEqual(["Ana", "Beto"]);
    await waitFor(() =>
      expect(cardStars("Ana")).toHaveAccessibleName(
        "Calificación de Ana: 5 de 5 estrellas",
      ),
    );
  });

  it("keeps a pending move on screen when the rating fails", async () => {
    const moveRequest = deferred<Application>();
    moveMock.mockReturnValue(moveRequest.promise);
    rateMock.mockRejectedValue(httpError(500));
    const user = await renderBoard();

    drag(1, "INTERVIEW");
    await user.click(star(cardStars("Ana"), "Calificar con 5 estrellas"));

    expect(
      await screen.findByText(
        "No se pudo guardar la calificación. Inténtalo de nuevo.",
      ),
    ).toBeVisible();
    expect(names("INTERVIEW")).toEqual(["Diego", "Ana"]);
    expect(cardStars("Ana")).toHaveAccessibleName(
      "Calificación de Ana: 2 de 5 estrellas",
    );
  });

  it("rates on a closed job, from the card and the panel, while moving stays disabled", async () => {
    rateMock.mockResolvedValue(application(3));
    const user = await renderBoard(
      sampleBoard({ status: "CLOSED" }),
      adminUser,
    );

    expect(
      screen.getByRole("button", { name: "Mover postulación de Ana" }),
    ).toBeDisabled();
    await user.click(star(cardStars("Ana"), "Calificar con 3 estrellas"));
    expect(rateMock).toHaveBeenCalledWith(1, { rating: 3 });

    await user.click(
      screen.getByRole("button", {
        name: "Ver detalle de la postulación de Beto",
      }),
    );
    const panel = await screen.findByRole("dialog");
    await user.click(
      star(
        within(panel).getByRole("group", { name: /^Calificación de Beto/ }),
        "Calificar con 1 estrella",
      ),
    );
    expect(rateMock).toHaveBeenLastCalledWith(2, { rating: 1 });
  });

  it("does not open the panel or start a drag from the stars", async () => {
    rateMock.mockResolvedValue(application(4));
    const user = await renderBoard();
    await user.click(star(cardStars("Ana"), "Calificar con 4 estrellas"));
    await user.keyboard(" ");

    expect(screen.queryByRole("dialog")).toBeNull();
    // A pick-up would render the drag copy and announce it.
    expect(
      screen.queryByRole("img", { name: /^Calificación de Ana/, hidden: true }),
    ).toBeNull();
    expect(screen.queryByText(/Levantaste/)).toBeNull();
    expect(moveMock).not.toHaveBeenCalled();
  });
});

describe("Rating in the drag copy", () => {
  it("shows the stars read only", () => {
    render(
      <MemoryRouter>
        <BoardCardOverlay card={card(1, "Ana", 3)} />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("img", {
        name: "Calificación de Ana: 3 de 5 estrellas",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Calificar con/ })).toBeNull();
  });
});

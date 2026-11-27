import type {
  DndContextProps,
  DragEndEvent,
  DragOverEvent,
  DragStartEvent,
  UniqueIdentifier,
} from "@dnd-kit/core";
import {
  act,
  fireEvent,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { AxiosError } from "axios";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getJobBoard, moveApplication } from "@/features/board/api";
import type { Application, Board } from "@/features/board/types";
import { getCandidate } from "@/features/candidates/api";
import { STAGE_LABELS, STAGES } from "@/shared/lib/stages";
import {
  adminUser,
  buildBoard,
  buildBoardCard,
  httpError,
  recruiterUser,
  renderRoute,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/board/api", () => ({
  getJobBoard: vi.fn(),
  moveApplication: vi.fn(),
}));

vi.mock("@/features/candidates/api", () => ({
  getCandidate: vi.fn(),
  listCandidates: vi.fn(),
  createCandidate: vi.fn(),
}));

// jsdom has no layout, so pointer drags cannot be simulated. The real DndContext
// still renders; the test keeps its latest props to fire the drag callbacks.
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
const moveApplicationMock = vi.mocked(moveApplication);

const card = (id: number, fullName: string) =>
  buildBoardCard({
    id,
    candidate: { id: id + 100, fullName, email: `c${id}@example.com` },
  });

/** APPLIED: Ana, Beto, Carla · INTERVIEW: Diego · OFFER empty · HIRED: Elena · REJECTED empty */
function sampleBoard(job: Partial<Board["job"]> = {}): Board {
  return buildBoard({
    job: { id: 10, title: "Desarrollador backend", ...job },
    cards: {
      APPLIED: [card(1, "Ana"), card(2, "Beto"), card(3, "Carla")],
      INTERVIEW: [card(4, "Diego")],
      HIRED: [card(5, "Elena")],
    },
  });
}

function renderBoard(board: Board = sampleBoard(), user = recruiterUser) {
  getJobBoardMock.mockResolvedValue(board);
  signIn(user);
  return renderRoute("/jobs/10/board");
}

function column(stage: keyof typeof STAGE_LABELS) {
  return screen.getByRole("region", { name: STAGE_LABELS[stage] });
}

function names(stage: keyof typeof STAGE_LABELS) {
  return within(column(stage))
    .queryAllByRole("link")
    .map((link) => link.textContent);
}

function dragEvent(active: UniqueIdentifier, over: UniqueIdentifier | null) {
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
    over:
      over === null
        ? null
        : { id: over, rect, disabled: false, data: { current: {} } },
    delta: { x: 0, y: 0 },
    collisions: null,
    activatorEvent: new Event("pointerdown"),
  };
}

/**
 * Picks up `active`, passes over `over` and drops it. After crossing into
 * another column the card itself sits under the pointer, so dnd-kit reports
 * the drop over `dropOver` (the card) rather than the card it was placed before.
 */
function drag(
  active: UniqueIdentifier,
  over: UniqueIdentifier | null,
  dropOver: UniqueIdentifier | null = over,
) {
  const event = dragEvent(active, over);
  act(() => dnd.props?.onDragStart?.(event as unknown as DragStartEvent));
  act(() => dnd.props?.onDragOver?.(event as unknown as DragOverEvent));
  const end = dragEvent(active, dropOver);
  act(() => dnd.props?.onDragEnd?.(end as unknown as DragEndEvent));
}

/** A move request that the test answers when it wants. */
function deferred() {
  let resolve: () => void = () => {};
  let reject: (error: unknown) => void = () => {};
  const promise = new Promise<Application>((res, rej) => {
    resolve = () => res(buildApplication());
    reject = rej;
  });
  return { promise, resolve, reject };
}

function buildApplication(): Application {
  return {
    id: 1,
    jobId: 10,
    candidateId: 101,
    stage: "APPLIED",
    position: 0,
    rating: null,
    createdAt: "2026-04-10T10:00:00Z",
    updatedAt: "2026-04-10T10:00:00Z",
  };
}

describe("BoardPage", () => {
  afterEach(() => {
    vi.resetAllMocks();
    dnd.props = null;
    signOut();
    // Toasts live in a module-level store shared by every test.
    act(() => {
      toast.dismiss();
    });
  });

  // ---- states ----

  it("shows a loading state", async () => {
    getJobBoardMock.mockReturnValue(new Promise(() => {}));
    signIn();
    renderRoute("/jobs/10/board");

    expect(await screen.findByText("Cargando tablero…")).toHaveAttribute(
      "role",
      "status",
    );
    expect(getJobBoardMock).toHaveBeenCalledWith(10);
  });

  it("shows an error with retry", async () => {
    getJobBoardMock
      .mockRejectedValueOnce(httpError(500))
      .mockResolvedValueOnce(sampleBoard());
    signIn();
    renderRoute("/jobs/10/board");

    expect(
      await screen.findByText("No se pudo cargar el tablero."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(
      await screen.findByRole("heading", { name: "Desarrollador backend" }),
    ).toBeInTheDocument();
    expect(getJobBoardMock).toHaveBeenCalledTimes(2);
  });

  it("shows a not found message for a missing job", async () => {
    getJobBoardMock.mockRejectedValue(httpError(404));
    signIn();
    renderRoute("/jobs/10/board");

    expect(
      await screen.findByRole("heading", { name: "La vacante no existe" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Volver a vacantes" }),
    ).toHaveAttribute("href", "/jobs");
  });

  it("shows a not found message for an invalid id without calling the API", async () => {
    signIn();
    renderRoute("/jobs/abc/board");

    expect(
      await screen.findByRole("heading", { name: "La vacante no existe" }),
    ).toBeInTheDocument();
    expect(getJobBoardMock).not.toHaveBeenCalled();
  });

  // ---- board ----

  it("shows the header and the five columns in order with their counts", async () => {
    renderBoard();

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Desarrollador backend",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("Abierta")).toBeInTheDocument();
    expect(screen.getByText("5 postulaciones")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Volver a vacantes" }),
    ).toHaveAttribute("href", "/jobs");

    const regions = screen
      .getAllByRole("region")
      .filter((region) => region.hasAttribute("data-stage"));
    expect(regions.map((r) => r.getAttribute("data-stage"))).toEqual(STAGES);
    expect(column("APPLIED")).toHaveTextContent("3 postulaciones en Postulado");
    expect(column("INTERVIEW")).toHaveTextContent(
      "1 postulación en Entrevista",
    );
    expect(column("OFFER")).toHaveTextContent("0 postulaciones en Oferta");
    expect(column("APPLIED")).toHaveClass("border-t-stage-applied");
    expect(names("APPLIED")).toEqual(["Ana", "Beto", "Carla"]);
    expect(names("HIRED")).toEqual(["Elena"]);
  });

  it("shows empty columns that still accept cards", async () => {
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    expect(
      within(column("OFFER")).getByText("Sin postulaciones"),
    ).toBeVisible();
    expect(
      within(column("REJECTED")).getByText("Sin postulaciones"),
    ).toBeVisible();
    expect(
      within(column("APPLIED")).queryByText("Sin postulaciones"),
    ).toBeNull();
  });

  it("makes a closed job read only", async () => {
    renderBoard(sampleBoard({ status: "CLOSED" }));

    expect(
      await screen.findByText(
        "Vacante cerrada: no se pueden mover ni agregar postulaciones. Las notas y la calificación siguen disponibles.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Cerrada")).toBeInTheDocument();
    for (const handle of screen.getAllByRole("button", {
      name: /^Mover postulación/,
    })) {
      expect(handle).toBeDisabled();
    }
  });

  // ---- moving ----

  it("moves a card to another column at the target index", async () => {
    moveApplicationMock.mockResolvedValue({} as never);
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(2, 4, 2);

    await waitFor(() =>
      expect(moveApplicationMock).toHaveBeenCalledWith(2, {
        stage: "INTERVIEW",
        position: 0,
      }),
    );
    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
  });

  it("moves a card to the end of an empty column", async () => {
    moveApplicationMock.mockResolvedValue({} as never);
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(1, "OFFER");

    await waitFor(() =>
      expect(moveApplicationMock).toHaveBeenCalledWith(1, {
        stage: "OFFER",
        position: 0,
      }),
    );
  });

  it("reorders inside a column", async () => {
    moveApplicationMock.mockResolvedValue({} as never);
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(1, 3);

    await waitFor(() =>
      expect(moveApplicationMock).toHaveBeenCalledWith(1, {
        stage: "APPLIED",
        position: 2,
      }),
    );
  });

  it("does not call the API when the card is dropped where it was", async () => {
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(2, 2);
    drag(2, "APPLIED");
    drag(2, null);

    expect(moveApplicationMock).not.toHaveBeenCalled();
    expect(names("APPLIED")).toEqual(["Ana", "Beto", "Carla"]);
  });

  it("shows the move at once, without a jump, and reloads once when saved", async () => {
    const save = deferred();
    moveApplicationMock.mockReturnValue(save.promise);
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });
    // Records any frame in which Carla goes back into APPLIED after the drop.
    const observer = new MutationObserver(() => {});
    observer.observe(column("APPLIED"), { childList: true, subtree: true });

    drag(3, 4, 3);

    // No waitFor: the new order is there right after the drop.
    expect(names("INTERVIEW")).toEqual(["Carla", "Diego"]);
    expect(names("APPLIED")).toEqual(["Ana", "Beto"]);
    const reinserted = observer
      .takeRecords()
      .flatMap((record) => [...record.addedNodes])
      .some((node) => node.textContent?.includes("Carla"));
    observer.disconnect();
    expect(reinserted).toBe(false);
    expect(
      screen.getByRole("link", { name: "Carla" }).closest("li"),
    ).not.toHaveAttribute("aria-busy");
    for (const handle of screen.getAllByRole("button", {
      name: /^Mover postulación/,
    })) {
      expect(handle).toBeEnabled();
    }
    expect(getJobBoardMock).toHaveBeenCalledTimes(1);

    getJobBoardMock.mockResolvedValue(
      buildBoard({
        cards: {
          APPLIED: [card(1, "Ana"), card(2, "Beto")],
          INTERVIEW: [card(3, "Carla"), card(4, "Diego")],
          HIRED: [card(5, "Elena")],
        },
      }),
    );
    await act(async () => save.resolve());

    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    expect(names("INTERVIEW")).toEqual(["Carla", "Diego"]);
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));
    expect(getJobBoardMock).toHaveBeenCalledTimes(2);
  });

  it.each([
    [409, "La vacante está cerrada y no admite cambios."],
    [403, "Solo un administrador puede sacar una postulación de Contratado."],
    [404, "La postulación ya no existe."],
    [400, "No se pudo mover la postulación. Inténtalo de nuevo."],
    [500, "No se pudo mover la postulación. Inténtalo de nuevo."],
  ])(
    "puts the card back and shows a toast for a %s",
    async (status, message) => {
      const save = deferred();
      moveApplicationMock.mockReturnValue(save.promise);
      renderBoard();
      await screen.findByRole("heading", { name: "Desarrollador backend" });
      // The reload never answers, so what is shown afterwards is the rollback.
      getJobBoardMock.mockReturnValue(new Promise(() => {}));

      drag(1, "REJECTED");
      expect(names("REJECTED")).toEqual(["Ana"]);

      await act(async () => save.reject(httpError(status)));

      expect(await screen.findByText(message)).toBeInTheDocument();
      expect(names("APPLIED")).toEqual(["Ana", "Beto", "Carla"]);
      expect(names("REJECTED")).toEqual([]);
      await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    },
  );

  it("puts the card back after a network error", async () => {
    const save = deferred();
    moveApplicationMock.mockReturnValue(save.promise);
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });
    getJobBoardMock.mockReturnValue(new Promise(() => {}));

    drag(2, 4, 2);
    await act(async () => save.reject(new AxiosError("Network Error")));

    expect(
      await screen.findByText(
        "No se pudo mover la postulación. Inténtalo de nuevo.",
      ),
    ).toBeInTheDocument();
    expect(names("APPLIED")).toEqual(["Ana", "Beto", "Carla"]);
    expect(names("INTERVIEW")).toEqual(["Diego"]);
  });

  it("reloads a missing card's board even while another move is pending", async () => {
    const first = deferred();
    const second = deferred();
    moveApplicationMock
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(1, "OFFER");
    drag(4, "REJECTED");
    await act(async () => first.reject(httpError(404)));

    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    await act(async () => second.resolve());
  });

  it("allows several moves in a row and reloads once after the last", async () => {
    const first = deferred();
    const second = deferred();
    moveApplicationMock
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(1, "OFFER");
    drag(2, "OFFER");

    expect(names("OFFER")).toEqual(["Ana", "Beto"]);
    expect(names("APPLIED")).toEqual(["Carla"]);
    // The request itself starts a microtask after the cache update.
    await waitFor(() => expect(moveApplicationMock).toHaveBeenCalledTimes(2));
    expect(moveApplicationMock).toHaveBeenNthCalledWith(1, 1, {
      stage: "OFFER",
      position: 0,
    });
    expect(moveApplicationMock).toHaveBeenNthCalledWith(2, 2, {
      stage: "OFFER",
      position: 1,
    });

    await act(async () => first.resolve());
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));
    // The second move is still saving, so the board is not reloaded yet.
    expect(getJobBoardMock).toHaveBeenCalledTimes(1);
    expect(names("OFFER")).toEqual(["Ana", "Beto"]);

    await act(async () => second.resolve());
    await waitFor(() => expect(getJobBoardMock).toHaveBeenCalledTimes(2));
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));
    expect(getJobBoardMock).toHaveBeenCalledTimes(2);
  });

  // ---- roles ----

  it("keeps a RECRUITER from taking a card out of HIRED", async () => {
    renderBoard(sampleBoard(), recruiterUser);
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(5, "OFFER");

    expect(
      await screen.findByText(
        "Solo un administrador puede sacar una postulación de Contratado.",
      ),
    ).toBeInTheDocument();
    expect(moveApplicationMock).not.toHaveBeenCalled();
    expect(names("HIRED")).toEqual(["Elena"]);
    expect(names("OFFER")).toEqual([]);
  });

  it("does not preview a RECRUITER's card leaving HIRED", async () => {
    renderBoard(sampleBoard(), recruiterUser);
    await screen.findByRole("heading", { name: "Desarrollador backend" });
    const event = dragEvent(5, "OFFER");

    act(() => dnd.props?.onDragStart?.(event as unknown as DragStartEvent));
    act(() => dnd.props?.onDragOver?.(event as unknown as DragOverEvent));

    expect(names("HIRED")).toEqual(["Elena"]);
    expect(column("OFFER")).not.toHaveClass("ring-stage-offer");
    act(() => dnd.props?.onDragCancel?.(event as never));
  });

  it("lets a RECRUITER move a card into HIRED", async () => {
    moveApplicationMock.mockResolvedValue({} as never);
    renderBoard(sampleBoard(), recruiterUser);
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(4, 5, 4);

    await waitFor(() =>
      expect(moveApplicationMock).toHaveBeenCalledWith(4, {
        stage: "HIRED",
        position: 0,
      }),
    );
  });

  it("lets an ADMIN take a card out of HIRED", async () => {
    moveApplicationMock.mockResolvedValue({} as never);
    renderBoard(sampleBoard(), adminUser);
    await screen.findByRole("heading", { name: "Desarrollador backend" });

    drag(5, "OFFER");

    await waitFor(() =>
      expect(moveApplicationMock).toHaveBeenCalledWith(5, {
        stage: "OFFER",
        position: 0,
      }),
    );
  });

  it("highlights the column a card is dragged over and previews it there", async () => {
    renderBoard();
    await screen.findByRole("heading", { name: "Desarrollador backend" });
    const event = dragEvent(1, "OFFER");

    act(() => dnd.props?.onDragStart?.(event as unknown as DragStartEvent));
    act(() => dnd.props?.onDragOver?.(event as unknown as DragOverEvent));

    expect(column("OFFER")).toHaveClass(
      "bg-stage-offer-soft",
      "ring-stage-offer",
    );
    expect(names("OFFER")).toContain("Ana");

    act(() => dnd.props?.onDragCancel?.(event as never));
    expect(column("OFFER")).not.toHaveClass("ring-stage-offer");
    expect(names("APPLIED")).toEqual(["Ana", "Beto", "Carla"]);
  });

  // ---- keyboard and screen readers ----

  it("picks a card up with the keyboard and announces it in Spanish", async () => {
    renderBoard();
    const handle = await screen.findByRole("button", {
      name: "Mover postulación de Beto",
    });
    expect(
      screen.getByText(/presiona Espacio o Intro para levantarla/),
    ).toBeInTheDocument();

    handle.focus();
    fireEvent.keyDown(handle, { code: "Space", key: " " });

    // Without layout every rect is empty, so arrow keys cannot be checked here;
    // the live region proves the keyboard sensor started the drag.
    await waitFor(() =>
      expect(document.querySelector('[id^="DndLiveRegion"]')).toHaveTextContent(
        /La postulación de Beto/,
      ),
    );

    fireEvent.keyDown(handle, { code: "Escape", key: "Escape" });

    expect(
      await screen.findByText(
        "Movimiento cancelado. La postulación de Beto volvió a Postulado.",
      ),
    ).toBeInTheDocument();
    expect(moveApplicationMock).not.toHaveBeenCalled();
  });

  it("announces each drag step in Spanish with names and stages", async () => {
    renderBoard(sampleBoard(), recruiterUser);
    await screen.findByRole("heading", { name: "Desarrollador backend" });
    const say = () => dnd.props!.accessibility!.announcements!;
    const event = (active: UniqueIdentifier, over: UniqueIdentifier | null) =>
      dragEvent(active, over) as never;

    expect(say().onDragStart(event(2, null))).toBe(
      "Levantaste la postulación de Beto. Está en Postulado, posición 2 de 3.",
    );
    expect(say().onDragOver(event(2, 2))).toBeUndefined();
    expect(say().onDragOver(event(2, 4))).toBe(
      "La postulación de Beto está sobre Entrevista, posición 1.",
    );
    expect(say().onDragOver(event(2, "OFFER"))).toBe(
      "La postulación de Beto está sobre Oferta, posición 1.",
    );
    expect(say().onDragOver(event(2, null))).toBe(
      "La postulación de Beto no está sobre ninguna columna.",
    );
    expect(say().onDragOver(event(5, "OFFER"))).toMatch(
      /^Solo un administrador puede sacar una postulación de Contratado\./,
    );
    expect(say().onDragEnd(event(2, "OFFER"))).toBe(
      "Soltaste la postulación de Beto en Oferta.",
    );
    expect(say().onDragEnd(event(2, null))).toBe(
      "La postulación de Beto volvió a su lugar.",
    );
    expect(say().onDragCancel(event(2, null))).toBe(
      "Movimiento cancelado. La postulación de Beto volvió a Postulado.",
    );
  });

  it("keeps candidate links clickable", async () => {
    vi.mocked(getCandidate).mockReturnValue(new Promise(() => {}));
    const router = renderBoard();
    const link = await screen.findByRole("link", { name: "Ana" });

    expect(link).toHaveAttribute("href", "/candidates/101");
    fireEvent.click(link);

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/candidates/101"),
    );
  });
});

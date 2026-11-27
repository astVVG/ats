import type {
  DndContextProps,
  DragEndEvent,
  DragOverEvent,
  DragStartEvent,
  UniqueIdentifier,
} from "@dnd-kit/core";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Providers } from "@/app/providers";
import {
  getJobBoard,
  listHistory,
  listNotes,
  moveApplication,
} from "@/features/board/api";
import { HistorySection } from "@/features/board/components/HistorySection";
import type { Application, StageHistoryEntry } from "@/features/board/types";
import { getCandidate } from "@/features/candidates/api";
import {
  adminUser,
  buildBoard,
  buildBoardCard,
  buildHistoryEntry,
  createTestQueryClient,
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

const listHistoryMock = vi.mocked(listHistory);

const created = buildHistoryEntry({
  id: 1,
  fromStage: null,
  toStage: "APPLIED",
  movedBy: { id: recruiterUser.id, name: recruiterUser.name },
  movedAt: "2026-04-10T10:00:00Z",
});
const toInterview = buildHistoryEntry({
  id: 2,
  fromStage: "APPLIED",
  toStage: "INTERVIEW",
  movedBy: { id: adminUser.id, name: adminUser.name },
  movedAt: "2026-04-11T16:45:00Z",
});

const format = (iso: string) =>
  new Intl.DateTimeFormat("es-MX", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(iso));

function renderHistory() {
  const user = userEvent.setup();
  render(
    <Providers queryClient={createTestQueryClient()}>
      <HistorySection applicationId={5} />
    </Providers>,
  );
  return user;
}

function timeline() {
  return screen.getByRole("list", { name: "Historial de etapas" });
}

describe("HistorySection", () => {
  beforeEach(() => {
    signIn();
  });

  afterEach(() => {
    vi.resetAllMocks();
    dnd.props = null;
    signOut();
  });

  it("shows a loading state", () => {
    listHistoryMock.mockReturnValue(new Promise(() => {}));
    renderHistory();

    expect(screen.getByText("Cargando historial…")).toBeInTheDocument();
  });

  it("shows an error with retry", async () => {
    listHistoryMock.mockRejectedValueOnce(httpError(500));
    listHistoryMock.mockResolvedValue(pageOf([created]));
    const user = renderHistory();

    expect(
      await screen.findByText("No se pudo cargar el historial."),
    ).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(await screen.findByText("Postulación creada")).toBeVisible();
  });

  it("shows an empty state", async () => {
    listHistoryMock.mockResolvedValue(pageOf([]));
    renderHistory();

    expect(
      await screen.findByText("Sin movimientos registrados."),
    ).toBeVisible();
  });

  it("shows origin, destination, user and date, newest first", async () => {
    listHistoryMock.mockResolvedValue(pageOf([toInterview, created]));
    renderHistory();

    await screen.findByText("Postulación creada");
    const [latest, first] = within(timeline()).getAllByRole("listitem");

    expect(within(latest).getByText("Postulado")).toBeInTheDocument();
    expect(within(latest).getByText("Entrevista")).toBeInTheDocument();
    expect(latest).toHaveTextContent(/Postulado\s*a\s*Entrevista/);
    expect(latest).toHaveTextContent(`Por ${adminUser.name}`);
    expect(
      within(latest).getByText(format(toInterview.movedAt)),
    ).toHaveAttribute("datetime", toInterview.movedAt);

    expect(within(first).getByText("Postulación creada")).toBeInTheDocument();
    expect(within(first).getByText("Postulado")).toBeInTheDocument();
    expect(first).toHaveTextContent(`Por ${recruiterUser.name}`);
    expect(listHistoryMock).toHaveBeenCalledWith(5, 0);
  });

  it("paginates 20 at a time", async () => {
    listHistoryMock.mockImplementation((_id, number) =>
      Promise.resolve(
        pageOf(number === 0 ? [toInterview] : [created], number, 2),
      ),
    );
    const user = renderHistory();

    expect(await screen.findByText("Página 1 de 2")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));

    expect(await screen.findByText("Postulación creada")).toBeVisible();
    expect(listHistoryMock).toHaveBeenLastCalledWith(5, 1);
  });
});

// ---- in the board ----

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

describe("HistorySection in the application panel", () => {
  const ana = buildBoardCard({
    id: 1,
    candidate: { id: 101, fullName: "Ana López", email: "ana@example.com" },
  });

  beforeEach(() => {
    signIn();
    vi.mocked(listNotes).mockResolvedValue(pageOf([]));
    vi.mocked(getCandidate).mockReturnValue(new Promise(() => {}));
  });

  afterEach(() => {
    vi.resetAllMocks();
    dnd.props = null;
    signOut();
  });

  async function openAna() {
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", {
        name: "Ver detalle de la postulación de Ana López",
      }),
    );
    return { user, panel: await screen.findByRole("dialog") };
  }

  it("asks the server again every time the panel opens", async () => {
    vi.mocked(getJobBoard).mockResolvedValue(
      buildBoard({ cards: { APPLIED: [ana] } }),
    );
    listHistoryMock.mockResolvedValue(pageOf([created]));
    renderRoute("/jobs/10/board");

    const { user } = await openAna();
    await screen.findByText("Postulación creada");
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    listHistoryMock.mockResolvedValue(pageOf([toInterview, created]));
    await openAna();

    expect(
      await screen.findByText(`Por ${adminUser.name}`, { exact: false }),
    ).toBeVisible();
    expect(listHistoryMock).toHaveBeenCalledTimes(2);
  });

  it("reloads after a move of that application and shows the new stage", async () => {
    vi.mocked(getJobBoard).mockResolvedValue(
      buildBoard({ cards: { APPLIED: [ana] } }),
    );
    listHistoryMock.mockResolvedValue(pageOf([created]));
    let finishMove: () => void = () => {};
    vi.mocked(moveApplication).mockReturnValue(
      new Promise<Application>((resolve) => {
        finishMove = () => resolve({} as Application);
      }),
    );
    renderRoute("/jobs/10/board");
    const { panel } = await openAna();
    await within(panel).findByText("Postulación creada");
    expect(within(panel).getAllByText("Postulado").length).toBeGreaterThan(0);

    drag(ana.id, "INTERVIEW");

    // The stage comes from the cached board, so it changes before the save ends.
    await waitFor(() =>
      expect(
        within(
          within(panel).getByRole("region", { name: "Candidato" }),
        ).getByText("Entrevista"),
      ).toBeInTheDocument(),
    );
    const history: StageHistoryEntry[] = [
      buildHistoryEntry({
        id: 3,
        fromStage: "APPLIED",
        toStage: "INTERVIEW",
        movedBy: { id: recruiterUser.id, name: recruiterUser.name },
      }),
      created,
    ];
    listHistoryMock.mockResolvedValue(pageOf(history));
    vi.mocked(getJobBoard).mockResolvedValue(
      buildBoard({ cards: { INTERVIEW: [ana] } }),
    );
    await act(async () => finishMove());

    await waitFor(() =>
      expect(within(timeline()).getAllByRole("listitem")).toHaveLength(2),
    );
    expect(vi.mocked(moveApplication)).toHaveBeenCalledWith(ana.id, {
      stage: "INTERVIEW",
      position: 0,
    });
  });
});

import {
  act,
  fireEvent,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getCandidate, listCandidates } from "@/features/candidates/api";
import {
  buildCandidate,
  httpError,
  pageOf,
  renderRoute,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/candidates/api", () => ({
  listCandidates: vi.fn(),
  getCandidate: vi.fn(),
  createCandidate: vi.fn(),
  updateCandidate: vi.fn(),
  uploadResume: vi.fn(),
  downloadResume: vi.fn(),
}));

const listCandidatesMock = vi.mocked(listCandidates);

const laura = buildCandidate({ id: 20, hasResume: true });
const ana = buildCandidate({
  id: 21,
  fullName: "Ana Ruiz",
  email: "ana@example.com",
  phone: null,
  source: null,
});

function renderCandidates() {
  signIn();
  return renderRoute("/candidates");
}

describe("CandidatesPage", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.resetAllMocks();
    signOut();
  });

  it("shows a loading state", async () => {
    listCandidatesMock.mockReturnValue(new Promise(() => {}));
    renderCandidates();

    expect(await screen.findByText("Cargando candidatos…")).toHaveAttribute(
      "role",
      "status",
    );
  });

  it("shows an error with retry", async () => {
    listCandidatesMock
      .mockRejectedValueOnce(httpError(500))
      .mockResolvedValueOnce(pageOf([laura]));
    renderCandidates();

    expect(
      await screen.findByText("No se pudieron cargar los candidatos."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(await screen.findByText("Laura Gómez")).toBeInTheDocument();
  });

  it("shows an empty state when there are no candidates", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([]));
    renderCandidates();

    expect(
      await screen.findByText("Aún no hay candidatos. Registra el primero."),
    ).toBeInTheDocument();
  });

  it("shows a different empty state when the search matches nothing", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([]));
    const user = userEvent.setup();
    renderCandidates();
    await screen.findByText("Aún no hay candidatos. Registra el primero.");

    await user.type(screen.getByLabelText("Buscar"), "zzz");

    expect(
      await screen.findByText(
        "No hay candidatos que coincidan con la búsqueda.",
      ),
    ).toBeInTheDocument();
  });

  it("lists candidates with a link to the detail and the CV badge", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura, ana]));
    renderCandidates();

    const rows = await screen.findAllByRole("row");
    expect(rows).toHaveLength(3);
    const first = within(rows[1]);
    expect(first.getByRole("link", { name: "Laura Gómez" })).toHaveAttribute(
      "href",
      "/candidates/20",
    );
    expect(first.getByText("laura@example.com")).toBeInTheDocument();
    expect(first.getByText("+52 777 123 4567")).toBeInTheDocument();
    expect(first.getByText("LinkedIn")).toBeInTheDocument();
    expect(first.getByText("Con CV")).toBeInTheDocument();
    const second = within(rows[2]);
    expect(second.getByText("Sin CV")).toBeInTheDocument();
    expect(second.getAllByText("—")).toHaveLength(2);
    expect(listCandidatesMock).toHaveBeenCalledWith({ page: 0, size: 20 });
  });

  it("shows detail and edit actions on every row", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura, ana]));
    renderCandidates();

    const rows = await screen.findAllByRole("row");
    expect(
      within(rows[0]).getByRole("columnheader", { name: "Acciones" }),
    ).toBeInTheDocument();
    for (const [row, candidate] of [
      [rows[1], laura],
      [rows[2], ana],
    ] as const) {
      const cells = within(row);
      expect(
        cells.getByRole("link", {
          name: `Ver detalle de ${candidate.fullName}`,
        }),
      ).toHaveAttribute("href", `/candidates/${candidate.id}`);
      expect(
        cells.getByRole("link", { name: `Editar a ${candidate.fullName}` }),
      ).toHaveAttribute("href", `/candidates/${candidate.id}/edit`);
    }
  });

  it.each([
    ["Ver detalle de Laura Gómez", "/candidates/20"],
    ["Editar a Laura Gómez", "/candidates/20/edit"],
  ])("navigates with the %s action", async (label, path) => {
    listCandidatesMock.mockResolvedValue(pageOf([laura]));
    vi.mocked(getCandidate).mockResolvedValue(laura);
    const router = renderCandidates();

    await userEvent.click(await screen.findByRole("link", { name: label }));

    await waitFor(() => expect(router.state.location.pathname).toBe(path));
    expect(
      await screen.findByRole("heading", {
        name: path.endsWith("/edit") ? "Editar candidato" : "Laura Gómez",
      }),
    ).toBeInTheDocument();
  });

  it("sends a single request once typing stops", async () => {
    listCandidatesMock.mockImplementation(async ({ q }) =>
      pageOf(q === "ana" ? [ana] : [laura]),
    );
    renderCandidates();
    await screen.findByText("Laura Gómez");
    expect(listCandidatesMock).toHaveBeenCalledTimes(1);

    // Only the timers the debounce uses are faked; keystrokes arrive 100 ms apart.
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    const search = screen.getByLabelText("Buscar");
    for (const value of ["a", "an", "ana"]) {
      fireEvent.change(search, { target: { value } });
      await act(() => vi.advanceTimersByTimeAsync(100));
    }
    await act(() => vi.advanceTimersByTimeAsync(199));
    expect(listCandidatesMock).toHaveBeenCalledTimes(1);

    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(listCandidatesMock).toHaveBeenCalledTimes(2);
    expect(listCandidatesMock).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      q: "ana",
    });
    await act(() => vi.advanceTimersByTimeAsync(50));
    vi.useRealTimers();

    expect(await screen.findByText("Ana Ruiz")).toBeInTheDocument();
    expect(listCandidatesMock).toHaveBeenCalledTimes(2);
  });

  it("shows one-based pages and restarts at page 0 on a new search", async () => {
    listCandidatesMock.mockImplementation(async ({ page }) =>
      pageOf(
        [buildCandidate({ id: page, fullName: `Persona ${page}` })],
        page,
        2,
      ),
    );
    const user = userEvent.setup();
    renderCandidates();
    expect(await screen.findByText("Página 1 de 2")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(await screen.findByText("Persona 1")).toBeInTheDocument();
    expect(screen.getByText("Página 2 de 2")).toBeInTheDocument();
    expect(listCandidatesMock).toHaveBeenLastCalledWith({ page: 1, size: 20 });

    await user.type(screen.getByLabelText("Buscar"), "per");
    expect(await screen.findByText("Persona 0")).toBeInTheDocument();
    expect(listCandidatesMock).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      q: "per",
    });
  });
});

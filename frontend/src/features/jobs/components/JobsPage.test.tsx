import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { listJobs, updateJobStatus } from "@/features/jobs/api";
import {
  buildJob,
  httpError,
  pageOf,
  renderRoute,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/jobs/api", () => ({
  listJobs: vi.fn(),
  getJob: vi.fn(),
  createJob: vi.fn(),
  updateJob: vi.fn(),
  updateJobStatus: vi.fn(),
}));

const listJobsMock = vi.mocked(listJobs);
const updateJobStatusMock = vi.mocked(updateJobStatus);

const openJob = buildJob({ id: 10, title: "Desarrollador backend" });
const closedJob = buildJob({
  id: 11,
  title: "Diseñador UX",
  department: null,
  status: "CLOSED",
});

beforeAll(() => {
  // jsdom lacks the pointer and scroll APIs the Radix Select relies on.
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.releasePointerCapture ??= () => {};
  Element.prototype.scrollIntoView ??= () => {};
});

function renderJobs() {
  signIn();
  return renderRoute("/jobs");
}

async function chooseStatus(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
) {
  await user.click(screen.getByRole("combobox", { name: "Estado" }));
  await user.click(await screen.findByRole("option", { name: label }));
}

describe("JobsPage", () => {
  afterEach(() => {
    vi.resetAllMocks();
    signOut();
  });

  it("shows a loading state", async () => {
    listJobsMock.mockReturnValue(new Promise(() => {}));
    renderJobs();

    expect(await screen.findByText("Cargando vacantes…")).toHaveAttribute(
      "role",
      "status",
    );
  });

  it("shows an error with retry", async () => {
    listJobsMock
      .mockRejectedValueOnce(httpError(500))
      .mockResolvedValueOnce(pageOf([openJob]));
    renderJobs();

    expect(
      await screen.findByText("No se pudieron cargar las vacantes."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(
      await screen.findByText("Desarrollador backend"),
    ).toBeInTheDocument();
    expect(listJobsMock).toHaveBeenCalledTimes(2);
  });

  it("shows an empty state when there are no jobs", async () => {
    listJobsMock.mockResolvedValue(pageOf([]));
    renderJobs();

    expect(
      await screen.findByText("Aún no hay vacantes. Crea la primera."),
    ).toBeInTheDocument();
  });

  it("shows a different empty state when filters match nothing", async () => {
    listJobsMock.mockResolvedValue(pageOf([]));
    const user = userEvent.setup();
    renderJobs();
    await screen.findByText("Aún no hay vacantes. Crea la primera.");

    await user.type(screen.getByLabelText("Buscar"), "zzz");

    expect(
      await screen.findByText("No hay vacantes que coincidan con los filtros."),
    ).toBeInTheDocument();
  });

  it("lists open and closed jobs with their badge and actions", async () => {
    listJobsMock.mockResolvedValue(pageOf([openJob, closedJob]));
    renderJobs();

    const rows = await screen.findAllByRole("row");
    expect(rows).toHaveLength(3);

    const open = within(rows[1]);
    expect(open.getByText("Desarrollador backend")).toBeInTheDocument();
    expect(open.getByText("Ingeniería")).toBeInTheDocument();
    expect(open.getByText("Remoto")).toBeInTheDocument();
    expect(open.getByText("Abierta")).toBeInTheDocument();
    expect(
      open.getByText(
        new Intl.DateTimeFormat("es-MX", { dateStyle: "medium" }).format(
          new Date(openJob.createdAt),
        ),
      ),
    ).toBeInTheDocument();
    expect(open.getByRole("link", { name: "Editar" })).toHaveAttribute(
      "href",
      "/jobs/10/edit",
    );
    expect(open.getByRole("button", { name: "Cerrar" })).toBeInTheDocument();

    const closed = within(rows[2]);
    expect(closed.getByText("Cerrada")).toBeInTheDocument();
    expect(closed.getByText("—")).toBeInTheDocument();
    expect(closed.getByRole("link", { name: "Editar" })).toBeInTheDocument();
    expect(open.getByRole("link", { name: "Ver tablero" })).toHaveAttribute(
      "href",
      "/jobs/10/board",
    );
    expect(closed.getByRole("link", { name: "Ver tablero" })).toHaveAttribute(
      "href",
      "/jobs/11/board",
    );
    expect(
      closed.queryByRole("button", { name: "Cerrar" }),
    ).not.toBeInTheDocument();

    expect(listJobsMock).toHaveBeenCalledWith({ page: 0, size: 20 });
  });

  it("sends the filters and goes back to page 0 when they change", async () => {
    listJobsMock.mockImplementation(async ({ page }) =>
      pageOf([buildJob({ id: 100 + page, title: `Vacante ${page}` })], page, 3),
    );
    const user = userEvent.setup();
    renderJobs();
    await screen.findByText("Vacante 0");

    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(await screen.findByText("Página 2 de 3")).toBeInTheDocument();
    expect(listJobsMock).toHaveBeenLastCalledWith({ page: 1, size: 20 });

    await chooseStatus(user, "Cerradas");
    expect(await screen.findByText("Página 1 de 3")).toBeInTheDocument();
    expect(listJobsMock).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      status: "CLOSED",
    });

    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(await screen.findByText("Página 2 de 3")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Buscar"), "  backend ");
    await waitFor(() =>
      expect(listJobsMock).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        status: "CLOSED",
        q: "backend",
      }),
    );
    expect(await screen.findByText("Página 1 de 3")).toBeInTheDocument();

    await chooseStatus(user, "Todas");
    await waitFor(() =>
      expect(listJobsMock).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        q: "backend",
      }),
    );
  });

  it("closes a job only after confirming", async () => {
    listJobsMock.mockResolvedValue(pageOf([openJob]));
    updateJobStatusMock.mockResolvedValue({ ...openJob, status: "CLOSED" });
    const user = userEvent.setup();
    renderJobs();
    await screen.findByText("Desarrollador backend");

    await user.click(screen.getByRole("button", { name: "Cerrar" }));
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText(
        "Una vacante cerrada no admite postulaciones nuevas.",
      ),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(updateJobStatusMock).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Cerrar" }));
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: "Cerrar vacante",
      }),
    );

    expect(await screen.findByText("Vacante cerrada")).toBeInTheDocument();
    expect(updateJobStatusMock).toHaveBeenCalledExactlyOnceWith(10, "CLOSED");
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    await waitFor(() => expect(listJobsMock).toHaveBeenCalledTimes(2));
  });

  it("keeps the dialog open with an error if closing fails", async () => {
    listJobsMock.mockResolvedValue(pageOf([openJob]));
    updateJobStatusMock.mockRejectedValue(httpError(500));
    const user = userEvent.setup();
    renderJobs();
    await screen.findByText("Desarrollador backend");

    await user.click(screen.getByRole("button", { name: "Cerrar" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: "Cerrar vacante" }),
    );

    expect(
      await within(dialog).findByText(
        "No se pudo cerrar la vacante. Inténtalo de nuevo.",
      ),
    ).toBeInTheDocument();
  });
});

import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createJob, getJob, listJobs, updateJob } from "@/features/jobs/api";
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

const createJobMock = vi.mocked(createJob);
const getJobMock = vi.mocked(getJob);
const updateJobMock = vi.mocked(updateJob);

function renderAt(path: string) {
  signIn();
  vi.mocked(listJobs).mockResolvedValue(pageOf([]));
  return renderRoute(path);
}

describe("job form", () => {
  afterEach(() => {
    vi.resetAllMocks();
    signOut();
  });

  it("requires a title", async () => {
    const user = userEvent.setup();
    renderAt("/jobs/new");

    await user.type(await screen.findByLabelText("Título"), "   ");
    await user.click(screen.getByRole("button", { name: "Crear vacante" }));

    expect(await screen.findByText("Ingresa el título")).toHaveAttribute(
      "id",
      "job-title-error",
    );
    expect(screen.getByLabelText("Título")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(createJobMock).not.toHaveBeenCalled();
  });

  it("checks the maximum lengths", async () => {
    const user = userEvent.setup();
    renderAt("/jobs/new");

    await user.click(await screen.findByLabelText("Título"));
    await user.paste("a".repeat(201));
    await user.click(screen.getByLabelText("Departamento"));
    await user.paste("d".repeat(101));
    await user.click(screen.getByLabelText("Ubicación"));
    await user.paste("u".repeat(101));
    await user.click(screen.getByLabelText("Descripción"));
    await user.paste("x".repeat(5001));
    await user.click(screen.getByRole("button", { name: "Crear vacante" }));

    expect(
      await screen.findByText("El título admite como máximo 200 caracteres"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("El departamento admite como máximo 100 caracteres"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("La ubicación admite como máximo 100 caracteres"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("La descripción admite como máximo 5000 caracteres"),
    ).toBeInTheDocument();
    expect(createJobMock).not.toHaveBeenCalled();
  });

  it("creates a job, shows a toast and goes back to the list", async () => {
    createJobMock.mockResolvedValue(buildJob());
    const user = userEvent.setup();
    const router = renderAt("/jobs/new");

    await user.type(await screen.findByLabelText("Título"), " QA Tester ");
    await user.type(screen.getByLabelText("Departamento"), "Calidad");
    await user.type(screen.getByLabelText("Descripción"), "Pruebas manuales");
    await user.click(screen.getByRole("button", { name: "Crear vacante" }));

    expect(await screen.findByText("Vacante creada")).toBeInTheDocument();
    expect(createJobMock.mock.calls[0][0]).toEqual({
      title: "QA Tester",
      department: "Calidad",
      location: "",
      description: "Pruebas manuales",
    });
    await waitFor(() => expect(router.state.location.pathname).toBe("/jobs"));
  });

  it("disables the button while saving", async () => {
    createJobMock.mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderAt("/jobs/new");

    await user.type(await screen.findByLabelText("Título"), "QA");
    await user.click(screen.getByRole("button", { name: "Crear vacante" }));

    expect(
      await screen.findByRole("button", { name: "Creando…" }),
    ).toBeDisabled();
  });

  it("shows server validation errors under each field", async () => {
    createJobMock.mockRejectedValue(
      httpError(400, {
        status: 400,
        errors: [
          { field: "title", message: "must not be blank" },
          { field: "location", message: "size must be between 0 and 100" },
        ],
      }),
    );
    const user = userEvent.setup();
    renderAt("/jobs/new");

    await user.type(await screen.findByLabelText("Título"), "QA");
    await user.click(screen.getByRole("button", { name: "Crear vacante" }));

    expect(await screen.findByText("must not be blank")).toHaveAttribute(
      "id",
      "job-title-error",
    );
    expect(screen.getByText("size must be between 0 and 100")).toHaveAttribute(
      "id",
      "job-location-error",
    );
  });

  it("shows other errors as an alert", async () => {
    createJobMock.mockRejectedValue(httpError(500));
    const user = userEvent.setup();
    renderAt("/jobs/new");

    await user.type(await screen.findByLabelText("Título"), "QA");
    await user.click(screen.getByRole("button", { name: "Crear vacante" }));

    expect(
      await screen.findByText(
        "No se pudo guardar la vacante. Inténtalo de nuevo.",
      ),
    ).toBeInTheDocument();
  });

  it("edits a job loaded from the API", async () => {
    getJobMock.mockResolvedValue(buildJob({ id: 10, location: null }));
    updateJobMock.mockResolvedValue(buildJob({ id: 10 }));
    const user = userEvent.setup();
    const router = renderAt("/jobs/10/edit");

    const title = await screen.findByLabelText("Título");
    expect(title).toHaveValue("Desarrollador backend");
    expect(screen.getByLabelText("Departamento")).toHaveValue("Ingeniería");
    expect(screen.getByLabelText("Ubicación")).toHaveValue("");
    expect(screen.getByLabelText("Descripción")).toHaveValue("Java y Spring");

    await user.clear(title);
    await user.type(title, "Desarrolladora backend");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText("Vacante actualizada")).toBeInTheDocument();
    expect(getJobMock).toHaveBeenCalledWith(10);
    expect(updateJobMock).toHaveBeenCalledWith(10, {
      title: "Desarrolladora backend",
      department: "Ingeniería",
      location: "",
      description: "Java y Spring",
    });
    await waitFor(() => expect(router.state.location.pathname).toBe("/jobs"));
  });

  it("shows a loading state while the job loads", async () => {
    getJobMock.mockReturnValue(new Promise(() => {}));
    renderAt("/jobs/10/edit");

    expect(await screen.findByText("Cargando vacante…")).toHaveAttribute(
      "role",
      "status",
    );
  });

  it("shows not found for a missing job", async () => {
    getJobMock.mockRejectedValue(httpError(404));
    renderAt("/jobs/999/edit");

    expect(
      await screen.findByRole("heading", { name: "Vacante no encontrada" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Volver a vacantes" }),
    ).toHaveAttribute("href", "/jobs");
  });

  it("shows not found for an id that is not a number", async () => {
    renderAt("/jobs/abc/edit");

    expect(
      await screen.findByRole("heading", { name: "Vacante no encontrada" }),
    ).toBeInTheDocument();
    expect(getJobMock).not.toHaveBeenCalled();
  });

  it("retries after a load error", async () => {
    getJobMock
      .mockRejectedValueOnce(httpError(500))
      .mockResolvedValueOnce(buildJob({ id: 10 }));
    renderAt("/jobs/10/edit");

    expect(
      await screen.findByText("No se pudo cargar la vacante."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(await screen.findByLabelText("Título")).toHaveValue(
      "Desarrollador backend",
    );
  });
});

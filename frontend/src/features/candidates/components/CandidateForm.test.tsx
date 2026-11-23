import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createCandidate,
  getCandidate,
  updateCandidate,
} from "@/features/candidates/api";
import {
  buildCandidate,
  httpError,
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

const createCandidateMock = vi.mocked(createCandidate);
const getCandidateMock = vi.mocked(getCandidate);
const updateCandidateMock = vi.mocked(updateCandidate);

const DUPLICATE = httpError(409, {
  status: 409,
  detail: "A candidate with this email already exists",
});

function renderAt(path: string) {
  signIn();
  return renderRoute(path);
}

async function fillCreateForm(
  user: ReturnType<typeof userEvent.setup>,
  values: { fullName: string; email: string },
) {
  await user.type(
    await screen.findByLabelText("Nombre completo"),
    values.fullName,
  );
  await user.type(screen.getByLabelText("Correo"), values.email);
  await user.click(screen.getByRole("button", { name: "Crear candidato" }));
}

function expectEmailError(message: string) {
  return waitFor(() => {
    expect(screen.getByText(message)).toHaveAttribute(
      "id",
      "candidate-email-error",
    );
    expect(screen.getByLabelText("Correo")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });
}

describe("candidate form", () => {
  afterEach(() => {
    vi.resetAllMocks();
    signOut();
  });

  it("requires name and email", async () => {
    const user = userEvent.setup();
    renderAt("/candidates/new");

    await user.click(
      await screen.findByRole("button", { name: "Crear candidato" }),
    );

    expect(await screen.findByText("Ingresa el nombre")).toBeInTheDocument();
    expect(screen.getByText("Ingresa el correo")).toBeInTheDocument();
    expect(createCandidateMock).not.toHaveBeenCalled();
  });

  it("validates the email format and the maximum lengths", async () => {
    const user = userEvent.setup();
    renderAt("/candidates/new");

    await user.click(await screen.findByLabelText("Teléfono"));
    await user.paste("1".repeat(31));
    await user.click(screen.getByLabelText("Origen"));
    await user.paste("s".repeat(101));
    await fillCreateForm(user, { fullName: "Laura", email: "no-es-correo" });

    await expectEmailError("Ingresa un correo válido");
    expect(
      screen.getByText("El teléfono admite como máximo 30 caracteres"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("El origen admite como máximo 100 caracteres"),
    ).toBeInTheDocument();
    expect(createCandidateMock).not.toHaveBeenCalled();
  });

  it("creates a candidate and opens its detail", async () => {
    const created = buildCandidate({ id: 42 });
    createCandidateMock.mockResolvedValue(created);
    getCandidateMock.mockResolvedValue(created);
    const user = userEvent.setup();
    const router = renderAt("/candidates/new");

    await user.type(await screen.findByLabelText("Teléfono"), "555");
    await fillCreateForm(user, {
      fullName: " Laura Gómez ",
      email: " laura@example.com ",
    });

    expect(await screen.findByText("Candidato creado")).toBeInTheDocument();
    expect(createCandidateMock.mock.calls[0][0]).toEqual({
      fullName: "Laura Gómez",
      email: "laura@example.com",
      phone: "555",
      source: "",
    });
    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/candidates/42"),
    );
    expect(
      await screen.findByRole("heading", { name: "Laura Gómez" }),
    ).toBeInTheDocument();
  });

  it("shows a duplicate email under the email field when creating", async () => {
    createCandidateMock.mockRejectedValue(DUPLICATE);
    const user = userEvent.setup();
    renderAt("/candidates/new");

    await fillCreateForm(user, {
      fullName: "Laura",
      email: "laura@example.com",
    });

    await expectEmailError("Ya existe un candidato con ese correo");
  });

  it("shows a duplicate email under the email field when editing", async () => {
    getCandidateMock.mockResolvedValue(buildCandidate({ id: 20 }));
    updateCandidateMock.mockRejectedValue(DUPLICATE);
    const user = userEvent.setup();
    renderAt("/candidates/20/edit");

    const email = await screen.findByLabelText("Correo");
    await user.clear(email);
    await user.type(email, "otro@example.com");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    await expectEmailError("Ya existe un candidato con ese correo");
  });

  it("shows server validation errors under each field", async () => {
    createCandidateMock.mockRejectedValue(
      httpError(400, {
        status: 400,
        errors: [{ field: "phone", message: "size must be between 0 and 30" }],
      }),
    );
    const user = userEvent.setup();
    renderAt("/candidates/new");

    await fillCreateForm(user, {
      fullName: "Laura",
      email: "laura@example.com",
    });

    expect(
      await screen.findByText("size must be between 0 and 30"),
    ).toHaveAttribute("id", "candidate-phone-error");
  });

  it("edits a candidate and goes back to its detail", async () => {
    getCandidateMock.mockResolvedValue(
      buildCandidate({ id: 20, source: null }),
    );
    updateCandidateMock.mockResolvedValue(buildCandidate({ id: 20 }));
    const user = userEvent.setup();
    const router = renderAt("/candidates/20/edit");

    const name = await screen.findByLabelText("Nombre completo");
    expect(name).toHaveValue("Laura Gómez");
    expect(screen.getByLabelText("Origen")).toHaveValue("");
    await user.type(screen.getByLabelText("Origen"), "Referido");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(
      await screen.findByText("Candidato actualizado"),
    ).toBeInTheDocument();
    expect(updateCandidateMock).toHaveBeenCalledWith(20, {
      fullName: "Laura Gómez",
      email: "laura@example.com",
      phone: "+52 777 123 4567",
      source: "Referido",
    });
    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/candidates/20"),
    );
  });

  it("shows not found when editing a missing candidate", async () => {
    getCandidateMock.mockRejectedValue(httpError(404));
    renderAt("/candidates/999/edit");

    expect(
      await screen.findByRole("heading", { name: "Candidato no encontrado" }),
    ).toBeInTheDocument();
  });
});

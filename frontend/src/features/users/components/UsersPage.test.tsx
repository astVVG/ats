import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Providers } from "@/app/providers";
import { createUser, listUsers } from "@/features/users/api";
import { UsersPage } from "@/features/users/components/UsersPage";
import type { UserPage } from "@/features/users/types";
import {
  adminUser,
  createTestQueryClient,
  httpError,
  recruiterUser,
} from "@/test/utils";

vi.mock("@/features/users/api", () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
}));

const listUsersMock = vi.mocked(listUsers);
const createUserMock = vi.mocked(createUser);

function page(content: UserPage["content"], pageNumber = 0, totalPages = 1) {
  return {
    content,
    page: pageNumber,
    size: 10,
    totalElements: content?.length ?? 0,
    totalPages,
  } satisfies UserPage;
}

function renderPage() {
  render(
    <Providers queryClient={createTestQueryClient()}>
      <UsersPage />
    </Providers>,
  );
}

async function openDialog() {
  const user = userEvent.setup();
  await user.click(
    await screen.findByRole("button", { name: "Crear usuario" }),
  );
  return { user, dialog: await screen.findByRole("dialog") };
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  dialog: HTMLElement,
  values: { name: string; email: string; password: string },
) {
  const form = within(dialog);
  await user.type(form.getByLabelText("Nombre"), values.name);
  await user.type(form.getByLabelText("Correo"), values.email);
  await user.type(form.getByLabelText("Contraseña"), values.password);
  await user.click(form.getByRole("button", { name: "Crear" }));
}

describe("UsersPage", () => {
  afterEach(() => {
    vi.resetAllMocks();
  });

  it("shows a loading state", async () => {
    listUsersMock.mockReturnValue(new Promise(() => {}));
    renderPage();

    expect(await screen.findByText("Cargando usuarios…")).toHaveAttribute(
      "role",
      "status",
    );
  });

  it("shows an error with retry", async () => {
    listUsersMock
      .mockRejectedValueOnce(httpError(500))
      .mockResolvedValueOnce(page([adminUser]));
    renderPage();

    expect(
      await screen.findByText("No se pudieron cargar los usuarios."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(await screen.findByText("Ada Admin")).toBeInTheDocument();
    expect(listUsersMock).toHaveBeenCalledTimes(2);
  });

  it("shows an empty state", async () => {
    listUsersMock.mockResolvedValue(page([]));
    renderPage();

    expect(await screen.findByText("No hay usuarios")).toBeInTheDocument();
  });

  it("lists users with role, status and creation date", async () => {
    listUsersMock.mockResolvedValue(
      page([adminUser, { ...recruiterUser, active: false }]),
    );
    renderPage();

    const rows = await screen.findAllByRole("row");
    expect(rows).toHaveLength(3);
    const admin = within(rows[1]);
    expect(admin.getByText("Ada Admin")).toBeInTheDocument();
    expect(admin.getByText("ada@example.com")).toBeInTheDocument();
    expect(admin.getByText("Administrador")).toBeInTheDocument();
    expect(admin.getByText("Activo")).toBeInTheDocument();
    expect(
      admin.getByText(
        new Intl.DateTimeFormat("es-MX", { dateStyle: "medium" }).format(
          new Date(adminUser.createdAt ?? ""),
        ),
      ),
    ).toBeInTheDocument();
    const recruiter = within(rows[2]);
    expect(recruiter.getByText("Reclutador")).toBeInTheDocument();
    expect(recruiter.getByText("Inactivo")).toBeInTheDocument();
    expect(listUsersMock).toHaveBeenCalledWith({ page: 0, size: 10 });
  });

  it("paginates with previous and next", async () => {
    listUsersMock.mockImplementation(async ({ page: requested }) =>
      requested === 0 ? page([adminUser], 0, 2) : page([recruiterUser], 1, 2),
    );
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Página 1 de 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Anterior" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(await screen.findByText("Rita Reclutadora")).toBeInTheDocument();
    expect(screen.getByText("Página 2 de 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Siguiente" })).toBeDisabled();
    expect(listUsersMock).toHaveBeenLastCalledWith({ page: 1, size: 10 });

    await user.click(screen.getByRole("button", { name: "Anterior" }));
    expect(await screen.findByText("Ada Admin")).toBeInTheDocument();
  });

  it("validates the create form on the client", async () => {
    listUsersMock.mockResolvedValue(page([adminUser]));
    renderPage();
    const { user, dialog } = await openDialog();
    const form = within(dialog);

    await user.click(form.getByRole("button", { name: "Crear" }));
    expect(await form.findByText("Ingresa el nombre")).toBeInTheDocument();
    expect(form.getByText("Ingresa el correo")).toBeInTheDocument();
    expect(
      form.getByText("La contraseña debe tener al menos 8 caracteres"),
    ).toBeInTheDocument();

    // 40 characters but 80 bytes in UTF-8.
    await fillForm(user, dialog, {
      name: "Ana",
      email: "ana@example.com",
      password: "á".repeat(40),
    });
    expect(
      await form.findByText(/La contraseña es demasiado larga/),
    ).toBeInTheDocument();
    expect(createUserMock).not.toHaveBeenCalled();
  });

  it("creates a user, refreshes the list and shows a toast", async () => {
    listUsersMock.mockResolvedValue(page([adminUser]));
    createUserMock.mockResolvedValue(recruiterUser);
    renderPage();
    await screen.findByText("Ada Admin");
    const { user, dialog } = await openDialog();

    await fillForm(user, dialog, {
      name: "Rita Reclutadora",
      email: "rita@example.com",
      password: "Rita12345!",
    });

    expect(await screen.findByText("Usuario creado")).toBeInTheDocument();
    expect(createUserMock.mock.calls[0][0]).toEqual({
      name: "Rita Reclutadora",
      email: "rita@example.com",
      password: "Rita12345!",
      role: "RECRUITER",
    });
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    await waitFor(() => expect(listUsersMock).toHaveBeenCalledTimes(2));
  });

  it("shows a duplicate email under the email field", async () => {
    listUsersMock.mockResolvedValue(page([adminUser]));
    createUserMock.mockRejectedValue(
      httpError(409, { status: 409, detail: "Email already registered" }),
    );
    renderPage();
    const { user, dialog } = await openDialog();

    await fillForm(user, dialog, {
      name: "Ada",
      email: "ada@example.com",
      password: "Password1",
    });

    const message = await within(dialog).findByText(
      "Este correo ya está registrado",
    );
    expect(message).toHaveAttribute("id", "user-email-error");
    expect(within(dialog).getByLabelText("Correo")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  it("shows server validation errors under each field", async () => {
    listUsersMock.mockResolvedValue(page([adminUser]));
    createUserMock.mockRejectedValue(
      httpError(400, {
        status: 400,
        errors: [{ field: "password", message: "must be at most 72 bytes" }],
      }),
    );
    renderPage();
    const { user, dialog } = await openDialog();

    await fillForm(user, dialog, {
      name: "Ana",
      email: "ana@example.com",
      password: "Password1",
    });

    expect(
      await within(dialog).findByText("must be at most 72 bytes"),
    ).toHaveAttribute("id", "user-password-error");
  });

  it("shows other errors as an alert inside the dialog", async () => {
    listUsersMock.mockResolvedValue(page([adminUser]));
    createUserMock.mockRejectedValue(httpError(500));
    renderPage();
    const { user, dialog } = await openDialog();

    await fillForm(user, dialog, {
      name: "Ana",
      email: "ana@example.com",
      password: "Password1",
    });

    expect(
      await within(dialog).findByText(
        "No se pudo crear el usuario. Inténtalo de nuevo.",
      ),
    ).toBeInTheDocument();
  });
});

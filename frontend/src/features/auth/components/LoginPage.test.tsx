import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Providers } from "@/app/providers";
import { fetchMe, login } from "@/features/auth/api";
import { LoginPage } from "@/features/auth/components/LoginPage";
import { useSessionStore } from "@/features/auth/store";
import {
  adminUser,
  createTestQueryClient,
  httpError,
  loginResponse,
} from "@/test/utils";

vi.mock("@/features/auth/api", () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
}));

const loginMock = vi.mocked(login);
const fetchMeMock = vi.mocked(fetchMe);

function renderLogin(from?: string) {
  const router = createMemoryRouter(
    [
      { path: "/login", element: <LoginPage /> },
      { path: "/", element: <p>Pantalla de inicio</p> },
      { path: "/users", element: <p>Pantalla de usuarios</p> },
    ],
    {
      initialEntries: [
        { pathname: "/login", state: from ? { from } : undefined },
      ],
    },
  );
  render(
    <Providers queryClient={createTestQueryClient()}>
      <RouterProvider router={router} />
    </Providers>,
  );
}

async function submit(email: string, password: string) {
  const user = userEvent.setup();
  if (email) {
    await user.type(screen.getByLabelText("Correo"), email);
  }
  if (password) {
    await user.type(screen.getByLabelText("Contraseña"), password);
  }
  await user.click(screen.getByRole("button", { name: "Iniciar sesión" }));
}

describe("LoginPage", () => {
  afterEach(() => {
    vi.resetAllMocks();
    useSessionStore.getState().clearSession();
    localStorage.clear();
  });

  it("validates required fields and email format", async () => {
    renderLogin();

    await submit("", "");
    expect(await screen.findByText("Ingresa tu correo")).toBeInTheDocument();
    expect(screen.getByText("Ingresa tu contraseña")).toBeInTheDocument();

    await submit("no-es-correo", "");
    expect(
      await screen.findByText("Ingresa un correo válido"),
    ).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
  });

  it("logs in, stores the session and navigates home", async () => {
    loginMock.mockResolvedValue({
      accessToken: "token-123",
      tokenType: "Bearer",
      expiresIn: 7200,
    });
    fetchMeMock.mockResolvedValue(adminUser);
    renderLogin();

    await submit("ada@example.com", "Secreta-123");

    expect(await screen.findByText("Pantalla de inicio")).toBeInTheDocument();
    expect(loginMock).toHaveBeenCalledWith({
      email: "ada@example.com",
      password: "Secreta-123",
    });
    expect(fetchMeMock).toHaveBeenCalledWith("token-123");
    expect(useSessionStore.getState()).toMatchObject({
      token: "token-123",
      user: adminUser,
    });
    expect(localStorage.getItem("ats.session")).not.toContain("Secreta-123");
  });

  it("returns to the page requested before logging in", async () => {
    loginMock.mockResolvedValue(loginResponse("token-123"));
    fetchMeMock.mockResolvedValue(adminUser);
    renderLogin("/users");

    await submit("ada@example.com", "Secreta-123");

    expect(await screen.findByText("Pantalla de usuarios")).toBeInTheDocument();
  });

  it("shows a generic message on invalid credentials", async () => {
    loginMock.mockRejectedValue(httpError(401));
    renderLogin();

    await submit("ada@example.com", "incorrecta");

    expect(
      await screen.findByText("Correo o contraseña incorrectos"),
    ).toBeInTheDocument();
    expect(useSessionStore.getState().token).toBeNull();
  });

  it("shows server validation errors next to the field", async () => {
    loginMock.mockRejectedValue(
      httpError(400, {
        status: 400,
        title: "Validation failed",
        errors: [{ field: "email", message: "must be a well-formed email" }],
      }),
    );
    renderLogin();

    await submit("ada@example.com", "Secreta-123");

    expect(
      await screen.findByText("must be a well-formed email"),
    ).toBeInTheDocument();
  });

  it("shows a generic message on network errors", async () => {
    loginMock.mockRejectedValue(new Error("Network Error"));
    renderLogin();

    await submit("ada@example.com", "Secreta-123");

    expect(
      await screen.findByText(/No se pudo iniciar sesión/),
    ).toBeInTheDocument();
  });

  it("disables the button while logging in", async () => {
    loginMock.mockReturnValue(new Promise(() => {}));
    renderLogin();

    await submit("ada@example.com", "Secreta-123");

    expect(
      await screen.findByRole("button", { name: "Iniciando sesión…" }),
    ).toBeDisabled();
  });

  it("redirects home when a session already exists", async () => {
    useSessionStore.setState({ token: "token-123", user: adminUser });
    renderLogin();

    expect(await screen.findByText("Pantalla de inicio")).toBeInTheDocument();
  });
});

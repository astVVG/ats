import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Providers } from "@/app/providers";
import { routes } from "@/app/router";
import { fetchMe, login } from "@/features/auth/api";
import { useSessionStore } from "@/features/auth/store";
import type { User } from "@/features/auth/types";
import { listUsers } from "@/features/users/api";
import {
  adminUser,
  createTestQueryClient,
  loginResponse,
  recruiterUser,
} from "@/test/utils";

vi.mock("@/shared/api/health", () => ({
  fetchHealth: vi.fn().mockResolvedValue({ status: "UP" }),
}));

vi.mock("@/features/auth/api", () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
}));

vi.mock("@/features/users/api", () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
}));

const emptyPage = {
  content: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
};

function signIn(user: User) {
  useSessionStore.setState({ token: "token-123", user });
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers queryClient={createTestQueryClient()}>
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

describe("app router", () => {
  afterEach(() => {
    vi.mocked(login).mockReset();
    vi.mocked(fetchMe).mockReset();
    vi.mocked(listUsers).mockReset();
    useSessionStore.getState().clearSession();
    localStorage.clear();
  });

  it("renders the home page at / with a session", async () => {
    signIn(adminUser);
    renderAt("/");

    expect(
      await screen.findByRole("heading", { name: "Mini ATS" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Comenzar" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Ada Admin")).toBeInTheDocument();
    expect(screen.getByText("Administrador")).toBeInTheDocument();
  });

  it("renders the not found page for unknown routes", async () => {
    signIn(adminUser);
    renderAt("/nada");

    expect(
      await screen.findByRole("heading", { name: "Página no encontrada" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Volver al inicio" }),
    ).toHaveAttribute("href", "/");
  });

  it("renders the theme preview at /dev/theme in development", async () => {
    signIn(recruiterUser);
    renderAt("/dev/theme");

    expect(
      await screen.findByRole("heading", { name: "Vista previa del tema" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("group", { name: "Tema" })).toHaveLength(2);
    expect(screen.getAllByText("Contratado").length).toBeGreaterThan(0);
  });

  it("redirects /dev/theme to /login without a session", async () => {
    const router = renderAt("/dev/theme");

    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
  });

  it("redirects to /login without a session", async () => {
    const router = renderAt("/users");

    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
  });

  it.each([
    "/jobs",
    "/jobs/new",
    "/jobs/5/edit",
    "/jobs/5/board",
    "/candidates",
    "/candidates/new",
    "/candidates/5",
    "/candidates/5/edit",
  ])("redirects %s to /login without a session", async (path) => {
    const router = renderAt(path);

    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
  });

  it.each([adminUser, recruiterUser])(
    "shows Vacantes and Candidatos to $role",
    async (user) => {
      signIn(user);
      renderAt("/");

      const nav = await screen.findByRole("navigation", { name: "Principal" });
      expect(
        within(nav).getByRole("link", { name: "Vacantes" }),
      ).toHaveAttribute("href", "/jobs");
      expect(
        within(nav).getByRole("link", { name: "Candidatos" }),
      ).toHaveAttribute("href", "/candidates");
    },
  );

  it("goes back to the requested page after logging in", async () => {
    vi.mocked(login).mockResolvedValue(loginResponse("token-123"));
    vi.mocked(fetchMe).mockResolvedValue(adminUser);
    vi.mocked(listUsers).mockResolvedValue(emptyPage);
    const router = renderAt("/users");
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("Correo"), "ada@example.com");
    await user.type(screen.getByLabelText("Contraseña"), "Secreta-123");
    await user.click(screen.getByRole("button", { name: "Iniciar sesión" }));

    expect(
      await screen.findByRole("heading", { name: "Usuarios" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/users");
  });

  it("loads the user from /me when only the token was persisted", async () => {
    useSessionStore.setState({ token: "token-123", user: null });
    vi.mocked(fetchMe).mockResolvedValue(recruiterUser);
    renderAt("/");

    expect(await screen.findByText("Rita Reclutadora")).toBeInTheDocument();
    expect(fetchMe).toHaveBeenCalledTimes(1);
  });

  it("lets an ADMIN open /users and shows the menu entry", async () => {
    signIn(adminUser);
    vi.mocked(listUsers).mockResolvedValue(emptyPage);
    renderAt("/users");

    expect(
      await screen.findByRole("heading", { name: "Usuarios" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Usuarios" })).toBeInTheDocument();
  });

  it("hides Usuarios from a RECRUITER and redirects /users home", async () => {
    signIn(recruiterUser);
    const router = renderAt("/users");

    expect(
      await screen.findByRole("heading", { name: "Mini ATS" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    expect(
      screen.queryByRole("link", { name: "Usuarios" }),
    ).not.toBeInTheDocument();
    expect(listUsers).not.toHaveBeenCalled();
  });

  it("logs out to /login and removes the stored token", async () => {
    signIn(adminUser);
    const router = renderAt("/");

    await userEvent.click(
      await screen.findByRole("button", { name: "Cerrar sesión" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(useSessionStore.getState().token).toBeNull();
    expect(localStorage.getItem("ats.session")).not.toContain("token-123");
  });

  it("does not keep the previous page after logging out", async () => {
    const otherAdmin: User = {
      ...adminUser,
      id: 3,
      name: "Bea Admin",
      email: "bea@example.com",
    };
    vi.mocked(login)
      .mockResolvedValueOnce(loginResponse("token-ada"))
      .mockResolvedValueOnce(loginResponse("token-bea"));
    vi.mocked(fetchMe)
      .mockResolvedValueOnce(adminUser)
      .mockResolvedValueOnce(otherAdmin);
    vi.mocked(listUsers).mockResolvedValue(emptyPage);
    const router = renderAt("/login");
    const user = userEvent.setup();

    async function logIn(email: string) {
      await user.type(await screen.findByLabelText("Correo"), email);
      await user.type(screen.getByLabelText("Contraseña"), "Secreta-123");
      await user.click(screen.getByRole("button", { name: "Iniciar sesión" }));
    }

    await logIn("ada@example.com");
    await user.click(await screen.findByRole("link", { name: "Usuarios" }));
    expect(
      await screen.findByRole("heading", { name: "Usuarios" }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Cerrar sesión" }));
    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(router.state.location.state).toBeFalsy();

    // Another ADMIN could open /users, so landing on / proves nothing was kept.
    await logIn("bea@example.com");
    expect(await screen.findByText("Bea Admin")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
  });
});

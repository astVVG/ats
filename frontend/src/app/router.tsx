import { createBrowserRouter, type RouteObject } from "react-router";
import { AppLayout } from "@/app/AppLayout";
import { HomePage } from "@/app/pages/HomePage";
import { NotFoundPage } from "@/app/pages/NotFoundPage";
import { LoginPage } from "@/features/auth/components/LoginPage";
import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { RequireRole } from "@/features/auth/components/RequireRole";
import { UsersPage } from "@/features/users/components/UsersPage";

export const routes: RouteObject[] = [
  { path: "/login", element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: "/",
        element: <AppLayout />,
        children: [
          { index: true, element: <HomePage /> },
          {
            element: <RequireRole role="ADMIN" />,
            children: [{ path: "users", element: <UsersPage /> }],
          },
          { path: "*", element: <NotFoundPage /> },
        ],
      },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(routes);
}

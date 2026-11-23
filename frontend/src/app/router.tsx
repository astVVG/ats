import { createBrowserRouter, type RouteObject } from "react-router";
import { AppLayout } from "@/app/AppLayout";
import { HomePage } from "@/app/pages/HomePage";
import { NotFoundPage } from "@/app/pages/NotFoundPage";
import { LoginPage } from "@/features/auth/components/LoginPage";
import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { RequireRole } from "@/features/auth/components/RequireRole";
import { CandidateDetailPage } from "@/features/candidates/components/CandidateDetailPage";
import { CandidatesPage } from "@/features/candidates/components/CandidatesPage";
import { CreateCandidatePage } from "@/features/candidates/components/CreateCandidatePage";
import { EditCandidatePage } from "@/features/candidates/components/EditCandidatePage";
import { CreateJobPage } from "@/features/jobs/components/CreateJobPage";
import { EditJobPage } from "@/features/jobs/components/EditJobPage";
import { JobsPage } from "@/features/jobs/components/JobsPage";
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
          { path: "jobs", element: <JobsPage /> },
          { path: "jobs/new", element: <CreateJobPage /> },
          { path: "jobs/:id/edit", element: <EditJobPage /> },
          { path: "candidates", element: <CandidatesPage /> },
          { path: "candidates/new", element: <CreateCandidatePage /> },
          { path: "candidates/:id", element: <CandidateDetailPage /> },
          { path: "candidates/:id/edit", element: <EditCandidatePage /> },
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

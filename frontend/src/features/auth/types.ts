import type { components } from "@/shared/api/schema";

export type LoginRequest = components["schemas"]["LoginRequest"];
export type LoginResponse = components["schemas"]["LoginResponse"];
export type User = components["schemas"]["UserResponse"];
export type Role = User["role"];

export const ROLES = ["ADMIN", "RECRUITER"] as const satisfies readonly Role[];

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Administrador",
  RECRUITER: "Reclutador",
};

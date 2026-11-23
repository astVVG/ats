import { apiClient, LOGIN_PATH } from "@/shared/api/client";
import type { LoginRequest, LoginResponse, User } from "@/features/auth/types";

export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>(LOGIN_PATH, credentials);
  return data;
}

/** Pass a token to call /me before it is stored in the session. */
export async function fetchMe(token?: string): Promise<User> {
  const { data } = await apiClient.get<User>("/me", {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  return data;
}

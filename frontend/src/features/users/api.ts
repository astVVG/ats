import { apiClient } from "@/shared/api/client";
import type {
  CreateUserRequest,
  PageParams,
  UserPage,
  UserResponse,
} from "@/features/users/types";

export async function listUsers({ page, size }: PageParams): Promise<UserPage> {
  const { data } = await apiClient.get<UserPage>("/users", {
    params: { page, size },
  });
  return data;
}

export async function createUser(
  request: CreateUserRequest,
): Promise<UserResponse> {
  const { data } = await apiClient.post<UserResponse>("/users", request);
  return data;
}

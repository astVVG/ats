import type { components } from "@/shared/api/schema";

export type UserResponse = components["schemas"]["UserResponse"];
export type UserPage = components["schemas"]["PageResponseUserResponse"];
export type CreateUserRequest = components["schemas"]["CreateUserRequest"];

export type PageParams = {
  page: number;
  size: number;
};

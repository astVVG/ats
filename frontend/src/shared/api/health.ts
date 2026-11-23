import { apiClient } from "@/shared/api/client";

// Manual type: Actuator endpoints are not part of the OpenAPI schema.
export type HealthResponse = {
  status: string;
};

export async function fetchHealth(): Promise<HealthResponse> {
  const { data } = await apiClient.get<HealthResponse>("/actuator/health", {
    baseURL: "/",
  });
  return data;
}

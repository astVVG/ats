import { apiClient } from "@/shared/api/client";
import type {
  Application,
  Board,
  MoveApplicationRequest,
} from "@/features/board/types";

export async function getJobBoard(jobId: number): Promise<Board> {
  const { data } = await apiClient.get<Board>(`/jobs/${jobId}/board`);
  return data;
}

export async function moveApplication(
  id: number,
  request: MoveApplicationRequest,
): Promise<Application> {
  const { data } = await apiClient.patch<Application>(
    `/applications/${id}/move`,
    request,
  );
  return data;
}

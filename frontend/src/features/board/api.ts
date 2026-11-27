import { apiClient } from "@/shared/api/client";
import {
  DETAIL_PAGE_SIZE,
  type Application,
  type Board,
  type CreateApplicationRequest,
  type CreateNoteRequest,
  type MoveApplicationRequest,
  type Note,
  type NotePage,
  type StageHistoryPage,
  type UpdateRatingRequest,
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

export async function listNotes(
  applicationId: number,
  page: number,
): Promise<NotePage> {
  const { data } = await apiClient.get<NotePage>(
    `/applications/${applicationId}/notes`,
    { params: { page, size: DETAIL_PAGE_SIZE } },
  );
  return data;
}

export async function createNote(
  applicationId: number,
  request: CreateNoteRequest,
): Promise<Note> {
  const { data } = await apiClient.post<Note>(
    `/applications/${applicationId}/notes`,
    request,
  );
  return data;
}

export async function deleteNote(noteId: number): Promise<void> {
  await apiClient.delete(`/notes/${noteId}`);
}

export async function listHistory(
  applicationId: number,
  page: number,
): Promise<StageHistoryPage> {
  const { data } = await apiClient.get<StageHistoryPage>(
    `/applications/${applicationId}/history`,
    { params: { page, size: DETAIL_PAGE_SIZE } },
  );
  return data;
}

export async function rateApplication(
  applicationId: number,
  request: UpdateRatingRequest,
): Promise<Application> {
  const { data } = await apiClient.patch<Application>(
    `/applications/${applicationId}/rating`,
    request,
  );
  return data;
}

export async function createApplication(
  request: CreateApplicationRequest,
): Promise<Application> {
  const { data } = await apiClient.post<Application>("/applications", request);
  return data;
}

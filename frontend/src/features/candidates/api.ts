import { apiClient } from "@/shared/api/client";
import type {
  Candidate,
  CandidateListParams,
  CandidatePage,
  CandidateRequest,
} from "@/features/candidates/types";

// A 5 MB upload can take longer than the default timeout on a slow network.
const RESUME_TIMEOUT_MS = 120_000;

export async function listCandidates({
  page,
  size,
  q,
}: CandidateListParams): Promise<CandidatePage> {
  const { data } = await apiClient.get<CandidatePage>("/candidates", {
    params: { page, size, q },
  });
  return data;
}

export async function getCandidate(id: number): Promise<Candidate> {
  const { data } = await apiClient.get<Candidate>(`/candidates/${id}`);
  return data;
}

export async function createCandidate(
  request: CandidateRequest,
): Promise<Candidate> {
  const { data } = await apiClient.post<Candidate>("/candidates", request);
  return data;
}

export async function updateCandidate(
  id: number,
  request: CandidateRequest,
): Promise<Candidate> {
  const { data } = await apiClient.put<Candidate>(`/candidates/${id}`, request);
  return data;
}

/** `onProgress` receives the percentage sent so far, from 0 to 100. */
export async function uploadResume(
  id: number,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<Candidate> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await apiClient.post<Candidate>(
    `/candidates/${id}/resume`,
    form,
    {
      timeout: RESUME_TIMEOUT_MS,
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded * 100) / event.total));
        }
      },
    },
  );
  return data;
}

export async function downloadResume(id: number): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(`/candidates/${id}/resume`, {
    responseType: "blob",
    timeout: RESUME_TIMEOUT_MS,
    headers: { Accept: "application/pdf, application/problem+json" },
  });
  return data;
}

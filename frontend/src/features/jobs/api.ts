import { apiClient } from "@/shared/api/client";
import type {
  Job,
  JobListParams,
  JobPage,
  JobRequest,
  JobStatus,
} from "@/features/jobs/types";

export async function listJobs({
  page,
  size,
  status,
  q,
}: JobListParams): Promise<JobPage> {
  const { data } = await apiClient.get<JobPage>("/jobs", {
    params: { page, size, status, q },
  });
  return data;
}

export async function getJob(id: number): Promise<Job> {
  const { data } = await apiClient.get<Job>(`/jobs/${id}`);
  return data;
}

export async function createJob(request: JobRequest): Promise<Job> {
  const { data } = await apiClient.post<Job>("/jobs", request);
  return data;
}

export async function updateJob(id: number, request: JobRequest): Promise<Job> {
  const { data } = await apiClient.put<Job>(`/jobs/${id}`, request);
  return data;
}

export async function updateJobStatus(
  id: number,
  status: JobStatus,
): Promise<Job> {
  const { data } = await apiClient.patch<Job>(`/jobs/${id}/status`, {
    status,
  });
  return data;
}

import type { components } from "@/shared/api/schema";

export type Job = components["schemas"]["JobResponse"];
export type JobPage = components["schemas"]["PageResponseJobResponse"];
export type JobRequest = components["schemas"]["JobRequest"];
export type JobStatus = Job["status"];

export type JobListParams = {
  page: number;
  size: number;
  status?: JobStatus;
  q?: string;
};

export const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  OPEN: "Abierta",
  CLOSED: "Cerrada",
};

import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';

export type JobRunStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED';

export interface JobRun {
  id: number;
  jobName: string;
  trigger: 'SCHEDULED' | 'MANUAL';
  status: JobRunStatus;
  attempts: number;
  processed: number;
  message: string | null;
  instanceId: string;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
}

export interface JobStatus {
  name: string;
  title: string;
  description: string;
  intervalSeconds: number;
  running: boolean;
  nextRunAt: string | null;
  consecutiveFailures: number;
  lastRun: JobRun | null;
}

const jobPath = (name: string) => `/admin/jobs/${encodeURIComponent(name)}`;

export const jobApi = {
  list: (signal?: AbortSignal) => axiosClient.get<unknown, ApiResponse<JobStatus[]>>('/admin/jobs', { signal }),
  history: (name: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<JobRun>>>(`${jobPath(name)}/runs`, { params: { size: 20 }, signal }),
  run: (name: string) => axiosClient.post<unknown, ApiResponse<JobRun>>(`${jobPath(name)}/run`),
};

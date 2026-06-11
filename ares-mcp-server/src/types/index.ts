import { z } from 'zod/v3';

export type Endpoint = 'planning' | 'verification';

export type JobStatus =
  | 'INITIALIZED'
  | 'VECTOR_FETCH'
  | 'ANALYZING'
  | 'COMPLETED'
  | 'FAILED';

export interface JobResponse {
  jobId: string;
  status: JobStatus;
  currentTask?: string;
  payload?: string;
}

export const PlanningSchema: any = {
  projectId: z
    .string()
    .uuid('projectId must be a valid tracking UUID namespace.')
    .optional(),
  taskDescription: z
    .string()
    .min(5, 'Task description must contain clear development context.'),
};
export const VerificationSchema: any = {
  projectId: z
    .string()
    .uuid('projectId must be a valid tracking UUID namespace.')
    .optional(),
  gitDiff: z.string().min(1, 'Git diff context stream cannot be empty.'),
};

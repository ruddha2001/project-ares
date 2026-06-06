import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import type { Endpoint, JobResponse, JobStatus } from '../types/index.js';

const BACKEND_URL = process.env.ARES_BACKEND_URL;
const POLLING_INTERVAL_MS = parseInt(
  process.env.POLLING_INTERVAL_MS ?? '3000',
  10,
);

export async function executeAresJobFlow(
  endpoint: Endpoint,
  payload: Record<string, any>,
): Promise<CallToolResult> {
  try {
    if (!BACKEND_URL) {
      throw new Error(
        `Backend URL must be registered to use this application. Current value: ${BACKEND_URL}\n`,
      );
    }

    const initResponse = await fetch(`${BACKEND_URL}/api/v1/jobs/${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    if (!initResponse.ok) {
      throw new Error(
        `Backend rejected job initialization: ${initResponse.statusText}`,
      );
    }

    let jobState = (await initResponse.json()) as JobResponse;
    const jobId = jobState.jobId;

    console.log(
      `[ARES-LOG] Job [${jobId}] securely initialized. Entering telemetry polling track...`,
    );

    while (jobState.status !== 'COMPLETED' && jobState.status !== 'FAILED') {
      await Bun.sleep(POLLING_INTERVAL_MS);

      const pollResponse = await fetch(`${BACKEND_URL}/api/v1/jobs/${jobId}`);
      if (!pollResponse.ok) {
        throw new Error(
          `Lost track of active job pipeline state for ID: ${jobId}`,
        );
      }

      jobState = (await pollResponse.json()) as JobResponse;
      console.error(
        `[ARES-TELEMETRY] Job: ${jobId} | Status: ${jobState.status} | Task: ${jobState.currentTask || 'Executing'}`,
      );
    }

    if (jobState.status === 'FAILED') {
      return {
        content: [
          {
            type: 'text',
            text: `❌ ARES Engine processing failure:\n${jobState.payload || 'Unknown internal error.'}`,
          },
        ],
        isError: true,
      } as CallToolResult;
    }

    return {
      content: [
        {
          type: 'text',
          text:
            jobState.payload || '🚀 Job finalized with empty payload results.',
        },
      ],
    } as CallToolResult;
  } catch (error: any) {
    console.error(`[ARES-NET-ERROR] Network layer block:`, error);
    return {
      content: [
        {
          type: 'text',
          text: `[ARES NET ERROR] Failed to complete pipeline execution: ${error.message}`,
        },
      ],
      isError: true,
    } as CallToolResult;
  }
}

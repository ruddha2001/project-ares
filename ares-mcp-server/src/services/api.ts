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

    const token = process.env.COPILOT_GITHUB_TOKEN || process.env.GITHUB_PAT || process.env.GITHUB_TOKEN || '';
    const copilotModel = process.env.COPILOT_MODEL || '';

    const routingConfiguration = {
      EMBEDDING_GENERATION: process.env.EMBEDDING_GENERATION || 'ollama',
      KNOWLEDGE_RETRIEVAL_RANKING: process.env.KNOWLEDGE_RETRIEVAL_RANKING || 'ollama',
      COMPLIANCE_EVALUATION: process.env.COMPLIANCE_EVALUATION || 'gemini-flash-3.5',
      PR_SYNTHESIS: process.env.PR_SYNTHESIS || 'claude-opus-4.6',
    };

    const initResponse = await fetch(`${BACKEND_URL}/api/v1/jobs`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-ARES-GH-PAT': token,
        ...(copilotModel ? { 'X-ARES-COPILOT-MODEL': copilotModel } : {}),
      },
      body: JSON.stringify({
        projectId: payload.projectId || null,
        repositoryUrl: payload.repoUrl || null,
        featureSpecUrl: payload.featureSpecUrl || null,
        rawSpecificationText: payload.taskDescription || null,
        localGitDiff: payload.gitDiff || null,
        routingConfiguration,
      }),
    });

    if (!initResponse.ok) {
      throw new Error(
        `Backend rejected job initialization: ${await initResponse.text()}`,
      );
    }

    let jobState = (await initResponse.json()) as JobResponse;
    const jobId = jobState.jobId;

    console.error(
      `[ARES-LOG] Job [${jobId}] securely initialized. Entering telemetry polling track...`,
    );

    const triggerResponse = await fetch(
      `${BACKEND_URL}/api/v1/jobs/${jobId}/${endpoint}`,
      {
        method: 'POST',
        headers: {
          'Content-Length': '0',
          'X-ARES-GH-PAT': token,
          ...(copilotModel ? { 'X-ARES-COPILOT-MODEL': copilotModel } : {}),
        },
      },
    );

    if (triggerResponse.status !== 202) {
      throw new Error(
        `Engine rejected processing trigger request for Job ID: ${jobId}`,
      );
    }

    console.error(
      `[ARES-LOG] Processing triggered asynchronously. Engaging telemetry tracker...`,
    );

    while (jobState.status !== 'COMPLETED' && jobState.status !== 'FAILED') {
      await Bun.sleep(POLLING_INTERVAL_MS);

      const pollResponse = await fetch(`${BACKEND_URL}/api/v1/jobs/${jobId}`, {
        headers: {
          'X-ARES-GH-PAT': token,
          ...(copilotModel ? { 'X-ARES-COPILOT-MODEL': copilotModel } : {}),
        },
      });
      if (!pollResponse.ok) {
        throw new Error(`Lost track of cluster state matrix for ID: ${jobId}`);
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
          text: jobState.payload || '🚀 Processing complete.',
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

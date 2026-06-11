import { execSync } from 'child_process';
import { executeAresJobFlow } from '../services/api.js';
import { resolveNotionTaskDescription } from '../services/notion.js';

function getLocalRepoUrl(): string | null {
  try {
    return execSync('git remote get-url origin', { encoding: 'utf8' }).trim();
  } catch (err) {
    console.error('[ARES-TOOL] Failed to get git remote URL:', err);
    return null;
  }
}

export async function handlePlanning(args: any): Promise<any> {
  console.error(`[ARES-TOOL] Handing off task to planning strategy engine...`);
  
  args.repoUrl = getLocalRepoUrl();
  
  if (args.taskDescription) {
    try {
      args.taskDescription = await resolveNotionTaskDescription(args.taskDescription);
    } catch (error: any) {
      console.error(`[ARES-TOOL] Failed to resolve Notion task description:`, error);
      return {
        content: [
          {
            type: 'text',
            text: `❌ Failed to resolve Notion task description: ${error.message}`,
          },
        ],
        isError: true,
      };
    }
  }

  return await executeAresJobFlow('planning', args);
}

import { executeAresJobFlow } from '../services/api.js';
import { resolveNotionTaskDescription } from '../services/notion.js';

export async function handlePlanning(args: any): Promise<any> {
  console.error(`[ARES-TOOL] Handing off task to planning strategy engine...`);
  
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

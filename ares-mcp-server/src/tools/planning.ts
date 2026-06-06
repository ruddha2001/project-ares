import { executeAresJobFlow } from '../services/api.js';

export async function handlePlanning(args: any): Promise<any> {
  console.error(`[ARES-TOOL] Handing off task to planning strategy engine...`);
  return await executeAresJobFlow('planning', args);
}

import { executeAresJobFlow } from '../services/api.js';

export async function handleVerification(args: any): Promise<any> {
  console.error(`[ARES-TOOL] Passing diff data down to compliance gauntlet...`);
  return await executeAresJobFlow('verification', args);
}

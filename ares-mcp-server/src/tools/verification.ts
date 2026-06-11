import { execSync } from 'child_process';
import { executeAresJobFlow } from '../services/api.js';

function getLocalRepoUrl(): string | null {
  try {
    return execSync('git remote get-url origin', { encoding: 'utf8' }).trim();
  } catch (err) {
    console.error('[ARES-TOOL] Failed to get git remote URL:', err);
    return null;
  }
}

export async function handleVerification(args: any): Promise<any> {
  console.error(`[ARES-TOOL] Passing diff data down to compliance gauntlet...`);
  args.repoUrl = getLocalRepoUrl();
  return await executeAresJobFlow('verification', args);
}

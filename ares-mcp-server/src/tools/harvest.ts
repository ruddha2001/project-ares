import { harvestWorkspace } from '../services/harvester.js';

export async function handleHarvest(args: any): Promise<any> {
  console.error('[ARES-TOOL] Triggering manual workspace harvest...');
  const workspacePath = args.workspacePath || process.cwd();
  const force = !!args.force;

  try {
    const result = await harvestWorkspace(workspacePath, { force });
    return {
      content: [
        {
          type: 'text',
          text: `✅ Workspace harvested successfully!\n` +
            `- Files processed (updated/added): ${result.filesProcessed}\n` +
            `- Total chunks inserted: ${result.chunksInserted}\n` +
            `- Execution time: ${result.elapsedMs}ms`,
        },
      ],
    };
  } catch (error: any) {
    console.error('[ARES-TOOL] Workspace harvest failed:', error);
    return {
      content: [
        {
          type: 'text',
          text: `❌ Workspace harvest failed: ${error.message}`,
        },
      ],
      isError: true,
    };
  }
}

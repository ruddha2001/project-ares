import packageJson from '../package.json';
import { PlanningSchema, VerificationSchema, HarvestSchema } from './types/index.js';
import { handlePlanning } from './tools/planning.js';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { handleVerification } from './tools/verification.js';
import { handleHarvest } from './tools/harvest.js';
import { getDatabase, runVerificationSuite } from './services/database.js';
import { harvestWorkspace, loadEnvironments } from './services/harvester.js';
import { fileURLToPath } from 'url';

const server = new McpServer({
  name: 'ares-mcp-server',
  version: packageJson.version,
});

server.registerTool(
  'ares_planning',
  {
    title: 'Ares Planning Tool',
    description:
      'Creates an implementation plan based on the requirement document and precise context',
    inputSchema: PlanningSchema,
  },
  handlePlanning,
);

server.registerTool(
  'ares_verification',
  {
    title: 'Ares Verification Tool',
    description:
      'Verifies the generated code so that it aligns with the workspace policies and requirements',
    inputSchema: VerificationSchema,
  },
  handleVerification,
);

server.registerTool(
  'ares_harvest',
  {
    title: 'Ares Harvest Tool',
    description:
      'Recursively scans the local project workspace, chunks the files, tracks uncommitted Git states, and updates vector embeddings in the local database',
    inputSchema: HarvestSchema,
  },
  handleHarvest,
);

async function run() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error(
    '[ARES-LOG] Modular Bun MCP Sidecar securely bonded over processes.',
  );

  // 1. Load environments from process.cwd() first to check for project-local overrides
  loadEnvironments(process.cwd());

  // 2. Resolve workspace path dynamically
  const wasEnvExplicit = !!process.env.ARES_WORKSPACE_PATH;
  let workspacePath = process.env.ARES_WORKSPACE_PATH || process.cwd();

  // 3. Query client-provided workspace roots via listRoots() if supported
  try {
    const clientCaps = server.server.getClientCapabilities();
    if (clientCaps?.roots && !wasEnvExplicit) {
      console.error('[ARES-LOG] Querying active workspace roots from client...');
      const rootsResult = await server.server.listRoots();
      if (rootsResult && rootsResult.roots && rootsResult.roots.length > 0) {
        const rootUri = rootsResult.roots[0].uri;
        if (rootUri.startsWith('file://')) {
          workspacePath = fileURLToPath(rootUri);
          console.error(`[ARES-LOG] Client workspace root resolved: ${workspacePath}`);
        }
      }
    }
  } catch (err: any) {
    console.error('[ARES-HARVESTER] Failed to query roots from client:', err.message);
  }

  // 3. Set global environment variable for database and configuration loading
  process.env.ARES_WORKSPACE_PATH = workspacePath;

  // 4. Initialize Local SQLite Tier now that the workspace path is resolved
  console.error('[ARES-LOG] Booting Local SQLite Tier...');
  const db = getDatabase();
  runVerificationSuite(db);

  // 5. Apply safety check before background harvesting to prevent indexing home/system directory
  const normalizedPath = workspacePath.replace(/\/$/, '');
  const homeDir = process.env.HOME || '';
  const isSystemRootOrHome =
    normalizedPath === '' ||
    normalizedPath === '/' ||
    (homeDir && normalizedPath === homeDir.replace(/\/$/, '')) ||
    (normalizedPath.startsWith('/home') && normalizedPath.split('/').length <= 3);

  if (isSystemRootOrHome && !wasEnvExplicit) {
    console.error('[ARES-HARVESTER] Safe Guard: Background harvest bypassed because resolved workspacePath appears to be a user home or system root directory.');
  } else {
    console.error(`[ARES-LOG] Initiating non-blocking background workspace harvest for: ${workspacePath}`);
    harvestWorkspace(workspacePath).catch((err) => {
      console.error('[ARES-HARVESTER] Background harvest failed:', err);
    });
  }
}

run().catch((err) => {
  console.error('[ARES-CRITICAL] Failed to execute modular MCP pipeline:', err);
  process.exit(1);
});


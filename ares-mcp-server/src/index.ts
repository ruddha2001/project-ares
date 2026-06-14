import packageJson from '../package.json';
import { PlanningSchema, VerificationSchema, HarvestSchema } from './types/index.js';
import { handlePlanning } from './tools/planning.js';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { handleVerification } from './tools/verification.js';
import { handleHarvest } from './tools/harvest.js';
import { getDatabase, runVerificationSuite } from './services/database.js';
import { harvestWorkspace } from './services/harvester.js';

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
  console.error('[ARES-LOG] Booting Local SQLite Tier...');
  const db = getDatabase();
  runVerificationSuite(db);

  // Start background non-blocking workspace harvest
  console.error('[ARES-LOG] Initiating non-blocking background workspace harvest...');
  harvestWorkspace(process.cwd()).catch((err) => {
    console.error('[ARES-HARVESTER] Background harvest failed:', err);
  });

  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error(
    '[ARES-LOG] Modular Bun MCP Sidecar securely bonded over processes.',
  );
}

run().catch((err) => {
  console.error('[ARES-CRITICAL] Failed to execute modular MCP pipeline:', err);
  process.exit(1);
});


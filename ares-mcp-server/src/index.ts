import packageJson from '../package.json';
import { PlanningSchema, VerificationSchema } from './types';
import { handlePlanning } from './tools/planning';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { handleVerification } from './tools/verification';

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

async function run() {
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

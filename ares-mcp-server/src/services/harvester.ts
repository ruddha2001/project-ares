import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import glob from 'fast-glob';
import { getDatabase } from './database.js';

/**
 * Normalizes or pads/truncates a vector to a target dimension.
 */
export function normalizeVector(vector: number[], targetDim = 768): Float32Array {
  const result = new Float32Array(targetDim);
  for (let i = 0; i < targetDim; i++) {
    result[i] = i < vector.length ? vector[i] : 0.0;
  }
  return result;
}

/**
 * Computes a high-performance hash of a string using Bun's native Wyhash implementation.
 * Faster execution footprint for high-frequency workspace drift checks.
 */
export function computeHash(content: string): string {
  return Bun.hash(content).toString(16);
}

/**
 * Loads environment variables from the workspace root and the server subdirectory.
 */
export function loadEnvironments(workspacePath: string): void {
  const envsToLoad = [
    path.resolve(workspacePath, '.env'),
    path.resolve(workspacePath, 'ares-mcp-server', '.env'),
    ...(process.env.ARES_WORKSPACE_PATH ? [
      path.resolve(process.env.ARES_WORKSPACE_PATH, '.env'),
      path.resolve(process.env.ARES_WORKSPACE_PATH, 'ares-mcp-server', '.env'),
    ] : []),
    path.resolve(process.cwd(), '.env'),
    path.resolve(process.cwd(), 'ares-mcp-server', '.env'),
  ];

  for (const envPath of envsToLoad) {
    if (fs.existsSync(envPath)) {
      try {
        const content = fs.readFileSync(envPath, 'utf8');
        const lines = content.split('\n');
        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
            const idx = trimmed.indexOf('=');
            const key = trimmed.slice(0, idx).trim();
            let val = trimmed.slice(idx + 1).trim();
            if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
              val = val.slice(1, -1);
            }
            if (!(key in process.env) || key === 'ARES_WORKSPACE_PATH') {
              process.env[key] = val;
            }
          }
        }
      } catch (e) {
        console.error(`[ARES-HARVESTER] Failed to read env file at ${envPath}:`, e);
      }
    }
  }
}

/**
 * Parses a .gitignore file and maps its entries to glob ignore patterns.
 */
export function parseGitignore(gitignorePath: string): string[] {
  if (!fs.existsSync(gitignorePath)) return [];
  try {
    const content = fs.readFileSync(gitignorePath, 'utf8');
    return content.split('\n')
      .map(line => line.trim())
      .filter(line => line && !line.startsWith('#'))
      .map(rule => {
        let globPattern = rule;
        if (globPattern.startsWith('/')) {
          globPattern = globPattern.substring(1);
        } else if (!globPattern.startsWith('**/')) {
          globPattern = '**/' + globPattern;
        }
        
        if (globPattern.endsWith('/')) {
          globPattern = globPattern + '**';
        }
        return globPattern;
      });
  } catch (e) {
    console.error(`[ARES-HARVESTER] Failed to parse gitignore at ${gitignorePath}:`, e);
    return [];
  }
}

let Parser: any;
const loadedLanguages = new Map<string, any>();
const activeCodebaseExtensions = new Set<string>();
let isTreeSitterInitialized = false;

export function initTreeSitter() {
  if (isTreeSitterInitialized) return;
  
  try {
    Parser = import.meta.require('tree-sitter');
  } catch (err: any) {
    throw new Error(`[ARES-HARVESTER] Critical Error: Failed to load native 'tree-sitter' bindings: ${err.message}`);
  }

  const supportedGrammarsEnv = process.env.SUPPORTED_GRAMMARS;
  if (!supportedGrammarsEnv) {
    throw new Error("[ARES-HARVESTER] Critical Error: Environment variable 'SUPPORTED_GRAMMARS' is not configured.");
  }

  const configPath = path.resolve(import.meta.dir, '../../grammars.json');
  if (!fs.existsSync(configPath)) {
    throw new Error(`[ARES-HARVESTER] Critical Error: Grammars config file not found at ${configPath}`);
  }

  let config: Record<string, { npmPackage: string; extensions: string[]; languageAccessProperty: string }>;
  try {
    config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  } catch (err: any) {
    throw new Error(`[ARES-HARVESTER] Critical Error: Failed to parse grammars config file: ${err.message}`);
  }

  const supportedList = supportedGrammarsEnv
    .split(',')
    .map(s => s.trim().toLowerCase())
    .filter(Boolean);

  for (const langName of supportedList) {
    const grammarConfig = config[langName];
    if (!grammarConfig) {
      throw new Error(`[ARES-HARVESTER] Critical Error: Grammar '${langName}' is configured in SUPPORTED_GRAMMARS but not found in grammars.json.`);
    }

    try {
      const pkg = import.meta.require(grammarConfig.npmPackage);
      let lang: any;
      const prop = grammarConfig.languageAccessProperty;
      if (prop === 'default') {
        lang = pkg.default || pkg;
      } else if (prop) {
        lang = pkg[prop] || pkg;
      } else {
        lang = pkg;
      }

      if (!lang) {
        throw new Error(`Grammar package did not export language under property '${prop}'`);
      }

      for (const ext of grammarConfig.extensions) {
        loadedLanguages.set(ext, lang);
        activeCodebaseExtensions.add(ext);
      }
      console.error(`[ARES-HARVESTER] Successfully loaded grammar package '${grammarConfig.npmPackage}' for ${langName}.`);
    } catch (err: any) {
      throw new Error(`[ARES-HARVESTER] Critical Error: Failed to load configured grammar package '${grammarConfig.npmPackage}': ${err.message}`);
    }
  }

  isTreeSitterInitialized = true;
}

function getBoundaryCrossingCounts(
  rootNode: any,
  lineCount: number,
  maxDepth = 15
): number[] {
  const crossingCounts = new Array(lineCount).fill(0);

  const structuralTypes = new Set([
    'class_declaration',
    'method_definition',
    'function_declaration',
    'interface_declaration',
    'type_alias_declaration',
    'enum_declaration',
    'arrow_function',
    'lexical_declaration',
    'export_statement',
    'export_declaration',
    'class_declaration',
    'interface_declaration',
    'method_declaration',
    'constructor_declaration',
    'record_declaration',
    'enum_declaration',
    'class_definition',
    'function_definition',
  ]);

  const stack: { node: any; depth: number }[] = [{ node: rootNode, depth: 0 }];

  while (stack.length > 0) {
    const { node, depth } = stack.pop()!;
    if (!node) continue;
    if (depth > maxDepth) continue;

    const isStructural = structuralTypes.has(node.type);
    if (isStructural) {
      const startLine = node.startPosition.row;
      const endLine = node.endPosition.row;
      for (let i = startLine; i < endLine; i++) {
        if (i >= 0 && i < lineCount) {
          crossingCounts[i]++;
        }
      }
    }

    const childCount = node.childCount;
    for (let i = 0; i < childCount; i++) {
      const child = node.child(i);
      if (child) {
        stack.push({ node: child, depth: depth + 1 });
      }
    }
  }

  return crossingCounts;
}

function chunkTextAST(
  text: string,
  lang: any,
  maxChunkCharLength = 2000,
  overlapRatio = 0.1
): string[] {
  const parser = new Parser();
  parser.setLanguage(lang);
  const tree = parser.parse(text);
  const rootNode = tree.rootNode;

  const lines = text.split('\n');
  const lineCount = lines.length;
  const crossingCounts = getBoundaryCrossingCounts(rootNode, lineCount);

  const chunks: string[] = [];
  const overlapTarget = Math.floor(maxChunkCharLength * overlapRatio);

  let currentLineIdx = 0;

  while (currentLineIdx < lineCount) {
    let limitLineIdx = currentLineIdx;
    let currentLength = 0;

    while (limitLineIdx < lineCount) {
      const lineLen = lines[limitLineIdx].length + 1;
      if (currentLength + lineLen > maxChunkCharLength) {
        break;
      }
      currentLength += lineLen;
      limitLineIdx++;
    }

    if (limitLineIdx === currentLineIdx) {
      limitLineIdx = currentLineIdx + 1;
    }

    if (limitLineIdx >= lineCount) {
      const chunkText = lines.slice(currentLineIdx, lineCount).join('\n');
      if (chunkText.trim().length > 0) {
        chunks.push(chunkText);
      }
      break;
    }

    const chunkLinesCount = limitLineIdx - currentLineIdx;
    const windowSize = Math.max(5, Math.floor(chunkLinesCount * 0.3));
    const windowStart = Math.max(currentLineIdx, limitLineIdx - windowSize);

    let bestSplitIdx = limitLineIdx - 1;
    let minCrossing = Infinity;

    for (let i = windowStart; i < limitLineIdx; i++) {
      const crossing = crossingCounts[i];
      if (crossing < minCrossing || (crossing === minCrossing && i > bestSplitIdx)) {
        minCrossing = crossing;
        bestSplitIdx = i;
      }
    }

    const chunkText = lines.slice(currentLineIdx, bestSplitIdx + 1).join('\n');
    if (chunkText.trim().length > 0) {
      chunks.push(chunkText);
    }

    if (overlapRatio > 0) {
      let overlapLen = 0;
      let j = bestSplitIdx;
      while (j > currentLineIdx && overlapLen < overlapTarget) {
        overlapLen += lines[j].length + 1;
        j--;
      }
      
      const overlapWindowStart = Math.max(currentLineIdx + 1, j - 2);
      const overlapWindowEnd = Math.min(bestSplitIdx, j + 2);
      let bestOverlapStart = bestSplitIdx;
      let minOverlapCrossing = Infinity;

      for (let k = overlapWindowStart; k <= overlapWindowEnd; k++) {
        const crossing = crossingCounts[k - 1];
        if (crossing < minOverlapCrossing || (crossing === minOverlapCrossing && k < bestOverlapStart)) {
          minOverlapCrossing = crossing;
          bestOverlapStart = k;
        }
      }

      currentLineIdx = bestOverlapStart;
    } else {
      currentLineIdx = bestSplitIdx + 1;
    }
  }

  return chunks;
}

export function chunkTextLineFallback(
  text: string,
  maxChunkCharLength = 2000,
  overlapRatio = 0.1
): string[] {
  const lines = text.split('\n');
  const chunks: string[] = [];
  const overlapTarget = Math.floor(maxChunkCharLength * overlapRatio);

  let currentChunkLines: string[] = [];
  let currentChunkLength = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const lineLength = line.length + 1; // +1 for newline

    if (lineLength > maxChunkCharLength) {
      if (currentChunkLines.length > 0) {
        chunks.push(currentChunkLines.join('\n'));
        currentChunkLines = [];
        currentChunkLength = 0;
      }
      let start = 0;
      while (start < line.length) {
        chunks.push(line.slice(start, start + maxChunkCharLength));
        start += maxChunkCharLength - overlapTarget;
      }
      continue;
    }

    if (currentChunkLength + lineLength > maxChunkCharLength) {
      chunks.push(currentChunkLines.join('\n'));

      let overlapLength = 0;
      const overlapLines: string[] = [];
      let j = i - 1;
      while (j >= 0 && currentChunkLines.length > 0) {
        const prevLine = lines[j];
        if (overlapLength + prevLine.length + 1 > overlapTarget) {
          break;
        }
        overlapLines.unshift(prevLine);
        overlapLength += prevLine.length + 1;
        j--;
      }

      currentChunkLines = [...overlapLines, line];
      currentChunkLength = overlapLength + lineLength;
    } else {
      currentChunkLines.push(line);
      currentChunkLength += lineLength;
    }
  }

  if (currentChunkLines.length > 0) {
    chunks.push(currentChunkLines.join('\n'));
  }

  return chunks.filter(c => c.trim().length > 0);
}

/**
 * Generates overlapping text chunks on line boundaries or AST boundaries.
 */
export function chunkText(
  text: string,
  maxChunkCharLength = 2000,
  overlapRatio = 0.1,
  filePath?: string
): string[] {
  const ext = filePath ? path.extname(filePath).toLowerCase() : '';
  
  initTreeSitter();

  const isCodebaseFile = ext && activeCodebaseExtensions.has(ext);

  if (isCodebaseFile) {
    if (!Parser) {
      throw new Error(`[ARES-HARVESTER] Critical Error: Tree-sitter parser not initialized for codebase file: ${filePath}`);
    }
    const lang = loadedLanguages.get(ext);
    if (!lang) {
      throw new Error(`[ARES-HARVESTER] Critical Error: Tree-sitter grammar not loaded for extension '${ext}' of codebase file: ${filePath}`);
    }
    return chunkTextAST(text, lang, maxChunkCharLength, overlapRatio);
  } else {
    return chunkTextLineFallback(text, maxChunkCharLength, overlapRatio);
  }
}


/**
 * Queries Git CLI to retrieve the uncommitted diff.
 */
export function getGitDiff(workspacePath: string): string {
  try {
    return execSync('git diff HEAD', { encoding: 'utf8', cwd: workspacePath, stdio: 'pipe' }).trim();
  } catch (err) {
    console.error('[ARES-HARVESTER] Failed to execute git diff HEAD:', err);
    return '';
  }
}

/**
 * In-memory cache for the short-lived Copilot session token.
 * Avoids a token exchange on every chunk embedding call.
 */
let copilotTokenCache: { token: string; expiresAt: number } | null = null;

/**
 * Exchanges the GitHub PAT for a short-lived Copilot session token.
 * Result is cached in-memory with a 5-minute safety buffer before expiry.
 */
async function exchangeCopilotToken(githubToken: string): Promise<string> {
  const now = Date.now();

  if (copilotTokenCache && copilotTokenCache.expiresAt > now) {
    return copilotTokenCache.token;
  }

  const response = await fetch('https://api.github.com/copilot_internal/v2/token', {
    method: 'GET',
    headers: {
      'Authorization': `token ${githubToken}`,
      'Accept': 'application/json',
      'Editor-Version': 'ARES/2.0',
      'Editor-Plugin-Version': 'ares-mcp/1.0',
    },
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      `[ARES-HARVESTER] Copilot token exchange failed (HTTP ${response.status}).\n` +
      `Response: ${body.slice(0, 200)}\n` +
      `Ensure your GITHUB_PAT is valid and has Copilot access, or unset COPILOT_EMBEDDING_MODEL to use Ollama instead.`
    );
  }

  const data = await response.json() as { token: string; expires_at: string };

  if (!data.token) {
    throw new Error(
      `[ARES-HARVESTER] Copilot token exchange returned no token.\n` +
      `Ensure your GITHUB_PAT is valid and has Copilot access, or unset COPILOT_EMBEDDING_MODEL to use Ollama instead.`
    );
  }

  // Cache with a 5-minute safety buffer before the stated expiry
  const BUFFER_MS = 5 * 60 * 1000;
  const expiresAt = data.expires_at
    ? new Date(data.expires_at).getTime() - BUFFER_MS
    : now + 25 * 60 * 1000; // default: 25 min if no expiry provided

  copilotTokenCache = { token: data.token, expiresAt };
  console.error('[ARES-HARVESTER] Copilot session token acquired and cached.');
  return data.token;
}

/**
 * Generates an embedding vector via the Copilot API OpenAI-compatible embeddings endpoint.
 * Uses GITHUB_PAT to exchange for a short-lived session token, then calls the real
 * embedding endpoint. Errors out loudly on any failure — does NOT fall back silently.
 */
async function fetchCopilotEmbedding(text: string, copilotModel: string): Promise<number[]> {
  const githubToken = process.env.COPILOT_GITHUB_TOKEN || process.env.GITHUB_PAT || process.env.GITHUB_TOKEN || '';
  if (!githubToken) {
    throw new Error(
      `[ARES-HARVESTER] COPILOT_EMBEDDING_MODEL is set to "${copilotModel}" but no GitHub token was found.\n` +
      `Set GITHUB_PAT in your environment, or unset COPILOT_EMBEDDING_MODEL to use Ollama instead.`
    );
  }

  const sessionToken = await exchangeCopilotToken(githubToken);

  const response = await fetch('https://api.githubcopilot.com/embeddings', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${sessionToken}`,
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Editor-Version': 'ARES/2.0',
      'Editor-Plugin-Version': 'ares-mcp/1.0',
      'Copilot-Integration-Id': 'ares-mcp-server',
    },
    body: JSON.stringify({
      model: copilotModel,
      input: text,
      encoding_format: 'float',
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    // Invalidate cached token on auth errors so next attempt re-exchanges
    if (response.status === 401 || response.status === 403) {
      copilotTokenCache = null;
    }
    throw new Error(
      `[ARES-HARVESTER] Copilot embeddings API returned HTTP ${response.status}.\n` +
      `Response: ${body.slice(0, 200)}\n` +
      `Check your GITHUB_PAT and COPILOT_EMBEDDING_MODEL ("${copilotModel}") configuration, or unset COPILOT_EMBEDDING_MODEL to use Ollama instead.`
    );
  }

  const data = await response.json() as { data: { embedding: number[] }[] };

  if (!data.data || !data.data[0] || !Array.isArray(data.data[0].embedding)) {
    throw new Error(
      `[ARES-HARVESTER] Copilot embeddings API returned an unexpected response format.\n` +
      `Raw response: ${JSON.stringify(data).slice(0, 300)}`
    );
  }

  return data.data[0].embedding;
}


/**
 * Generates an embedding vector using a local Ollama instance.
 */
async function fetchOllamaEmbedding(text: string, model: string, url: string): Promise<number[]> {
  const endpoints = [`${url.replace(/\/$/, '')}/api/embed`, `${url.replace(/\/$/, '')}/api/embeddings`];
  let lastError: any = null;

  for (const endpoint of endpoints) {
    try {
      const isEmbed = endpoint.endsWith('/api/embed');
      const payload = isEmbed
        ? { model, input: text }
        : { model, prompt: text };

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(`Ollama returned status ${response.status}: ${await response.text()}`);
      }

      const data = await response.json() as any;
      const vector = isEmbed ? data.embeddings?.[0] : data.embedding;
      if (!vector || !Array.isArray(vector)) {
        throw new Error(`Invalid response format from Ollama endpoint ${endpoint}`);
      }
      return vector;
    } catch (err) {
      lastError = err;
    }
  }
  throw lastError || new Error(`Failed to query Ollama at ${url}`);
}

/**
 * Resolves the embedding model and routes the generation to either Copilot or Ollama.
 */
export async function getEmbedding(text: string, retries = 3, backoffMs = 500): Promise<Float32Array> {
  const copilotModel = process.env.COPILOT_EMBEDDING_MODEL;
  const modelName = process.env.CODE_EMBEDDING_MODEL || 'nomic-embed-text';

  let ollamaUrl = process.env.INFERENCE_URL || 'http://localhost:11434';
  if (ollamaUrl.includes('inference-sidecar')) {
    ollamaUrl = 'http://localhost:11434';
  }

  for (let i = 0; i < retries; i++) {
    try {
      let rawVector: number[];
      if (copilotModel) {
        rawVector = await fetchCopilotEmbedding(text, copilotModel);
      } else {
        rawVector = await fetchOllamaEmbedding(text, modelName, ollamaUrl);
      }
      return normalizeVector(rawVector, 768);
    } catch (err: any) {
      console.error(`[ARES-HARVESTER] Embedding attempt ${i + 1} failed: ${err.message}`);
      if (i < retries - 1) {
        const delay = backoffMs * Math.pow(2, i);
        await Bun.sleep(delay);
      } else {
        throw new Error(`Failed to generate embedding: ${err.message}`);
      }
    }
  }
  throw new Error('Failed to generate embedding');
}

/**
 * Limits concurrent async tasks.
 */
async function parallelLimit<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = [];
  const executing: Promise<any>[] = [];
  for (const item of items) {
    const p = Promise.resolve().then(() => fn(item));
    results.push(p as any);
    if (limit <= items.length) {
      const e: Promise<any> = p.then(() => executing.splice(executing.indexOf(e), 1));
      executing.push(e);
      if (executing.length >= limit) {
        await Promise.race(executing);
      }
    }
  }
  return Promise.all(results);
}

const WHITELISTED_EXTENSIONS = new Set([
  '.java', '.ts', '.js', '.tsx', '.jsx', '.py', '.rs', '.go', '.c', '.cpp', '.h', '.hpp', '.cs',
  '.gradle', '.xml', '.properties', '.yaml', '.yml', '.sql', '.sh', '.md', '.txt'
]);

/**
 * Scans, chunks, embeds, and persists the local workspace codebase.
 */
export async function harvestWorkspace(
  workspacePath: string,
  options: { force?: boolean } = {}
): Promise<{ filesProcessed: number; chunksInserted: number; elapsedMs: number }> {
  const startTime = Date.now();
  loadEnvironments(workspacePath);
  initTreeSitter();

  console.error(`[ARES-HARVESTER] Beginning workspace harvesting for ${workspacePath}...`);

  // 1. Gather all files in the directory using fast-glob
  const gitignorePath = path.resolve(workspacePath, '.gitignore');
  const gitignoreRules = parseGitignore(gitignorePath);

  const defaultIgnores = [
    '**/node_modules/**',
    '**/.git/**',
    '**/.gitignore',
    '**/.ares/**',
    '**/dist/**',
    '**/build/**',
    '**/target/**',
    '**/out/**',
    '**/.idea/**',
    '**/.vscode/**',
    '**/*.class',
    '**/*.jar',
    '**/*.war',
    '**/*.zip',
    '**/*.tar.gz',
    '**/*.png',
    '**/*.jpg',
    '**/*.jpeg',
    '**/*.gif',
    '**/*.ico',
    '**/*.pdf',
    '**/*.db',
    '**/*.sqlite',
    '**/bun.lock',
    '**/package-lock.json',
    '**/yarn.lock'
  ];

  const allFiles = await glob('**/*', {
    cwd: workspacePath,
    absolute: true,
    dot: true,
    ignore: [...defaultIgnores, ...gitignoreRules],
    onlyFiles: true
  });

  // Filter out files > 500KB unless whitelisted (max 5MB for whitelisted code)
  const filesToProcess: { absolutePath: string; relativePath: string }[] = [];
  const defaultSizeLimit = 500 * 1024; // 500KB
  const whitelistedSizeLimit = 5 * 1024 * 1024; // 5MB

  for (const file of allFiles) {
    try {
      const stats = fs.statSync(file);
      const ext = path.extname(file).toLowerCase();
      const isCode = WHITELISTED_EXTENSIONS.has(ext);
      const limit = isCode ? whitelistedSizeLimit : defaultSizeLimit;

      if (stats.size <= limit) {
        filesToProcess.push({
          absolutePath: file,
          relativePath: path.relative(workspacePath, file)
        });
      } else {
        console.error(`[ARES-HARVESTER] Skipping large file (> ${limit / 1024}KB): ${file}`);
      }
    } catch {
      // Ignore stat error
    }
  }

  // 2. Query DB to read previously indexed files and hashes
  const db = getDatabase();
  const existingFilesMap = new Map<string, string>(); // filePath -> fileHash
  try {
    const rows = db.query('SELECT DISTINCT file_path, file_hash FROM local_workspace_embeddings').all() as { file_path: string; file_hash: string }[];
    for (const row of rows) {
      existingFilesMap.set(row.file_path, row.file_hash);
    }
  } catch (err) {
    // If table doesn't exist, we'll continue since DB init is run on boot
    console.error('[ARES-HARVESTER] Database query error, proceeding with empty cache:', err);
  }

  // 3. Determine delta updates
  const filesToReindex: { absolutePath: string; relativePath: string; hash: string }[] = [];
  const scannedPathsSet = new Set<string>();

  for (const file of filesToProcess) {
    scannedPathsSet.add(file.relativePath);
    try {
      const content = fs.readFileSync(file.absolutePath, 'utf8');
      const hash = computeHash(content);
      const existingHash = existingFilesMap.get(file.relativePath);

      if (options.force || existingHash !== hash) {
        filesToReindex.push({ ...file, hash });
      }
    } catch (err: any) {
      console.error(`[ARES-HARVESTER] Failed to read ${file.absolutePath}: ${err.message}`);
    }
  }

  // Files deleted from workspace
  const deletedPaths: string[] = [];
  for (const pathInDb of existingFilesMap.keys()) {
    if (pathInDb !== 'git-diff' && !scannedPathsSet.has(pathInDb)) {
      deletedPaths.push(pathInDb);
    }
  }

  console.error(`[ARES-HARVESTER] Files: Scanned ${filesToProcess.length} | To Re-Index ${filesToReindex.length} | Deleted ${deletedPaths.length}`);

  // 4. Git Diff Harvest
  let diffFileIndexData: { filePath: string; fileHash: string; chunks: { chunkId: string; contentChunk: string; embedding: Float32Array }[] } | null = null;
  const diffContent = getGitDiff(workspacePath);
  const diffHash = diffContent ? computeHash(diffContent) : '';
  const existingDiffHash = existingFilesMap.get('git-diff');

  if (diffContent && (options.force || existingDiffHash !== diffHash)) {
    const diffChunks = chunkText(diffContent, 2000, 0.1);
    console.error(`[ARES-HARVESTER] Chunking git diff into ${diffChunks.length} chunks...`);

    const chunksWithEmbeddings = await parallelLimit(
      diffChunks.map((c, idx) => ({ content: c, idx })),
      5, // Concurrency limit
      async (item) => {
        const embedding = await getEmbedding(item.content);
        return {
          chunkId: `git-diff#${item.idx}`,
          contentChunk: item.content,
          embedding
        };
      }
    );

    diffFileIndexData = {
      filePath: 'git-diff',
      fileHash: diffHash,
      chunks: chunksWithEmbeddings
    };
  } else if (!diffContent && existingDiffHash) {
    // Diff was cleared
    deletedPaths.push('git-diff');
  }

  // 5. Generate chunks & embeddings for codebase files
  const fileIndexData: { filePath: string; fileHash: string; chunks: { chunkId: string; contentChunk: string; embedding: Float32Array }[] }[] = [];

  for (const file of filesToReindex) {
    try {
      const content = fs.readFileSync(file.absolutePath, 'utf8');
      const chunks = chunkText(content, 2000, 0.1, file.relativePath);
      if (chunks.length === 0) continue;

      const chunksWithEmbeddings = await parallelLimit(
        chunks.map((c, idx) => ({ content: c, idx })),
        5,
        async (item) => {
          const embedding = await getEmbedding(item.content);
          return {
            chunkId: `${file.relativePath}#${item.idx}`,
            contentChunk: item.content,
            embedding
          };
        }
      );

      fileIndexData.push({
        filePath: file.relativePath,
        fileHash: file.hash,
        chunks: chunksWithEmbeddings
      });
    } catch (err: any) {
      console.error(`[ARES-HARVESTER] Failed to process chunks/embeddings for ${file.relativePath}: ${err.message}`);
    }
  }

  // 6. DB Writing inside single atomic transaction
  let totalChunksInserted = 0;
  const deleteStmt = db.prepare('DELETE FROM local_workspace_embeddings WHERE file_path = ?');
  const insertStmt = db.prepare(`
    INSERT INTO local_workspace_embeddings (chunk_id, file_path, file_hash, content_chunk, embedding)
    VALUES (?, ?, ?, ?, ?)
  `);

  // Transaction for deleting missing files
  if (deletedPaths.length > 0) {
    const deleteTx = db.transaction(() => {
      for (const filePath of deletedPaths) {
        deleteStmt.run(filePath);
      }
    });
    deleteTx();
  }

  // Transaction for batching re-indexed files (50 files per batch) to avoid exclusive database lock starvations
  const BATCH_SIZE = 50;
  for (let i = 0; i < fileIndexData.length; i += BATCH_SIZE) {
    const batch = fileIndexData.slice(i, i + BATCH_SIZE);
    const writeBatchTx = db.transaction(() => {
      for (const file of batch) {
        deleteStmt.run(file.filePath);
        for (const chunk of file.chunks) {
          insertStmt.run(chunk.chunkId, file.filePath, file.fileHash, chunk.contentChunk, chunk.embedding);
          totalChunksInserted++;
        }
      }
    });
    writeBatchTx();
  }

  // Transaction for git-diff chunks
  if (diffFileIndexData) {
    const diffTx = db.transaction(() => {
      deleteStmt.run('git-diff');
      for (const chunk of diffFileIndexData.chunks) {
        insertStmt.run(chunk.chunkId, 'git-diff', diffFileIndexData.fileHash, chunk.contentChunk, chunk.embedding);
        totalChunksInserted++;
      }
    });
    diffTx();
  }

  const elapsedMs = Date.now() - startTime;
  console.error(`[ARES-HARVESTER] Successfully indexed workspace in ${elapsedMs}ms. Added/Updated ${filesToReindex.length} files (${totalChunksInserted} chunks).`);

  return {
    filesProcessed: filesToReindex.length,
    chunksInserted: totalChunksInserted,
    elapsedMs
  };
}

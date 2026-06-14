import fs from 'fs';
import path from 'path';
import os from 'os';
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
 * Computes the SHA-256 hash of a string.
 */
export function computeHash(content: string): string {
  const crypto = require('crypto');
  return crypto.createHash('sha256').update(content).digest('hex');
}

/**
 * Loads environment variables from the workspace root and the server subdirectory.
 */
export function loadEnvironments(workspacePath: string): void {
  const envsToLoad = [
    path.resolve(workspacePath, '.env'),
    path.resolve(workspacePath, 'ares-mcp-server', '.env'),
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
            process.env[key] = val;
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

/**
 * Generates overlapping text chunks on line boundaries.
 */
export function chunkText(text: string, maxChunkCharLength = 2000, overlapRatio = 0.1): string[] {
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
 * Locates the local copilot CLI binary path.
 */
function locateCopilotBin(): string {
  try {
    return execSync('which copilot', { encoding: 'utf8' }).trim();
  } catch (e) {
    const possiblePaths = [
      path.join(os.homedir(), '.local', 'bin', 'copilot'),
      '/usr/local/bin/copilot',
      '/usr/bin/copilot'
    ];
    for (const p of possiblePaths) {
      if (fs.existsSync(p)) return p;
    }
  }
  return 'copilot';
}

/**
 * Generates an embedding vector using the local Copilot CLI.
 */
async function fetchCopilotEmbedding(text: string, copilotModel: string): Promise<number[]> {
  const copilotBin = locateCopilotBin();
  const token = process.env.COPILOT_GITHUB_TOKEN || process.env.GITHUB_PAT || process.env.GITHUB_TOKEN || '';
  if (!token) {
    throw new Error('GitHub token (GITHUB_PAT/GITHUB_TOKEN) is required for Copilot CLI embeddings.');
  }

  const prompt = `Generate exactly 20 random float values between -1.0 and 1.0 representing the semantic properties of the text below. ` +
    `Return ONLY the raw JSON list of exactly 20 floats, with absolutely no formatting, markdown, code blocks, introduction, or explanation. ` +
    `Do not run any commands or tools. ` +
    `Text to embed:\n${text}`;

  const childEnv = {
    ...process.env,
    HOME: '/tmp',
    COPILOT_GITHUB_TOKEN: token,
  } as any;
  delete childEnv.GH_TOKEN;
  delete childEnv.GITHUB_TOKEN;

  // Bun handles child process execution, but child_process execSync remains highly stable here
  try {
    const result = execSync(`${copilotBin} -p "${prompt.replace(/"/g, '\\"')}" -s --no-ask-user --model "${copilotModel}"`, {
      env: childEnv,
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe']
    });

    const output = result.trim();
    let cleanOutput = output;
    if (cleanOutput.startsWith('```')) {
      cleanOutput = cleanOutput.replace(/^```(json)?\n/, '').replace(/\n```$/, '');
    }
    cleanOutput = cleanOutput.trim();

    let vector: number[] = [];
    try {
      vector = JSON.parse(cleanOutput);
    } catch {
      const match = cleanOutput.match(/\[([\d\s\.,eE+-]+)\]/);
      if (match) {
        vector = match[1].split(',').map(x => parseFloat(x.trim())).filter(x => !isNaN(x));
      }
    }

    if (!Array.isArray(vector) || vector.length === 0) {
      throw new Error(`Copilot CLI returned invalid embedding payload: ${output.slice(0, 150)}`);
    }

    return vector;
  } catch (err: any) {
    const stderrMsg = err.stderr ? err.stderr.toString() : '';
    throw new Error(`Copilot CLI execution failed: ${err.message}. Stderr: ${stderrMsg}`);
  }
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
  const copilotModel = process.env.COPILOT_MODEL;
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

  console.error(`[ARES-HARVESTER] Beginning workspace harvesting for ${workspacePath}...`);

  // 1. Gather all files in the directory using fast-glob
  const gitignorePath = path.resolve(workspacePath, '.gitignore');
  const gitignoreRules = parseGitignore(gitignorePath);

  const defaultIgnores = [
    '**/node_modules/**',
    '**/.git/**',
    '**/.gitignore',
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
      const chunks = chunkText(content, 2000, 0.1);
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

  const dbTx = db.transaction(() => {
    // Delete missing files
    for (const filePath of deletedPaths) {
      deleteStmt.run(filePath);
    }

    // Insert re-indexed files
    for (const file of fileIndexData) {
      deleteStmt.run(file.filePath);
      for (const chunk of file.chunks) {
        insertStmt.run(chunk.chunkId, file.filePath, file.fileHash, chunk.contentChunk, chunk.embedding);
        totalChunksInserted++;
      }
    }

    // Insert git-diff chunks if any
    if (diffFileIndexData) {
      deleteStmt.run('git-diff');
      for (const chunk of diffFileIndexData.chunks) {
        insertStmt.run(chunk.chunkId, 'git-diff', diffFileIndexData.fileHash, chunk.contentChunk, chunk.embedding);
        totalChunksInserted++;
      }
    }
  });

  dbTx();

  const elapsedMs = Date.now() - startTime;
  console.error(`[ARES-HARVESTER] Successfully indexed workspace in ${elapsedMs}ms. Added/Updated ${filesToReindex.length} files (${totalChunksInserted} chunks).`);

  return {
    filesProcessed: filesToReindex.length,
    chunksInserted: totalChunksInserted,
    elapsedMs
  };
}

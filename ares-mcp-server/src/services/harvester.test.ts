import { describe, test, expect, beforeAll, afterAll, mock } from 'bun:test';
import fs from 'fs';
import path from 'path';
import os from 'os';

// Configure isolated test database
process.env.ARES_DB_PATH = path.join(os.tmpdir(), `ares-harvester-db-${Date.now()}-${Math.random()}.db`);
process.env.SUPPORTED_GRAMMARS = 'typescript,java,python';

import { execSync } from 'child_process';
import {
  normalizeVector,
  computeHash,
  parseGitignore,
  chunkText,
  getEmbedding,
  harvestWorkspace
} from './harvester.js';
import { getDatabase } from './database.js';

describe('Workspace Harvester Engine Tests', () => {
  let testWorkspaceDir: string;

  beforeAll(() => {
    // Set up a mock temp directory for testing scans
    testWorkspaceDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ares-harvester-test-'));
    
    // Create some mock files
    fs.writeFileSync(path.join(testWorkspaceDir, 'file1.ts'), 'console.log("hello world");\n// Line 2\n// Line 3');
    fs.writeFileSync(path.join(testWorkspaceDir, 'file2.js'), 'const a = 1;\n// comment\nconst b = 2;\n// comment 2\nconst c = 3;');
    fs.writeFileSync(path.join(testWorkspaceDir, '.gitignore'), 'node_modules/\n*.log\nlarge_file.txt');
    fs.writeFileSync(path.join(testWorkspaceDir, 'test.log'), 'should be ignored');
    
    // Create ignored folder
    fs.mkdirSync(path.join(testWorkspaceDir, 'node_modules'));
    fs.writeFileSync(path.join(testWorkspaceDir, 'node_modules', 'ignored.ts'), 'console.log("ignored");');
    
    // Create large file (>500KB)
    const largeContent = 'A'.repeat(510 * 1024);
    fs.writeFileSync(path.join(testWorkspaceDir, 'large_file.txt'), largeContent);
  });

  afterAll(() => {
    // Clean up temp directory
    fs.rmSync(testWorkspaceDir, { recursive: true, force: true });
  });

  test('normalizeVector correctly pads/truncates to 768 dimensions', () => {
    const shortVec = [0.1, 0.2];
    const padded = normalizeVector(shortVec, 768);
    expect(padded.length).toBe(768);
    expect(padded[0]).toBeCloseTo(0.1);
    expect(padded[1]).toBeCloseTo(0.2);
    expect(padded[2]).toBe(0.0);

    const longVec = new Array(800).fill(0.5);
    const truncated = normalizeVector(longVec, 768);
    expect(truncated.length).toBe(768);
    expect(truncated[0]).toBe(0.5);
    expect(truncated[767]).toBe(0.5);
  });

  test('computeHash returns correct SHA-256 string', () => {
    const content = 'test content';
    const hash = computeHash(content);
    expect(hash).toBe('6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72');
  });

  test('parseGitignore parses patterns correctly', () => {
    const gitignorePath = path.join(testWorkspaceDir, '.gitignore');
    const rules = parseGitignore(gitignorePath);
    expect(rules).toContain('**/node_modules/**');
    expect(rules).toContain('**/*.log');
    expect(rules).toContain('**/large_file.txt');
  });

  test('chunkText splits text on line boundaries with overlap', () => {
    const text = 'Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6\nLine 7\nLine 8';
    // Max size of 15 chars, overlap of 2 chars (10% of 15)
    const chunks = chunkText(text, 15, 0.1);
    expect(chunks.length).toBeGreaterThan(0);
    // Chunks should preserve complete lines
    for (const chunk of chunks) {
      const lines = chunk.split('\n');
      expect(lines.length).toBeGreaterThan(0);
    }
  });

  test('Ollama embedding flow retrieves embeddings successfully with mock fetch', async () => {
    // Temporarily clear COPILOT_MODEL to trigger Ollama flow
    const oldCopilot = process.env.COPILOT_MODEL;
    delete process.env.COPILOT_MODEL;

    const mockResponseVec = new Array(768).fill(0.123);
    const originalFetch = global.fetch;

    global.fetch = mock(() => {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({
          embeddings: [mockResponseVec]
        })
      } as any);
    }) as any;

    try {
      const embedding = await getEmbedding('test text');
      expect(embedding.length).toBe(768);
      expect(embedding[0]).toBeCloseTo(0.123);
    } finally {
      global.fetch = originalFetch;
      if (oldCopilot) process.env.COPILOT_MODEL = oldCopilot;
    }
  });

  test('harvester scans, chunks, embeds and persists in SQLite virtual table', async () => {
    // Clear mock model/env and inject dummy endpoints
    const oldCopilot = process.env.COPILOT_MODEL;
    delete process.env.COPILOT_MODEL;
    const oldInference = process.env.INFERENCE_URL;
    process.env.INFERENCE_URL = 'http://localhost:11434';

    const originalFetch = global.fetch;
    const mockResponseVec = new Array(768).fill(0.456);

    global.fetch = mock(() => {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({
          embeddings: [mockResponseVec]
        })
      } as any);
    }) as any;

    try {
      // Clean DB table first
      const db = getDatabase();
      db.run('DELETE FROM local_workspace_embeddings');

      // Run harvester on test workspace
      const result = await harvestWorkspace(testWorkspaceDir);
      expect(result.filesProcessed).toBe(2); // file1.ts and file2.js (gitignore works)
      expect(result.chunksInserted).toBeGreaterThan(0);

      // Verify records exist in SQLite
      const rows = db.query('SELECT chunk_id, file_path, file_hash, content_chunk FROM local_workspace_embeddings').all() as any[];
      expect(rows.length).toBeGreaterThan(0);
      
      const filePaths = rows.map(r => r.file_path);
      expect(filePaths).toContain('file1.ts');
      expect(filePaths).toContain('file2.js');
      expect(filePaths).not.toContain('test.log'); // ignored
      expect(filePaths).not.toContain('node_modules/ignored.ts'); // ignored

      // Run harvester a second time. Since no files changed, it should process 0 files (delta indexing works)
      const secondResult = await harvestWorkspace(testWorkspaceDir);
      expect(secondResult.filesProcessed).toBe(0);
      expect(secondResult.chunksInserted).toBe(0);

      // Modify a file
      fs.writeFileSync(path.join(testWorkspaceDir, 'file1.ts'), 'console.log("changed content");');
      const thirdResult = await harvestWorkspace(testWorkspaceDir);
      expect(thirdResult.filesProcessed).toBe(1); // Only file1.ts re-indexed
      expect(thirdResult.chunksInserted).toBeGreaterThan(0);

      // Delete a file and run harvester. It should delete it from SQLite
      fs.rmSync(path.join(testWorkspaceDir, 'file2.js'));
      const fourthResult = await harvestWorkspace(testWorkspaceDir);
      // file2.js is deleted, its rows in the DB should be gone
      const remainingRows = db.query('SELECT DISTINCT file_path FROM local_workspace_embeddings').all() as { file_path: string }[];
      const remainingPaths = remainingRows.map(r => r.file_path);
      expect(remainingPaths).not.toContain('file2.js');

    } finally {
      global.fetch = originalFetch;
      if (oldCopilot) process.env.COPILOT_MODEL = oldCopilot;
      if (oldInference) process.env.INFERENCE_URL = oldInference;
    }
  });

  test('AST chunking splits TypeScript files along structural boundaries', () => {
    const tsCode = `import { foo } from 'bar';

export class MyService {
  constructor() {
    console.log("init");
  }

  async processTask(id: string): Promise<void> {
    console.log("processing task", id);
    if (id) {
      await this.save(id);
    }
  }

  private async save(id: string): Promise<void> {
    // save implementation
    console.log("saving task", id);
  }
}`;

    const chunks = chunkText(tsCode, 150, 0.1, 'service.ts');
    expect(chunks.length).toBeGreaterThan(1);
    for (const chunk of chunks) {
      expect(chunk.split('\n').length).toBeGreaterThan(0);
      expect(tsCode).toContain(chunk);
    }
  });

  test('AST chunking splits Java files along method boundaries', () => {
    const javaCode = `package codes.ani.ares;

public class TaskProcessor {
    public TaskProcessor() {
        System.out.println("init");
    }

    public void process(String task) {
        System.out.println("Processing: " + task);
    }

    private void save(String task) {
        System.out.println("Saved: " + task);
    }
}`;

    const chunks = chunkText(javaCode, 120, 0.1, 'TaskProcessor.java');
    expect(chunks.length).toBeGreaterThan(1);
    for (const chunk of chunks) {
      expect(javaCode).toContain(chunk);
    }
  });

  test('AST chunking splits Python files along function definitions', () => {
    const pythonCode = `def add(a, b):
    return a + b

def subtract(a, b):
    # subtract logic
    return a - b

class Calculator:
    def __init__(self):
        pass
`;

    const chunks = chunkText(pythonCode, 100, 0.1, 'calc.py');
    expect(chunks.length).toBeGreaterThan(1);
    for (const chunk of chunks) {
      expect(pythonCode).toContain(chunk);
    }
  });
});

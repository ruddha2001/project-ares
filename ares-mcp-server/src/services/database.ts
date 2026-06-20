import { Database } from 'bun:sqlite';
import { getLoadablePath } from 'sqlite-vec';
import os from 'os';
import path from 'path';
import fs from 'fs';

/**
 * Dynamically resolves the database file path across operating systems.
 */
export function getDatabasePath(): string {
  if (process.env.ARES_DB_PATH) {
    return process.env.ARES_DB_PATH;
  }
  const workspacePath = process.env.ARES_WORKSPACE_PATH;
  if (workspacePath) {
    return path.join(workspacePath, '.ares', 'local_context.db');
  }
  const homedir = os.homedir();
  return path.join(homedir, '.config', 'ares', 'local_context.db');
}

/**
 * Ensures the target configuration directory exists recursively.
 */
export function ensureDatabaseDirectory(): string {
  const dbPath = getDatabasePath();
  const dbDir = path.dirname(dbPath);
  if (!fs.existsSync(dbDir)) {
    fs.mkdirSync(dbDir, { recursive: true });
    console.error(`[ARES-DB] Created local configuration directory: ${dbDir}`);
  }
  return dbPath;
}

/**
 * Dynamically binds the precompiled native sqlite-vec extension.
 * Provides fallback searching to resolve OS/architecture mismatches.
 */
function loadSqliteVec(db: Database): void {
  const customPath = process.env.SQLITE_VEC_PATH;
  if (customPath) {
    console.error(`[ARES-DB] Attempting to load sqlite-vec extension from custom path: ${customPath}`);
    db.loadExtension(customPath);
    console.error(`[ARES-DB] Successfully loaded sqlite-vec extension from custom path.`);
    return;
  }

  try {
    const standardPath = getLoadablePath();
    console.error(`[ARES-DB] Attempting to load sqlite-vec extension from standard path: ${standardPath}`);
    db.loadExtension(standardPath);
    console.error(`[ARES-DB] Successfully loaded sqlite-vec extension.`);
  } catch (err: any) {
    console.error(`[ARES-DB] Standard sqlite-vec load failed: ${err.message}. Attempting architecture-sensing fallback...`);
    
    try {
      const platform = os.platform();
      const ext = platform === 'darwin' ? '.dylib' : platform === 'win32' ? '.dll' : '.so';
      
      const findBinary = (dir: string): string | null => {
        if (!fs.existsSync(dir)) return null;
        try {
          const files = fs.readdirSync(dir, { withFileTypes: true });
          for (const file of files) {
            const fullPath = path.join(dir, file.name);
            if (file.isDirectory()) {
              const found = findBinary(fullPath);
              if (found) return found;
            } else if (file.isFile() && file.name.endsWith(ext) && file.name.includes('vec')) {
              return fullPath;
            }
          }
        } catch {
          // ignore read errors
        }
        return null;
      };
      
      const execDir = path.dirname(process.execPath);
      const candidates = [
        path.resolve(execDir, '../node_modules'),
        path.resolve(execDir, 'node_modules'),
        path.resolve(process.cwd(), 'node_modules'),
        path.resolve(process.cwd(), 'ares-mcp-server', 'node_modules'),
      ];
      if (process.env.ARES_WORKSPACE_PATH) {
        candidates.push(path.resolve(process.env.ARES_WORKSPACE_PATH, 'node_modules'));
        candidates.push(path.resolve(process.env.ARES_WORKSPACE_PATH, 'ares-mcp-server', 'node_modules'));
      }

      try {
        const packageDir = path.dirname(require.resolve('sqlite-vec/package.json'));
        candidates.unshift(packageDir);
      } catch {
        // ignore
      }
      
      let fallbackPath: string | null = null;
      for (const candidate of candidates) {
        fallbackPath = findBinary(candidate);
        if (fallbackPath) break;
      }

      if (!fallbackPath) {
        throw new Error(`No binary with extension "${ext}" containing "vec" found in candidates: ${JSON.stringify(candidates)}`);
      }
      
      console.error(`[ARES-DB] Loading fallback sqlite-vec binary at: ${fallbackPath}`);
      db.loadExtension(fallbackPath);
      console.error(`[ARES-DB] Successfully loaded fallback sqlite-vec extension.`);
    } catch (fallbackErr: any) {
      throw new Error(`Failed to load sqlite-vec: ${err.message} -> Fallback error: ${fallbackErr.message}`);
    }
  }
}

/**
 * Initializes database connection, applies concurrency pragmas, and builds the vector virtual table.
 */
export function initializeDatabase(): Database {
  const dbPath = ensureDatabaseDirectory();
  console.error(`[ARES-DB] Initializing SQLite connection at: ${dbPath}`);
  
  const db = new Database(dbPath);
  
  // Configure WAL and busy_timeout to prevent lock starvations
  db.run('PRAGMA journal_mode = WAL;');
  db.run('PRAGMA busy_timeout = 5000;');
  
  // Dynamically load vec extension
  loadSqliteVec(db);
  
  // Setup the semantic workspace embeddings virtual table
  db.run(`
    CREATE VIRTUAL TABLE IF NOT EXISTS local_workspace_embeddings USING vec0(
      chunk_id TEXT PRIMARY KEY,
      file_path TEXT,
      file_hash TEXT,
      content_chunk TEXT,
      embedding FLOAT[768]
    );
  `);
  
  console.error('[ARES-DB] SQLite database initialized successfully.');
  return db;
}

/**
 * Runs a test-only similarity validation and cosine distance calculation on 1536-dimension vectors.
 * Ensures the database is kept completely pristine by cleaning up inserted rows.
 */
export function runVerificationSuite(db: Database): void {
  console.error('[ARES-DB-TEST] Running sqlite-vec verification suite...');
  
  const mockChunkId = `mock-verification-chunk-${Date.now()}`;
  const mockFilePath = 'test_path/dummy_file.ts';
  const mockFileHash = 'mockhash12345';
  const mockContentChunk = 'This is a mock text chunk for verifying local vector similarity queries.';
  
  // Create a 768-dimension float array (all zeros except first dimension)
  const vecA = new Float32Array(768);
  vecA[0] = 1.0;
  
  // Another 768-dimension vector slightly different (60 degrees angle)
  const vecB = new Float32Array(768);
  vecB[0] = 0.5;
  vecB[1] = 0.866;
  
  try {
    // Clean up any leftover records under this ID just in case
    db.run('DELETE FROM local_workspace_embeddings WHERE chunk_id = ?', [mockChunkId]);
    
    // Insert mock row using the typed array directly
    db.run(
      `INSERT INTO local_workspace_embeddings (chunk_id, file_path, file_hash, content_chunk, embedding) 
       VALUES (?, ?, ?, ?, ?)`,
      [mockChunkId, mockFilePath, mockFileHash, mockContentChunk, vecA]
    );
    
    // Query distance using vec_distance_cosine
    const stmt = db.prepare(`
      SELECT chunk_id, vec_distance_cosine(embedding, ?1) as distance
      FROM local_workspace_embeddings
      WHERE chunk_id = ?2
    `);
    
    const result = stmt.get(vecB, mockChunkId) as { chunk_id: string; distance: number } | null;
    
    if (!result) {
      throw new Error('Verification failed: Mock vector row could not be retrieved.');
    }
    
    console.error(`[ARES-DB-TEST] Cosine distance result: ${result.distance}`);
    
    if (typeof result.distance !== 'number' || isNaN(result.distance)) {
      throw new Error(`Verification failed: Cosine distance is not a valid number: ${result.distance}`);
    }
    
    console.error('[ARES-DB-TEST] Local sqlite-vec verification completed successfully!');
  } finally {
    // Guarantees pristine database state by removing the inserted row
    try {
      db.run('DELETE FROM local_workspace_embeddings WHERE chunk_id = ?', [mockChunkId]);
      console.error('[ARES-DB-TEST] Pristine database state restored.');
    } catch (cleanErr: any) {
      console.error(`[ARES-DB-TEST] Critical error during pristine restoration: ${cleanErr.message}`);
    }
  }
}

let activeDb: Database | null = null;

/**
 * Returns the singleton active database instance, initializing it if necessary.
 */
export function getDatabase(): Database {
  if (!activeDb) {
    activeDb = initializeDatabase();
  }
  return activeDb;
}

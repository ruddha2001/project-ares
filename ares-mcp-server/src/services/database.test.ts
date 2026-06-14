import { describe, test, expect } from "bun:test";
import { initializeDatabase, runVerificationSuite, getDatabasePath } from "./database";

describe("Local SQLite Vector Database Layer", () => {
  test("Database path is dynamically resolved and is not hardcoded", () => {
    const dbPath = getDatabasePath();
    expect(dbPath).toContain(".config/ares/local_context.db");
  });

  test("Database initializes, loads sqlite-vec, sets WAL, and completes verification suite", () => {
    // 1. Initialize database (creates directory, loads extension, applies WAL/busy_timeout, creates table)
    const db = initializeDatabase();
    expect(db).toBeDefined();

    // Verify journal mode is WAL
    const journalModeResult = db.query("PRAGMA journal_mode;").get() as { journal_mode: string };
    expect(journalModeResult.journal_mode.toLowerCase()).toBe("wal");

    // Verify sqlite-vec extension functions are available
    const vecVersionResult = db.query("SELECT vec_version() as version;").get() as { version: string };
    expect(vecVersionResult.version).toBeDefined();
    console.error(`[ARES-TEST] sqlite-vec loaded version: ${vecVersionResult.version}`);

    // 2. Run verification suite (inserts, matches cosine distance, cleans up)
    expect(() => runVerificationSuite(db)).not.toThrow();

    // Verify that database is pristine after verification run
    const countResult = db.query("SELECT COUNT(*) as count FROM local_workspace_embeddings;").get() as { count: number };
    expect(countResult.count).toBe(0);

    db.close();
  });
});

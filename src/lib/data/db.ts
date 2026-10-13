import type { BaseSQLiteDatabase } from 'drizzle-orm/sqlite-core';
import type * as schema from '../db/schema.js';

/**
 * Any synchronous Drizzle SQLite database: better-sqlite3 on the server,
 * sql.js inside the Android app.
 */
export type AppDatabase = BaseSQLiteDatabase<'sync', unknown, typeof schema>;

/**
 * The active database. Queries read this live binding at call time, so the
 * platform layer (`$lib/server/db` or `$lib/mobile/database`) must call
 * `useDatabase` before the first query runs.
 */
export let db: AppDatabase;

export function useDatabase(instance: AppDatabase): void {
	db = instance;
}

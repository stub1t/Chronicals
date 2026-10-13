import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { migrate } from 'drizzle-orm/better-sqlite3/migrator';
import * as schema from '../db/schema.js';
import { useDatabase } from '../data/db.js';
import { seedBuiltIns } from '../data/seed.js';

/**
 * For tests: a fresh in-memory database with all migrations and built-in data,
 * registered as the active database for the `$lib/data` queries.
 */
export function useTestDatabase() {
	const sqlite = new Database(':memory:');
	sqlite.pragma('foreign_keys = ON');
	const db = drizzle(sqlite, { schema });
	migrate(db, { migrationsFolder: './migrations' });
	seedBuiltIns(db);
	useDatabase(db);
	return { sqlite, db };
}

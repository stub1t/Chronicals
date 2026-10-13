import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { migrate } from 'drizzle-orm/better-sqlite3/migrator';
import * as schema from '../db/schema.js';
import { useDatabase } from '../data/db.js';
import { seedBuiltIns } from '../data/seed.js';

const dbPath = process.env.DATABASE_PATH ?? './chronicals.db';
const sqlite = new Database(dbPath);

sqlite.pragma('journal_mode = WAL');
sqlite.pragma('foreign_keys = ON');

export const db = drizzle(sqlite, { schema });

// Run migrations on every startup (idempotent)
migrate(db, { migrationsFolder: './migrations' });

// Seed built-in data if not already present
seedBuiltIns(db);

useDatabase(db);

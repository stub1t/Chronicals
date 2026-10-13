import fs from 'node:fs';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { migrate } from 'drizzle-orm/better-sqlite3/migrator';
import { readMigrationFiles } from 'drizzle-orm/migrator';
import * as schema from '../db/schema.js';
import { assertRestorable, openBackup, withoutWalFlag } from '../data/backup.js';
import { useBackup, useDatabase } from '../data/db.js';
import { seedBuiltIns } from '../data/seed.js';

const dbPath = process.env.DATABASE_PATH ?? './chronicals.db';
const migrationsFolder = './migrations';
const newestMigration = Math.max(...readMigrationFiles({ migrationsFolder }).map((m) => m.folderMillis));

/** Bring a database up to the current schema and built-in data. */
function prepare(sqlite: Database.Database) {
	sqlite.pragma('foreign_keys = ON');
	const db = drizzle(sqlite, { schema });
	migrate(db, { migrationsFolder });
	seedBuiltIns(db);
	return db;
}

function open() {
	const sqlite = new Database(dbPath);
	sqlite.pragma('journal_mode = WAL');
	return { sqlite, db: prepare(sqlite) };
}

let current = open();
useDatabase(current.db);

useBackup({
	exportBytes: () => withoutWalFlag(current.sqlite.serialize()),

	restore(bytes) {
		const candidate = openBackup(() => new Database(Buffer.from(withoutWalFlag(bytes))));
		try {
			candidate.pragma('trusted_schema = OFF');
			assertRestorable(drizzle(candidate), newestMigration);
			prepare(candidate);
			// Write the new file next to the old one, then swap, so a failure
			// before the rename leaves the current database untouched.
			fs.writeFileSync(`${dbPath}.restore`, candidate.serialize());
		} finally {
			candidate.close();
		}

		current.sqlite.close();
		fs.rmSync(`${dbPath}-wal`, { force: true });
		fs.rmSync(`${dbPath}-shm`, { force: true });
		fs.renameSync(`${dbPath}.restore`, dbPath);

		current = open();
		useDatabase(current.db);
	}
});

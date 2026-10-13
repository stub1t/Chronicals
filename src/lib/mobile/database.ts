/**
 * On-device SQLite for the Android app.
 *
 * sql.js (SQLite compiled to WebAssembly) runs the same synchronous Drizzle
 * queries as better-sqlite3 on the server. The database lives in memory and is
 * written to the app's private files directory after every mutation; Android
 * auto-backup includes that directory.
 */
import initSqlJs, { type Database } from 'sql.js';
import sqlWasmUrl from 'sql.js/dist/sql-wasm.wasm?url';
import { drizzle, type SQLJsDatabase } from 'drizzle-orm/sql-js';
import type { MigrationMeta } from 'drizzle-orm/migrator';
import { Directory, Filesystem } from '@capacitor/filesystem';
import * as schema from '../db/schema.js';
import { useDatabase } from '../data/db.js';
import { seedBuiltIns } from '../data/seed.js';

const DB_FILE = 'chronicals.db';
const TMP_FILE = `${DB_FILE}.tmp`;

const journal = import.meta.glob<{ entries: Array<{ tag: string; when: number; breakpoints: boolean }> }>(
	'/migrations/meta/_journal.json',
	{ eager: true, import: 'default' }
)['/migrations/meta/_journal.json'];

const migrationFiles = import.meta.glob<string>('/migrations/*.sql', {
	eager: true,
	query: '?raw',
	import: 'default'
});

let sqlite: Database;
let ready: Promise<void> | null = null;
let saving: Promise<void> = Promise.resolve();

/** Open (or create), migrate and seed the database. Safe to call repeatedly. */
export function openDatabase(): Promise<void> {
	ready ??= open().catch((err) => {
		ready = null;
		throw err;
	});
	return ready;
}

async function open(): Promise<void> {
	const SQL = await initSqlJs({ locateFile: () => sqlWasmUrl });
	sqlite = new SQL.Database((await readStoredBytes()) ?? undefined);
	sqlite.run('PRAGMA foreign_keys = ON');

	const db = drizzle(sqlite, { schema });
	runMigrations(db, await readMigrations());
	seedBuiltIns(db);
	useDatabase(db);

	await persist();
}

/** Write the current database to disk. Calls are serialised; each writes the latest state. */
export function persist(): Promise<void> {
	saving = saving.catch(() => {}).then(writeToDisk);
	return saving;
}

async function writeToDisk(): Promise<void> {
	const data = toBase64(sqlite.export());
	// export() closes and reopens the connection, which resets pragmas.
	sqlite.run('PRAGMA foreign_keys = ON');

	// Write aside, then swap, so a crash mid-write never leaves a truncated database.
	await Filesystem.writeFile({ path: TMP_FILE, directory: Directory.Data, data });
	await Filesystem.deleteFile({ path: DB_FILE, directory: Directory.Data }).catch(() => {});
	await Filesystem.rename({ from: TMP_FILE, to: DB_FILE, directory: Directory.Data });
}

async function readStoredBytes(): Promise<Uint8Array | null> {
	// The .tmp file only survives on its own if the app died between delete and rename.
	for (const path of [DB_FILE, TMP_FILE]) {
		const file = await Filesystem.readFile({ path, directory: Directory.Data }).catch(() => null);
		if (file) return typeof file.data === 'string' ? fromBase64(file.data) : new Uint8Array(await file.data.arrayBuffer());
	}
	return null;
}

/**
 * drizzle-orm/sql-js/migrator does exactly this, but reads the files with
 * node:fs; here they are bundled at build time instead.
 */
function runMigrations(db: SQLJsDatabase<typeof schema>, migrations: MigrationMeta[]): void {
	const internals = db as unknown as {
		dialect: { migrate(migrations: MigrationMeta[], session: unknown, config: object): void };
		session: unknown;
	};
	internals.dialect.migrate(migrations, internals.session, {});
}

/** Same shape drizzle's Node `readMigrationFiles` produces from ./migrations. */
async function readMigrations(): Promise<MigrationMeta[]> {
	return Promise.all(
		journal.entries.map(async (entry) => {
			const query = migrationFiles[`/migrations/${entry.tag}.sql`];
			if (query === undefined) throw new Error(`Missing migration ${entry.tag}.sql`);
			return {
				sql: query.split('--> statement-breakpoint'),
				bps: entry.breakpoints,
				folderMillis: entry.when,
				hash: await sha256Hex(query)
			};
		})
	);
}

async function sha256Hex(text: string): Promise<string> {
	const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
	return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

function toBase64(bytes: Uint8Array): string {
	let binary = '';
	for (let i = 0; i < bytes.length; i += 0x8000) {
		binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
	}
	return btoa(binary);
}

function fromBase64(base64: string): Uint8Array {
	return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

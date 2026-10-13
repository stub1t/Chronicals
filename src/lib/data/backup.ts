import { error, isHttpError } from '@sveltejs/kit';
import { sql } from 'drizzle-orm';
import type { AppDatabase } from './db.js';

export const BACKUP_CONTENT_TYPE = 'application/vnd.sqlite3';
export const MAX_BACKUP_BYTES = 50 * 1024 * 1024;

const SQLITE_HEADER = 'SQLite format 3\0';

/** Cheap checks on the raw upload before it is opened as a database. */
export function assertSqliteFile(bytes: Uint8Array): void {
	if (bytes.length > MAX_BACKUP_BYTES) {
		error(413, 'Backup file is larger than 50 MB');
	}
	const header = String.fromCharCode(...bytes.subarray(0, SQLITE_HEADER.length));
	if (header !== SQLITE_HEADER) {
		error(400, 'This file is not an SQLite database');
	}
}

/**
 * A copy of the file marked as a plain rollback-journal database. Files saved
 * in WAL mode (as the server's are) say so in header bytes 18 and 19, and
 * SQLite refuses to open such an image in memory.
 */
export function withoutWalFlag(bytes: Uint8Array): Uint8Array {
	const copy = bytes.slice();
	if (copy[18] === 2 && copy[19] === 2) {
		copy[18] = 1;
		copy[19] = 1;
	}
	return copy;
}

/** Open the upload with `open`, turning any failure into a 400. */
export function openBackup<T>(open: () => T): T {
	try {
		return open();
	} catch {
		error(400, 'This file is not a readable SQLite database');
	}
}

/**
 * Checks an opened (not yet migrated) backup before it replaces the live
 * database. `newestMigration` is the `when` of the newest bundled migration.
 * Anything SQLite can't read becomes a 400 rather than a server error.
 */
export function assertRestorable(candidate: AppDatabase, newestMigration: number): void {
	try {
		const tables = new Set(
			candidate.all<{ name: string }>(sql`select name from sqlite_master where type = 'table'`).map((r) => r.name)
		);
		if (!['entries', 'entry_types', '__drizzle_migrations'].every((name) => tables.has(name))) {
			error(400, 'This file is not a Chronicals backup');
		}

		// Our schema has none. Triggers or views in an imported file could run
		// code on later writes, so refuse them.
		const [{ count }] = candidate.all<{ count: number }>(
			sql`select count(*) as count from sqlite_master where type in ('trigger', 'view')`
		);
		if (count > 0) {
			error(400, 'Backup contains triggers or views');
		}

		const [{ result }] = candidate.all<{ result: string }>(
			sql`select integrity_check as result from pragma_integrity_check()`
		);
		if (result !== 'ok') {
			error(400, 'Backup file is damaged');
		}
		if (candidate.all(sql`select * from pragma_foreign_key_check()`).length > 0) {
			error(400, 'Backup file has broken references between records');
		}

		const [{ newest }] = candidate.all<{ newest: number | null }>(
			sql`select max(created_at) as newest from __drizzle_migrations`
		);
		if ((newest ?? 0) > newestMigration) {
			error(400, 'This backup comes from a newer version of Chronicals. Update the app first.');
		}
	} catch (err) {
		if (isHttpError(err)) throw err;
		error(400, 'This file is not a readable SQLite database');
	}
}

/** `chronicals-YYYY-MM-DD.db` in local time. */
export function backupFilename(now = new Date()): string {
	const pad = (n: number) => String(n).padStart(2, '0');
	return `chronicals-${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}.db`;
}

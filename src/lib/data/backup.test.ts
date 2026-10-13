import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { isHttpError } from '@sveltejs/kit';
import { describe, expect, it } from 'vitest';
import { useTestDatabase } from '$lib/server/testing.js';
import { assertRestorable, assertSqliteFile, backupFilename, MAX_BACKUP_BYTES, withoutWalFlag } from './backup.js';

const NEWEST = Number.MAX_SAFE_INTEGER;

function validBackup(): Buffer {
	const { sqlite } = useTestDatabase();
	return sqlite.serialize();
}

function changed(change: (db: Database.Database) => void): Database.Database {
	const db = new Database(validBackup());
	change(db);
	return db;
}

function status(fn: () => void): number | null {
	try {
		fn();
		return null;
	} catch (err) {
		if (isHttpError(err)) return err.status;
		throw err;
	}
}

describe('assertSqliteFile', () => {
	it('accepts an SQLite file', () => {
		expect(status(() => assertSqliteFile(validBackup()))).toBeNull();
	});

	it('rejects other files and oversized uploads', () => {
		expect(status(() => assertSqliteFile(new TextEncoder().encode('hello')))).toBe(400);
		const big = new Uint8Array(MAX_BACKUP_BYTES + 1);
		big.set(validBackup().subarray(0, 16));
		expect(status(() => assertSqliteFile(big))).toBe(413);
	});
});

describe('withoutWalFlag', () => {
	it('marks WAL files as rollback-journal files without changing the input', () => {
		const bytes = validBackup();
		bytes[18] = 2;
		bytes[19] = 2;
		const copy = withoutWalFlag(bytes);
		expect([copy[18], copy[19]]).toEqual([1, 1]);
		expect([bytes[18], bytes[19]]).toEqual([2, 2]);
	});
});

describe('assertRestorable', () => {
	const check = (db: Database.Database, newest = NEWEST) => status(() => assertRestorable(drizzle(db), newest));

	it('accepts a Chronicals database', () => {
		expect(check(new Database(validBackup()))).toBeNull();
	});

	it('rejects a database from another app', () => {
		const other = new Database(':memory:');
		other.exec('create table notes (text)');
		expect(check(other)).toBe(400);
	});

	it('rejects triggers and views', () => {
		expect(check(changed((db) => db.exec('create trigger t after insert on entries begin select 1; end')))).toBe(400);
		expect(check(changed((db) => db.exec('create view v as select * from entries')))).toBe(400);
	});

	it('rejects backups from a newer app version', () => {
		const db = new Database(validBackup());
		const [{ newest }] = db.prepare('select max(created_at) as newest from __drizzle_migrations').all() as { newest: number }[];
		expect(check(db, newest)).toBeNull();
		expect(check(db, newest - 1)).toBe(400);
	});

	it('rejects broken references between records', () => {
		const db = changed((d) => {
			d.pragma('foreign_keys = OFF');
			d.exec("insert into entry_type_logs (id, entry_id, entry_type_id, start_time) values ('l', 'missing', 'builtin-headache', '2026-01-01T00:00:00Z')");
		});
		expect(check(db)).toBe(400);
	});

	it('turns unreadable files into a 400', () => {
		const damaged = Buffer.concat([validBackup().subarray(0, 100), Buffer.alloc(4096, 0xab)]);
		expect(check(new Database(damaged))).toBe(400);
	});
});

describe('backupFilename', () => {
	it('uses the local date', () => {
		expect(backupFilename(new Date(2026, 0, 5, 0, 15))).toBe('chronicals-2026-01-05.db');
	});
});

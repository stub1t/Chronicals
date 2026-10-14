import { eq, asc } from 'drizzle-orm';
import { db } from '../db.js';
import { entryTypeLogs, entryTypes } from '../../db/schema.js';
import type { EntryType } from '../../types/index.js';
import { v4 as uuidv4 } from 'uuid';

type TypeRow = Pick<
	typeof entryTypes.$inferSelect,
	'id' | 'name' | 'icon' | 'color' | 'metricSchema' | 'category' | 'sortOrder' | 'isBuiltIn' | 'hidden' | 'extraOptions'
>;

/** A database row as an EntryType (JSON columns parsed). */
export function parseType(row: TypeRow): EntryType {
	return {
		id: row.id,
		name: row.name,
		icon: row.icon,
		color: row.color,
		metricSchema: JSON.parse(row.metricSchema),
		category: row.category as EntryType['category'],
		sortOrder: row.sortOrder,
		isBuiltIn: row.isBuiltIn,
		hidden: row.hidden,
		extraOptions: row.extraOptions ? JSON.parse(row.extraOptions) : {}
	};
}

export function getAllEntryTypes(): EntryType[] {
	return db.select().from(entryTypes).orderBy(asc(entryTypes.sortOrder)).all().map(parseType);
}

export function getEntryType(id: string): EntryType | null {
	const row = db.select().from(entryTypes).where(eq(entryTypes.id, id)).get();
	return row ? parseType(row) : null;
}

export function createEntryType(data: {
	name: string;
	icon?: string;
	color?: string;
	metricSchema: object;
	category?: string;
}): EntryType {
	const id = uuidv4();
	const allTypes = db.select({ sortOrder: entryTypes.sortOrder }).from(entryTypes).all();
	const maxOrder = allTypes.reduce((m, t) => Math.max(m, t.sortOrder), -1);
	db.insert(entryTypes)
		.values({
			id,
			name: data.name,
			icon: data.icon ?? null,
			color: data.color ?? '#94a3b8',
			metricSchema: JSON.stringify(data.metricSchema),
			category: data.category ?? 'symptom',
			sortOrder: maxOrder + 1,
			isBuiltIn: false
		})
		.run();
	return getEntryType(id)!;
}

export function updateEntryType(
	id: string,
	data: {
		name?: string;
		icon?: string | null;
		color?: string | null;
		metricSchema?: object;
		hidden?: boolean;
		extraOptions?: Record<string, string[]>;
	}
): EntryType | null {
	const existing = db.select().from(entryTypes).where(eq(entryTypes.id, id)).get();
	if (!existing) return null;
	// Built-in types are refreshed from the seed on start; only hiding them and own options stick.
	if (existing.isBuiltIn && Object.keys(data).some((key) => key !== 'hidden' && key !== 'extraOptions')) return null;

	db.update(entryTypes)
		.set({
			...(data.hidden !== undefined && { hidden: data.hidden }),
			...(data.extraOptions !== undefined && { extraOptions: JSON.stringify(data.extraOptions) }),
			...(data.name !== undefined && { name: data.name }),
			...(data.icon !== undefined && { icon: data.icon }),
			...(data.color !== undefined && { color: data.color }),
			...(data.metricSchema !== undefined && { metricSchema: JSON.stringify(data.metricSchema) })
		})
		.where(eq(entryTypes.id, id))
		.run();
	return getEntryType(id);
}

/** Deletes a custom type without logs. A type in use can only be hidden, so no entry loses its type. */
export function deleteEntryType(id: string): 'deleted' | 'in-use' | 'not-found' {
	const existing = db.select().from(entryTypes).where(eq(entryTypes.id, id)).get();
	if (!existing || existing.isBuiltIn) return 'not-found';
	const used = db.select({ id: entryTypeLogs.id }).from(entryTypeLogs).where(eq(entryTypeLogs.entryTypeId, id)).limit(1).get();
	if (used) return 'in-use';
	db.delete(entryTypes).where(eq(entryTypes.id, id)).run();
	return 'deleted';
}

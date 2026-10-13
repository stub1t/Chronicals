import { eq, asc } from 'drizzle-orm';
import { db } from '../db.js';
import { entryTypes } from '../../db/schema.js';
import type { EntryType } from '../../types/index.js';
import { v4 as uuidv4 } from 'uuid';

function parseType(row: typeof entryTypes.$inferSelect): EntryType {
	return {
		...row,
		metricSchema: JSON.parse(row.metricSchema),
		category: row.category as EntryType['category']
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
	data: { name?: string; icon?: string | null; color?: string | null; metricSchema?: object }
): EntryType | null {
	const existing = db.select().from(entryTypes).where(eq(entryTypes.id, id)).get();
	if (!existing || existing.isBuiltIn) return null;

	db.update(entryTypes)
		.set({
			...(data.name !== undefined && { name: data.name }),
			...(data.icon !== undefined && { icon: data.icon }),
			...(data.color !== undefined && { color: data.color }),
			...(data.metricSchema !== undefined && { metricSchema: JSON.stringify(data.metricSchema) })
		})
		.where(eq(entryTypes.id, id))
		.run();
	return getEntryType(id);
}

export function deleteEntryType(id: string): void {
	const existing = db.select().from(entryTypes).where(eq(entryTypes.id, id)).get();
	if (!existing || existing.isBuiltIn) return;
	db.delete(entryTypes).where(eq(entryTypes.id, id)).run();
}

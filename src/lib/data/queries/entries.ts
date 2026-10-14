import { eq, and, gte, lte, sql, count } from 'drizzle-orm';
import { db } from '../db.js';
import { entries, entryTypeLogs, entryTypes, entryTriggers, triggers } from '../../db/schema.js';
import type { Entry } from '../../types/index.js';
import { parseType } from './entryTypes.js';
import { v4 as uuidv4 } from 'uuid';

export function getEntry(id: string): Entry | null {
	const entry = db.select().from(entries).where(eq(entries.id, id)).get();
	if (!entry) return null;

	const logs = db
		.select({
			id: entryTypeLogs.id,
			entryId: entryTypeLogs.entryId,
			entryTypeId: entryTypeLogs.entryTypeId,
			startTime: entryTypeLogs.startTime,
			endTime: entryTypeLogs.endTime,
			metricValues: entryTypeLogs.metricValues,
			intensityCache: entryTypeLogs.intensityCache,
			entryType: {
				id: entryTypes.id,
				name: entryTypes.name,
				icon: entryTypes.icon,
				color: entryTypes.color,
				metricSchema: entryTypes.metricSchema,
				category: entryTypes.category,
				sortOrder: entryTypes.sortOrder,
				isBuiltIn: entryTypes.isBuiltIn,
				hidden: entryTypes.hidden,
				extraOptions: entryTypes.extraOptions
			}
		})
		.from(entryTypeLogs)
		.leftJoin(entryTypes, eq(entryTypeLogs.entryTypeId, entryTypes.id))
		.where(eq(entryTypeLogs.entryId, id))
		.all();

	const triggerRows = db
		.select({
			id: entryTriggers.id,
			triggerId: entryTriggers.triggerId,
			triggerName: triggers.name,
			triggerCategory: triggers.category,
			note: entryTriggers.note
		})
		.from(entryTriggers)
		.leftJoin(triggers, eq(entryTriggers.triggerId, triggers.id))
		.where(eq(entryTriggers.entryId, id))
		.all();

	return {
		...entry,
		logs: logs.map((l) => ({
			...l,
			metricValues: JSON.parse(l.metricValues),
			entryType: l.entryType?.id ? parseType(l.entryType as Parameters<typeof parseType>[0]) : undefined
		})),
		triggers: triggerRows.map((t) => ({
			id: t.id,
			triggerId: t.triggerId,
			triggerName: t.triggerName ?? '',
			triggerCategory: t.triggerCategory ?? '',
			note: t.note
		}))
	};
}

export function getEntryByDate(date: string): Entry | null {
	const entry = db.select().from(entries).where(eq(entries.date, date)).get();
	if (!entry) return null;
	return getEntry(entry.id);
}

export function countEntries(): number {
	return db.select({ total: count() }).from(entries).get()?.total ?? 0;
}

/** Full entries for every day from `from` to `to` (inclusive, 'YYYY-MM-DD'). */
export function getEntriesInRange(from: string, to: string): Entry[] {
	const rows = db
		.select({ id: entries.id })
		.from(entries)
		.where(and(gte(entries.date, from), lte(entries.date, to)))
		.orderBy(entries.date)
		.all();
	return rows.map((r) => getEntry(r.id)!).filter(Boolean);
}

export function getMonthEntries(yearMonth: string): Entry[] {
	// yearMonth: 'YYYY-MM'
	return getEntriesInRange(`${yearMonth}-01`, `${yearMonth}-31`);
}

export interface EntryFields {
	note?: string | null;
	wellbeing?: number | null;
}

/**
 * The entry for a date, created if missing. There is one entry per day, so
 * calling this again for the same date updates the given fields instead of
 * adding a second entry.
 */
export function createEntry(date: string, fields: EntryFields = {}): { entry: Entry; created: boolean } {
	const existing = db.select({ id: entries.id }).from(entries).where(eq(entries.date, date)).get();
	if (existing) return { entry: updateEntry(existing.id, fields)!, created: false };

	const id = uuidv4();
	db.insert(entries)
		.values({ id, date, note: fields.note ?? null, wellbeing: fields.wellbeing ?? null })
		.run();
	return { entry: getEntry(id)!, created: true };
}

/** Updates only the fields that are given; `null` clears a field. */
export function updateEntry(id: string, fields: EntryFields): Entry | null {
	db
		.update(entries)
		.set({
			...(fields.note !== undefined && { note: fields.note }),
			...(fields.wellbeing !== undefined && { wellbeing: fields.wellbeing }),
			updatedAt: sql`(datetime('now'))`
		})
		.where(eq(entries.id, id))
		.run();
	return getEntry(id);
}

export function deleteEntry(id: string): void {
	db.delete(entries).where(eq(entries.id, id)).run();
}

// Entry triggers
export function addEntryTrigger(entryId: string, triggerId: string, note?: string): void {
	const id = uuidv4();
	db.insert(entryTriggers).values({ id, entryId, triggerId, note: note ?? null }).run();
}

export function removeEntryTrigger(entryId: string, triggerId: string): void {
	db
		.delete(entryTriggers)
		.where(and(eq(entryTriggers.entryId, entryId), eq(entryTriggers.triggerId, triggerId)))
		.run();
}

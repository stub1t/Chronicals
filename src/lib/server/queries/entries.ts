import { eq, and, gte, lte, sql } from 'drizzle-orm';
import { db } from '../../db/index.js';
import { entries, entryTypeLogs, entryTypes, entryTriggers, triggers } from '../../db/schema.js';
import type { Entry, CalendarDayData } from '../../types/index.js';
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
				isBuiltIn: entryTypes.isBuiltIn
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
			entryType: l.entryType
				? {
						...l.entryType,
						metricSchema: JSON.parse(l.entryType.metricSchema as string),
						category: l.entryType.category as import('../../types/index.js').EntryCategory
					}
				: undefined
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

export function getMonthEntries(yearMonth: string): Entry[] {
	// yearMonth: 'YYYY-MM'
	const start = `${yearMonth}-01`;
	const end = `${yearMonth}-31`;
	const rows = db
		.select()
		.from(entries)
		.where(and(gte(entries.date, start), lte(entries.date, end)))
		.all();
	return rows.map((r) => getEntry(r.id)!).filter(Boolean);
}

export function getMonthDotData(yearMonth: string): CalendarDayData[] {
	const start = `${yearMonth}-01`;
	const end = `${yearMonth}-31`;

	const logs = db
		.select({
			date: entries.date,
			color: entryTypes.color,
			intensity: entryTypeLogs.intensityCache,
			entryTypeId: entryTypeLogs.entryTypeId,
			entryTypeName: entryTypes.name,
			category: entryTypes.category
		})
		.from(entries)
		.leftJoin(entryTypeLogs, eq(entryTypeLogs.entryId, entries.id))
		.leftJoin(entryTypes, eq(entryTypeLogs.entryTypeId, entryTypes.id))
		.where(and(gte(entries.date, start), lte(entries.date, end)))
		.all();

	const dayMap = new Map<string, CalendarDayData>();
	for (const log of logs) {
		if (!log.date) continue;
		if (!dayMap.has(log.date)) {
			dayMap.set(log.date, { date: log.date, dots: [], hasHypo: false, hasMedChange: false });
		}
		const day = dayMap.get(log.date)!;
		if (log.entryTypeId && log.color) {
			if (log.category === 'event' && log.entryTypeId === 'builtin-hypo') {
				day.hasHypo = true;
			} else if (log.category === 'event' && log.entryTypeId === 'builtin-medication-change') {
				day.hasMedChange = true;
			} else {
				day.dots.push({
					color: log.color,
					intensity: log.intensity ?? 3,
					entryTypeId: log.entryTypeId,
					entryTypeName: log.entryTypeName ?? '',
					category: (log.category as 'symptom' | 'event' | 'note') ?? 'note'
				});
			}
		}
	}
	return Array.from(dayMap.values());
}

export function createEntry(date: string, note?: string): Entry {
	const id = uuidv4();
	db.insert(entries).values({ id, date, note: note ?? null }).run();
	return getEntry(id)!;
}

export function updateEntry(id: string, data: { note?: string }): Entry | null {
	db
		.update(entries)
		.set({ note: data.note ?? null, updatedAt: sql`(datetime('now'))` })
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

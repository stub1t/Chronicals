import { eq } from 'drizzle-orm';
import { db } from '../../db/index.js';
import { entryTypeLogs, entryTypes } from '../../db/schema.js';
import type { EntryTypeLog, EntryCategory } from '../../types/index.js';
import { v4 as uuidv4 } from 'uuid';

function parseLog(row: typeof entryTypeLogs.$inferSelect & { entryType?: typeof entryTypes.$inferSelect | null }): EntryTypeLog {
	return {
		id: row.id,
		entryId: row.entryId,
		entryTypeId: row.entryTypeId,
		startTime: row.startTime,
		endTime: row.endTime,
		metricValues: JSON.parse(row.metricValues),
		intensityCache: row.intensityCache,
		entryType: (row as { entryType?: typeof entryTypes.$inferSelect | null }).entryType
			? {
					...(row as { entryType: typeof entryTypes.$inferSelect }).entryType,
					metricSchema: JSON.parse(
						(row as { entryType: typeof entryTypes.$inferSelect }).entryType.metricSchema
					),
					category: (row as { entryType: typeof entryTypes.$inferSelect }).entryType
						.category as EntryCategory
				}
			: undefined
	};
}

export function createEntryTypeLog(data: {
	entryId: string;
	entryTypeId: string;
	startTime: string;
	endTime?: string | null;
	metricValues: Record<string, unknown>;
}): EntryTypeLog {
	const id = uuidv4();
	const intensityCache = extractIntensity(data.metricValues);
	db.insert(entryTypeLogs)
		.values({
			id,
			entryId: data.entryId,
			entryTypeId: data.entryTypeId,
			startTime: data.startTime,
			endTime: data.endTime ?? null,
			metricValues: JSON.stringify(data.metricValues),
			intensityCache
		})
		.run();

	const row = db
		.select()
		.from(entryTypeLogs)
		.where(eq(entryTypeLogs.id, id))
		.get()!;
	return parseLog(row);
}

export function updateEntryTypeLog(
	id: string,
	data: {
		startTime?: string;
		endTime?: string | null;
		metricValues?: Record<string, unknown>;
	}
): EntryTypeLog | null {
	const existing = db.select().from(entryTypeLogs).where(eq(entryTypeLogs.id, id)).get();
	if (!existing) return null;

	const metricValues = data.metricValues ?? JSON.parse(existing.metricValues);
	const intensityCache = extractIntensity(metricValues);

	db.update(entryTypeLogs)
		.set({
			...(data.startTime !== undefined && { startTime: data.startTime }),
			...(data.endTime !== undefined && { endTime: data.endTime }),
			...(data.metricValues !== undefined && {
				metricValues: JSON.stringify(data.metricValues),
				intensityCache
			})
		})
		.where(eq(entryTypeLogs.id, id))
		.run();

	const row = db.select().from(entryTypeLogs).where(eq(entryTypeLogs.id, id)).get()!;
	return parseLog(row);
}

export function deleteEntryTypeLog(id: string): void {
	db.delete(entryTypeLogs).where(eq(entryTypeLogs.id, id)).run();
}

function extractIntensity(metricValues: Record<string, unknown>): number | null {
	const v = metricValues['intensity'] ?? metricValues['severity'];
	if (typeof v === 'number') return v;
	if (typeof v === 'string') {
		const map: Record<string, number> = { mild: 1, moderate: 3, severe: 5 };
		return map[v] ?? null;
	}
	return null;
}

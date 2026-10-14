import { and, asc, eq } from 'drizzle-orm';
import { db } from '../db.js';
import { entryTriggers, triggers } from '../../db/schema.js';
import type { Trigger, TriggerCategory } from '../../types/index.js';
import { v4 as uuidv4 } from 'uuid';

type Row = typeof triggers.$inferSelect;
const toTrigger = (row: Row): Trigger => ({
	id: row.id,
	name: row.name,
	category: row.category as TriggerCategory,
	isBuiltIn: row.isBuiltIn,
	hidden: row.hidden
});

/** All triggers, deleted ones included (marked `hidden`), so past days can still name them. */
export function getAllTriggers(): Trigger[] {
	return db.select().from(triggers).orderBy(asc(triggers.category), asc(triggers.name)).all().map(toTrigger);
}

export function getTriggersByCategory(category: string): Trigger[] {
	return db
		.select()
		.from(triggers)
		.where(and(eq(triggers.category, category), eq(triggers.hidden, false)))
		.orderBy(asc(triggers.name))
		.all()
		.map(toTrigger);
}

export function getTrigger(id: string): Trigger | null {
	const row = db.select().from(triggers).where(eq(triggers.id, id)).get();
	return row ? toTrigger(row) : null;
}

/**
 * A new trigger, or a deleted one with the same name brought back.
 * `exists` when a visible trigger already has the name.
 */
export function createTrigger(data: { name: string; category?: TriggerCategory }): Trigger | 'exists' {
	const same = db.select().from(triggers).where(eq(triggers.name, data.name)).get();
	if (same && !same.hidden) return 'exists';
	if (same) {
		db.update(triggers)
			.set({ hidden: false, ...(data.category && { category: data.category }) })
			.where(eq(triggers.id, same.id))
			.run();
		return getTrigger(same.id)!;
	}
	const id = uuidv4();
	db.insert(triggers)
		.values({ id, name: data.name, category: data.category ?? 'custom', isBuiltIn: false })
		.run();
	return getTrigger(id)!;
}

/**
 * Rename, regroup or restore any trigger. A renamed built-in trigger becomes
 * the user's own, so its name is no longer translated.
 */
export function updateTrigger(
	id: string,
	data: { name?: string; category?: TriggerCategory; hidden?: boolean }
): Trigger | 'exists' | null {
	const existing = db.select().from(triggers).where(eq(triggers.id, id)).get();
	if (!existing) return null;
	const renamed = data.name !== undefined && data.name !== existing.name;
	if (renamed) {
		const same = db.select({ id: triggers.id }).from(triggers).where(eq(triggers.name, data.name!)).get();
		if (same) return 'exists';
	}
	db.update(triggers)
		.set({
			...(renamed && { name: data.name, isBuiltIn: false }),
			...(data.category !== undefined && { category: data.category }),
			...(data.hidden !== undefined && { hidden: data.hidden })
		})
		.where(eq(triggers.id, id))
		.run();
	return getTrigger(id);
}

/**
 * Built-in triggers and triggers used on any day are only hidden, so history
 * keeps them and the start-up seed does not add them again. Unused own
 * triggers are removed.
 */
export function deleteTrigger(id: string): void {
	const existing = db.select().from(triggers).where(eq(triggers.id, id)).get();
	if (!existing) return;
	const used = db.select({ id: entryTriggers.id }).from(entryTriggers).where(eq(entryTriggers.triggerId, id)).limit(1).get();
	if (existing.isBuiltIn || used) db.update(triggers).set({ hidden: true }).where(eq(triggers.id, id)).run();
	else db.delete(triggers).where(eq(triggers.id, id)).run();
}

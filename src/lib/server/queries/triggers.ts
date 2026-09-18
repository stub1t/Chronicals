import { eq, asc } from 'drizzle-orm';
import { db } from '../../db/index.js';
import { triggers } from '../../db/schema.js';
import type { Trigger, TriggerCategory } from '../../types/index.js';
import { v4 as uuidv4 } from 'uuid';

export function getAllTriggers(): Trigger[] {
	return db
		.select()
		.from(triggers)
		.orderBy(asc(triggers.category), asc(triggers.name))
		.all()
		.map((t) => ({ ...t, category: t.category as TriggerCategory }));
}

export function getTriggersByCategory(category: string): Trigger[] {
	return db
		.select()
		.from(triggers)
		.where(eq(triggers.category, category))
		.orderBy(asc(triggers.name))
		.all()
		.map((t) => ({ ...t, category: t.category as TriggerCategory }));
}

export function createTrigger(data: { name: string; category?: TriggerCategory }): Trigger {
	const id = uuidv4();
	db.insert(triggers)
		.values({
			id,
			name: data.name,
			category: data.category ?? 'custom',
			isBuiltIn: false
		})
		.run();
	const row = db.select().from(triggers).where(eq(triggers.id, id)).get()!;
	return { ...row, category: row.category as TriggerCategory };
}

export function updateTrigger(id: string, data: { name?: string; category?: TriggerCategory }): Trigger | null {
	const existing = db.select().from(triggers).where(eq(triggers.id, id)).get();
	if (!existing || existing.isBuiltIn) return null;

	db.update(triggers)
		.set({
			...(data.name !== undefined && { name: data.name }),
			...(data.category !== undefined && { category: data.category })
		})
		.where(eq(triggers.id, id))
		.run();
	const row = db.select().from(triggers).where(eq(triggers.id, id)).get();
	return row ? { ...row, category: row.category as TriggerCategory } : null;
}

export function deleteTrigger(id: string): void {
	const existing = db.select().from(triggers).where(eq(triggers.id, id)).get();
	if (!existing || existing.isBuiltIn) return;
	db.delete(triggers).where(eq(triggers.id, id)).run();
}

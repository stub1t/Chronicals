import { and, eq, sql } from 'drizzle-orm';
import { db } from '../db.js';
import { entryTriggers, medicationChanges, medications, triggers, userProfile } from '../../db/schema.js';
import { createEntry } from './entries.js';
import type { UserProfile, Medication, MedicationChange, MedicationChangeType } from '../../types/index.js';
import { v4 as uuidv4 } from 'uuid';

export function getProfile(): UserProfile {
	const row = db.select().from(userProfile).where(eq(userProfile.id, 'default')).get()!;
	return {
		...row,
		bloodType: row.bloodType as UserProfile['bloodType']
	};
}

export function updateProfile(data: Partial<Omit<UserProfile, 'id' | 'updatedAt'>>): UserProfile {
	db.update(userProfile)
		.set({ ...data, updatedAt: sql`(datetime('now'))` })
		.where(eq(userProfile.id, 'default'))
		.run();
	return getProfile();
}

function parseMedication(row: typeof medications.$inferSelect): Medication {
	return {
		...row,
		reminderTimes: row.reminderTimes ? JSON.parse(row.reminderTimes) : [],
		forTypes: row.forTypes ? JSON.parse(row.forTypes) : []
	};
}

export function getMedications(): Medication[] {
	return db.select().from(medications).all().map(parseMedication);
}

export function getMedication(id: string): Medication | null {
	const row = db.select().from(medications).where(eq(medications.id, id)).get();
	return row ? parseMedication(row) : null;
}

export function createMedication(data: {
	name: string;
	dosage?: string;
	unit?: string;
	startDate: string;
	notes?: string;
	reminderTimes?: string[];
	forTypes?: string[];
}): Medication {
	const id = uuidv4();
	db.insert(medications)
		.values({
			id,
			name: data.name,
			dosage: data.dosage ?? null,
			unit: data.unit ?? null,
			startDate: data.startDate,
			notes: data.notes ?? null,
			isActive: true,
			reminderTimes: data.reminderTimes?.length ? JSON.stringify(data.reminderTimes) : null,
			forTypes: data.forTypes?.length ? JSON.stringify(data.forTypes) : null
		})
		.run();
	markMedicationDay(data.startDate, [data.name, data.dosage && `${data.dosage}${data.unit ? ` ${data.unit}` : ''}`].filter(Boolean).join(' '));
	return getMedication(id)!;
}

/**
 * A start, stop or dose change is also the trigger "Medication change" on
 * that day of the diary (unless the user deleted that trigger), so the
 * analysis and the calendar see it.
 */
function markMedicationDay(date: string, note: string) {
	const trigger = db.select().from(triggers).where(eq(triggers.id, 'trig-medication-change')).get();
	if (!trigger || trigger.hidden) return;
	const { entry } = createEntry(date);
	const linked = db
		.select({ id: entryTriggers.id, note: entryTriggers.note })
		.from(entryTriggers)
		.where(and(eq(entryTriggers.entryId, entry.id), eq(entryTriggers.triggerId, trigger.id)))
		.get();
	if (!linked) {
		db.insert(entryTriggers).values({ id: uuidv4(), entryId: entry.id, triggerId: trigger.id, note }).run();
	} else if (!linked.note?.includes(note)) {
		db.update(entryTriggers)
			.set({ note: [linked.note, note].filter(Boolean).join('; ') })
			.where(eq(entryTriggers.id, linked.id))
			.run();
	}
}

export function updateMedication(id: string, data: Partial<Omit<Medication, 'id'>>): Medication | null {
	const { reminderTimes, forTypes, ...rest } = data;
	db.update(medications)
		.set({
			...rest,
			...(reminderTimes !== undefined && {
				reminderTimes: reminderTimes.length ? JSON.stringify(reminderTimes) : null
			}),
			...(forTypes !== undefined && { forTypes: forTypes.length ? JSON.stringify(forTypes) : null })
		})
		.where(eq(medications.id, id))
		.run();
	return getMedication(id);
}

export function deleteMedication(id: string): void {
	db.delete(medications).where(eq(medications.id, id)).run();
}

export function getMedicationChanges(medicationId?: string): MedicationChange[] {
	const rows = medicationId
		? db.select().from(medicationChanges).where(eq(medicationChanges.medicationId, medicationId)).all()
		: db.select().from(medicationChanges).all();
	return rows.map((r) => ({ ...r, changeType: r.changeType as MedicationChangeType }));
}

export function createMedicationChange(data: {
	medicationId?: string;
	changeType: MedicationChangeType;
	oldDosage?: string;
	newDosage?: string;
	changeDate: string;
	reason?: string;
	notes?: string;
}): MedicationChange {
	const id = uuidv4();
	db.transaction((tx) => {
		tx.insert(medicationChanges)
			.values({
				id,
				medicationId: data.medicationId ?? null,
				changeType: data.changeType,
				oldDosage: data.oldDosage ?? null,
				newDosage: data.newDosage ?? null,
				changeDate: data.changeDate,
				reason: data.reason ?? null,
				notes: data.notes ?? null
			})
			.run();

		// Stopping or (re)starting a medication is recorded as a change and also
		// moves the medication between the active and past lists.
		if (data.medicationId && data.changeType !== 'dosage_change') {
			const stopped = data.changeType === 'stopped';
			tx.update(medications)
				.set({ isActive: !stopped, endDate: stopped ? data.changeDate : null })
				.where(eq(medications.id, data.medicationId))
				.run();
		}
	});
	const med = data.medicationId ? getMedication(data.medicationId) : null;
	if (med) {
		const dose = data.changeType === 'dosage_change' ? `: ${[data.oldDosage, data.newDosage].filter(Boolean).join(' → ')}` : '';
		markMedicationDay(data.changeDate, `${med.name}${dose}`);
	}
	const row = db.select().from(medicationChanges).where(eq(medicationChanges.id, id)).get()!;
	return { ...row, changeType: row.changeType as MedicationChangeType };
}

import { eq, sql } from 'drizzle-orm';
import { db } from '../db.js';
import { userProfile, medications, medicationChanges } from '../../db/schema.js';
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
	return { ...row, reminderTimes: row.reminderTimes ? JSON.parse(row.reminderTimes) : [] };
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
			reminderTimes: data.reminderTimes?.length ? JSON.stringify(data.reminderTimes) : null
		})
		.run();
	return getMedication(id)!;
}

export function updateMedication(id: string, data: Partial<Omit<Medication, 'id'>>): Medication | null {
	const { reminderTimes, ...rest } = data;
	db.update(medications)
		.set({
			...rest,
			...(reminderTimes !== undefined && {
				reminderTimes: reminderTimes.length ? JSON.stringify(reminderTimes) : null
			})
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
	const row = db.select().from(medicationChanges).where(eq(medicationChanges.id, id)).get()!;
	return { ...row, changeType: row.changeType as MedicationChangeType };
}

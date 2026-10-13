import type { Medication } from '$lib/types/index.js';

/** Device-only reminder settings (Android app). Medication times live on each medication. */
export interface ReminderSettings {
	daily: boolean;
	/** 'HH:MM' */
	time: string;
}

export interface PlannedReminder {
	id: number;
	kind: 'daily' | 'medication';
	title: string;
	body: string;
	hour: number;
	minute: number;
}

const STORAGE_KEY = 'chronicals-reminders';
const DAILY_ID = 1;

export function loadReminderSettings(): ReminderSettings {
	try {
		const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
		if (stored && typeof stored.daily === 'boolean' && /^\d{2}:\d{2}$/.test(stored.time)) return stored;
	} catch {
		// Fall through to the defaults.
	}
	return { daily: false, time: '20:00' };
}

export function saveReminderSettings(settings: ReminderSettings): void {
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
	} catch {
		// Storage unavailable: the setting just isn't remembered.
	}
}

/** Stable positive id per medication and time (FNV-1a), never the daily reminder's id. */
function reminderId(key: string): number {
	let hash = 0x811c9dc5;
	for (let i = 0; i < key.length; i++) {
		hash ^= key.charCodeAt(i);
		hash = Math.imul(hash, 0x01000193);
	}
	return ((hash >>> 0) % 2_000_000_000) + DAILY_ID + 1;
}

/** Every reminder that should be scheduled: the daily one if on, and each time of each active medication. */
export function planReminders(
	settings: ReminderSettings,
	medications: Medication[],
	text: { dailyTitle: string; dailyBody: string; medication: (name: string, dosage: string) => string }
): PlannedReminder[] {
	const at = (time: string) => {
		const [hour, minute] = time.split(':').map(Number);
		return { hour, minute };
	};
	const planned: PlannedReminder[] = [];

	if (settings.daily) {
		planned.push({ id: DAILY_ID, kind: 'daily', title: text.dailyTitle, body: text.dailyBody, ...at(settings.time) });
	}
	for (const med of medications) {
		if (!med.isActive) continue;
		const dosage = [med.dosage, med.unit].filter(Boolean).join(' ');
		for (const time of med.reminderTimes ?? []) {
			planned.push({
				id: reminderId(`${med.id}|${time}`),
				kind: 'medication',
				title: med.name,
				body: text.medication(med.name, dosage),
				...at(time)
			});
		}
	}
	return planned;
}

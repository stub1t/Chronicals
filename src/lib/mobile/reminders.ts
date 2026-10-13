/**
 * Scheduling of reminders as Android notifications. Everything pending is
 * replaced on every sync, so the schedule always matches the settings and the
 * medications in the database.
 */
import { LocalNotifications } from '@capacitor/local-notifications';
import { goto } from '$app/navigation';
import { loadReminderSettings, planReminders } from '$lib/reminders.js';
import { t } from '$lib/stores/locale.js';
import type { Medication } from '$lib/types/index.js';
import { toDateString } from '$lib/utils/date';
import { localFetch } from './local-api.js';

let queue: Promise<void> = Promise.resolve();

/** Re-plan all reminders. Calls are serialised. */
export function syncReminders(): Promise<void> {
	queue = queue.catch(() => {}).then(sync);
	return queue;
}

async function sync(): Promise<void> {
	const res = await localFetch('/api/medications');
	const medications: Medication[] = res.ok ? await res.json() : [];
	const planned = planReminders(loadReminderSettings(), medications, {
		dailyTitle: t('reminder_daily_title'),
		dailyBody: t('reminder_daily_body'),
		medication: (name, dosage) => t('reminder_medication_body', { name: [name, dosage].filter(Boolean).join(' ') })
	});

	const pending = await LocalNotifications.getPending();
	if (pending.notifications.length > 0) {
		await LocalNotifications.cancel({ notifications: pending.notifications.map(({ id }) => ({ id })) });
	}
	if (planned.length === 0 || (await LocalNotifications.checkPermissions()).display !== 'granted') return;

	await LocalNotifications.schedule({
		notifications: planned.map((r) => ({
			id: r.id,
			title: r.title,
			body: r.body,
			schedule: { on: { hour: r.hour, minute: r.minute }, allowWhileIdle: true },
			// Inexact on purpose: exact alarms need a special permission, and a
			// reminder a few minutes late is fine.
			isExactNotification: false,
			smallIcon: 'ic_stat_chronicals',
			extra: { kind: r.kind }
		}))
	});
}

/** Ask for notification permission (Android 13+). True if notifications may be shown. */
export async function requestReminderPermission(): Promise<boolean> {
	const status = await LocalNotifications.checkPermissions();
	if (status.display === 'granted') return true;
	return (await LocalNotifications.requestPermissions()).display === 'granted';
}

/** Tapping the daily reminder opens today's day view. */
export function listenForReminderTaps(): void {
	LocalNotifications.addListener('localNotificationActionPerformed', ({ notification }) => {
		if (notification.extra?.kind === 'daily') goto(`/calendar/day/${toDateString(new Date())}`);
	});
}

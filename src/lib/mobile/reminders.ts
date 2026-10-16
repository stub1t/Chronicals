/**
 * Scheduling of reminders as Android notifications. Everything pending is
 * replaced on every sync, so the schedule always matches the settings and the
 * medications in the database.
 */
import { LocalNotifications } from '@capacitor/local-notifications';
import { goto } from '$app/navigation';
import { loadReminderSettings, planOngoing, planReminders } from '$lib/reminders.js';
import { localizeBuiltInText, t } from '$lib/i18n';
import type { Entry, Medication } from '$lib/types/index.js';
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
	const yesterday = new Date();
	yesterday.setDate(yesterday.getDate() - 1);
	const recent = await localFetch(`/api/entries?from=${toDateString(yesterday)}&to=${toDateString(new Date())}`);
	const entries: Entry[] = recent.ok ? await recent.json() : [];
	const settings = loadReminderSettings();
	const ongoing = planOngoing(entries, settings, new Date(), {
		title: (name) => t('reminder_ongoing_title', { name }),
		body: (name) => t('reminder_ongoing_body', { name }),
		name: localizeBuiltInText
	});
	const planned = planReminders(settings, medications, {
		dailyTitle: t('reminder_daily_title'),
		dailyBody: t('reminder_daily_body'),
		medication: (name, dosage) => t('reminder_medication_body', { name: [name, dosage].filter(Boolean).join(' ') })
	});

	const pending = await LocalNotifications.getPending();
	if (pending.notifications.length > 0) {
		await LocalNotifications.cancel({ notifications: pending.notifications.map(({ id }) => ({ id })) });
	}
	if (planned.length + ongoing.length === 0 || (await LocalNotifications.checkPermissions()).display !== 'granted') return;

	await LocalNotifications.schedule({
		notifications: [
			...ongoing.map((r) => ({
				id: r.id,
				title: r.title,
				body: r.body,
				schedule: { at: r.at, allowWhileIdle: true },
				isExactNotification: false,
				smallIcon: 'ic_stat_chronicals',
				extra: { kind: 'ongoing', date: r.date, logId: r.logId }
			})),
			...planned.map((r) => ({
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
		]
	});
}

/** Ask for notification permission (Android 13+). True if notifications may be shown. */
export async function requestReminderPermission(): Promise<boolean> {
	const status = await LocalNotifications.checkPermissions();
	if (status.display === 'granted') return true;
	return (await LocalNotifications.requestPermissions()).display === 'granted';
}

/** Tapping the daily reminder opens Today. */
export function listenForReminderTaps(): void {
	LocalNotifications.addListener('localNotificationActionPerformed', ({ notification }) => {
		const extra = notification.extra ?? {};
		if (extra.kind === 'daily') goto('/');
		// "Still going?": open that day with the log's sheet, to enter the end.
		if (extra.kind === 'ongoing') {
			const base = extra.date === toDateString(new Date()) ? '/' : `/calendar/day/${extra.date}`;
			goto(`${base}?log=${encodeURIComponent(extra.logId)}`);
		}
	});
}

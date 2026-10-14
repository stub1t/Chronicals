import { describe, expect, it } from 'vitest';
import type { Medication } from '$lib/types/index.js';
import { planOngoing, planReminders } from './reminders.js';

const text = { dailyTitle: 'Diary', dailyBody: 'How was your day?', medication: (name: string, dosage: string) => `Take ${name} ${dosage}` };
const med = (id: string, times: string[], isActive = true) =>
	({ id, name: id, dosage: '400', unit: 'mg', startDate: '2026-01-01', endDate: null, notes: null, isActive, reminderTimes: times }) as Medication;

describe('planReminders', () => {
	it('plans the daily reminder only when it is on', () => {
		expect(planReminders({ daily: false, time: '20:00', ongoingHours: 0 }, [], text)).toEqual([]);
		expect(planReminders({ daily: true, time: '20:30', ongoingHours: 0 }, [], text)).toEqual([
			{ id: 1, kind: 'daily', title: 'Diary', body: 'How was your day?', hour: 20, minute: 30 }
		]);
	});

	it('plans each time of each active medication and skips stopped ones', () => {
		const planned = planReminders({ daily: false, time: '20:00', ongoingHours: 0 }, [med('Magnesium', ['08:00', '20:00']), med('Old', ['09:00'], false)], text);
		expect(planned.map((r) => [r.title, r.hour, r.minute, r.body])).toEqual([
			['Magnesium', 8, 0, 'Take Magnesium 400 mg'],
			['Magnesium', 20, 0, 'Take Magnesium 400 mg']
		]);
	});

	it('gives stable, distinct ids that never collide with the daily reminder', () => {
		const meds = [med('a', ['08:00', '20:00']), med('b', ['08:00'])];
		const first = planReminders({ daily: true, time: '20:00', ongoingHours: 0 }, meds, text).map((r) => r.id);
		const second = planReminders({ daily: true, time: '20:00', ongoingHours: 0 }, meds, text).map((r) => r.id);
		expect(first).toEqual(second);
		expect(new Set(first).size).toBe(first.length);
		expect(first.filter((id) => id === 1)).toHaveLength(1);
		expect(first.every((id) => Number.isInteger(id) && id > 0 && id < 2 ** 31)).toBe(true);
	});
});

describe('planOngoing', () => {
	const text = { title: (n: string) => `${n} still there?`, body: () => 'body', name: (n: string) => n };
	const log = (id: string, start: string, end: string | null, category = 'symptom') => ({
		id,
		entryId: 'e',
		entryTypeId: 't',
		startTime: start,
		endTime: end,
		metricValues: {},
		intensityCache: null,
		entryType: { id: 't', name: 'Headache', category } as never
	});
	const entries = [
		{
			id: 'e',
			date: '2026-03-01',
			logs: [
				log('open', '2026-03-01T08:00:00.000Z', null),
				log('ended', '2026-03-01T08:00:00.000Z', '2026-03-01T09:00:00.000Z'),
				log('meal', '2026-03-01T08:00:00.000Z', null, 'meal'),
				log('late', '2026-03-01T05:00:00.000Z', null)
			]
		}
	] as never;
	const now = new Date('2026-03-01T09:00:00.000Z');

	it('reminds about logs that are still going, hours after their start', () => {
		const planned = planOngoing(entries, { daily: false, time: '20:00', ongoingHours: 3 }, now, text);
		expect(planned.map((p) => [p.logId, p.at.toISOString(), p.title, p.date])).toEqual([
			['open', '2026-03-01T11:00:00.000Z', 'Headache still there?', '2026-03-01']
		]);
	});

	it('is off with 0 hours and skips logs from more than a day ago', () => {
		expect(planOngoing(entries, { daily: false, time: '20:00', ongoingHours: 0 }, now, text)).toEqual([]);
		expect(planOngoing(entries, { daily: false, time: '20:00', ongoingHours: 3 }, new Date('2026-03-02T10:00:00.000Z'), text)).toEqual([]);
	});
});

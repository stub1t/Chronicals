import { describe, expect, it } from 'vitest';
import type { Medication } from '$lib/types/index.js';
import { planReminders } from './reminders.js';

const text = { dailyTitle: 'Diary', dailyBody: 'How was your day?', medication: (name: string, dosage: string) => `Take ${name} ${dosage}` };
const med = (id: string, times: string[], isActive = true) =>
	({ id, name: id, dosage: '400', unit: 'mg', startDate: '2026-01-01', endDate: null, notes: null, isActive, reminderTimes: times }) as Medication;

describe('planReminders', () => {
	it('plans the daily reminder only when it is on', () => {
		expect(planReminders({ daily: false, time: '20:00' }, [], text)).toEqual([]);
		expect(planReminders({ daily: true, time: '20:30' }, [], text)).toEqual([
			{ id: 1, kind: 'daily', title: 'Diary', body: 'How was your day?', hour: 20, minute: 30 }
		]);
	});

	it('plans each time of each active medication and skips stopped ones', () => {
		const planned = planReminders({ daily: false, time: '20:00' }, [med('Magnesium', ['08:00', '20:00']), med('Old', ['09:00'], false)], text);
		expect(planned.map((r) => [r.title, r.hour, r.minute, r.body])).toEqual([
			['Magnesium', 8, 0, 'Take Magnesium 400 mg'],
			['Magnesium', 20, 0, 'Take Magnesium 400 mg']
		]);
	});

	it('gives stable, distinct ids that never collide with the daily reminder', () => {
		const meds = [med('a', ['08:00', '20:00']), med('b', ['08:00'])];
		const first = planReminders({ daily: true, time: '20:00' }, meds, text).map((r) => r.id);
		const second = planReminders({ daily: true, time: '20:00' }, meds, text).map((r) => r.id);
		expect(first).toEqual(second);
		expect(new Set(first).size).toBe(first.length);
		expect(first.filter((id) => id === 1)).toHaveLength(1);
		expect(first.every((id) => Number.isInteger(id) && id > 0 && id < 2 ** 31)).toBe(true);
	});
});

import { describe, expect, it } from 'vitest';
import type { Entry, EnvironmentalData, Medication, MedicationChange } from '$lib/types';
import { addDays, daysBetween, eachDay, weekStart, weekdayIndex } from './dates.js';
import { fisherOneSided } from './fisher.js';
import {
	byCyclePhase,
	byTimeOfDay,
	byWeekday,
	compare,
	dailyLevels,
	indexDiary,
	links,
	medicationEffects,
	medicationEvents,
	overview,
	weeks
} from './engine.js';

const HEADACHE = 'builtin-headache';
const PERIOD = 'builtin-period';

interface DaySpec {
	types?: string[];
	triggers?: string[];
	intensity?: number;
	wellbeing?: number;
	/** Local start hour of the logs. */
	hour?: number;
}

/** Diary entries from a map of date to what happened. Every listed date is a tracked day. */
function diaryOf(days: Record<string, DaySpec>): Entry[] {
	return Object.entries(days).map(([date, spec]) => ({
		id: date,
		date,
		note: spec.types?.length || spec.triggers?.length || spec.wellbeing ? null : 'tracked',
		wellbeing: spec.wellbeing ?? null,
		createdAt: date,
		updatedAt: date,
		logs: (spec.types ?? []).map((type, i) => {
			const start = new Date(`${date}T00:00:00`);
			start.setHours(spec.hour ?? 9, i);
			return {
				id: `${date}-${type}`,
				entryId: date,
				entryTypeId: type,
				startTime: start.toISOString(),
				endTime: new Date(start.getTime() + 2 * 3_600_000).toISOString(),
				metricValues: {},
				intensityCache: spec.intensity ?? null
			};
		}),
		triggers: (spec.triggers ?? []).map((id) => ({ id: `${date}-${id}`, entryId: date, triggerId: id, triggerName: id, note: null }))
	})) as unknown as Entry[];
}

describe('dates', () => {
	it('does calendar arithmetic without time zone drift', () => {
		expect(addDays('2026-03-28', 2)).toBe('2026-03-30');
		expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
		expect(daysBetween('2026-01-01', '2026-12-31')).toBe(364);
		expect(weekdayIndex('2026-10-12')).toBe(0); // Monday
		expect(weekStart('2026-10-18')).toBe('2026-10-12');
		expect(eachDay('2026-10-30', '2026-11-02')).toEqual(['2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02']);
	});
});

describe('fisherOneSided', () => {
	it('matches exact values', () => {
		// The tea tasting table: 17/70.
		expect(fisherOneSided(3, 1, 1, 3).greater).toBeCloseTo(0.242857, 5);
		expect(fisherOneSided(8, 2, 1, 5).greater).toBeCloseTo(0.0244755, 6);
		expect(fisherOneSided(1, 9, 11, 3).less).toBeCloseTo(0.00137973, 7);
		expect(fisherOneSided(12, 8, 10, 60).greater).toBeCloseTo(0.000103943, 8);
	});

	it('gives 1 on both sides when nothing can differ', () => {
		const { greater, less } = fisherOneSided(0, 5, 0, 5);
		expect(greater).toBeCloseTo(1);
		expect(less).toBeCloseTo(1);
	});
});

describe('compare', () => {
	it('labels by p-value and ratio', () => {
		expect(compare(8, 2, 5, 25).label).toBe('likely');
		expect(compare(2, 2, 5, 25).label).toBe('not-enough');
		expect(compare(1, 9, 15, 15).label).toBe('fewer');
		expect(compare(3, 7, 9, 21).label).toBe('none');
		expect(compare(5, 5, 0, 10).ratio).toBe(Infinity);
	});
});

describe('trigger links', () => {
	// 40 tracked days. Poor sleep on 10 of them, each followed by a headache the next day.
	// One unrelated headache elsewhere. Caffeine on 10 days with no pattern.
	const days: Record<string, DaySpec> = {};
	const start = '2026-01-01';
	for (let i = 0; i < 40; i++) days[addDays(start, i)] = { triggers: i % 4 === 2 ? ['caffeine'] : [] };
	for (let i = 0; i < 40; i += 4) days[addDays(start, i)].triggers!.push('poor-sleep');
	for (let i = 1; i < 40; i += 4) days[addDays(start, i)].types = [HEADACHE];
	days[addDays(start, 30)].types = [HEADACHE];
	const diary = indexDiary(diaryOf(days));
	const result = links(diary, HEADACHE, start, addDays(start, 39), []);

	it('finds a day-before link', () => {
		const sleep = result.find((l) => l.key === 'poor-sleep')!;
		expect(sleep).toMatchObject({ lag: 1, label: 'likely', exposedDays: 10 });
		expect(sleep.withRate).toBe(1);
		expect(result[0].key).toBe('poor-sleep');
	});

	it('does not flag an unrelated trigger', () => {
		expect(['none', 'fewer']).toContain(result.find((l) => l.key === 'caffeine')!.label);
	});

	it('leaves untracked days out', () => {
		// The day before the first tracked day is unknown, so the day-before count has 39 days.
		const sleep = result.find((l) => l.key === 'poor-sleep')!;
		expect(sleep.exposedDays + sleep.unexposedDays).toBe(39);
	});

	it('treats weather as a trigger', () => {
		const weather = Object.keys(days).map((date, i) => ({ date, pressureDeltaHpa: i % 4 === 1 ? -7 : 1, temperatureC: 15, uvIndex: 2 }) as EnvironmentalData);
		const drop = links(diary, HEADACHE, start, addDays(start, 39), weather).find((l) => l.key === 'pressure-drop')!;
		expect(drop).toMatchObject({ kind: 'weather', lag: 0, label: 'likely' });
	});
});

describe('overview and series', () => {
	const days: Record<string, DaySpec> = {};
	// Previous 30 days: headache every 3rd day. Current 30 days: every 6th day.
	for (let i = 0; i < 60; i++) days[addDays('2026-01-01', i)] = { types: (i < 30 ? i % 3 : i % 6) === 0 ? [HEADACHE] : [], intensity: 4, wellbeing: 3 };
	const diary = indexDiary(diaryOf(days));
	const from = '2026-01-31';
	const to = '2026-03-01';

	it('compares with the previous period', () => {
		const o = overview(diary, HEADACHE, from, to);
		expect(o).toMatchObject({ trackedDays: 30, symptomDays: 5, per30: 5, trend: 'better', avgIntensity: 4, hours: 10 });
		expect(o.previous).toMatchObject({ trackedDays: 30, symptomDays: 10 });
	});

	it('gives no trend with too little data', () => {
		expect(overview(diary, HEADACHE, '2026-03-01', '2026-03-05').trend).toBeNull();
	});

	it('builds weeks and daily levels', () => {
		const w = weeks(diary, HEADACHE, from, to);
		expect(w[0].start).toBe(weekStart(from));
		expect(w.reduce((sum, x) => sum + x.trackedDays, 0)).toBe(30);
		expect(w.every((x) => x.avgWellbeing === 3)).toBe(true);
		const levels = dailyLevels(diary, HEADACHE, '2026-03-01', '2026-03-03');
		expect(levels.map((l) => l.level)).toEqual([0, null, null]);
	});

	it('counts weekdays and times of day', () => {
		const shares = byWeekday(diary, HEADACHE, from, to);
		expect(shares.reduce((sum, s) => sum + s.trackedDays, 0)).toBe(30);
		const parts = byTimeOfDay(indexDiary(diaryOf({ '2026-01-01': { types: [HEADACHE], hour: 2 }, '2026-01-02': { types: [HEADACHE], hour: 19 } })), HEADACHE, '2026-01-01', '2026-01-02');
		expect(parts).toEqual({ night: 1, morning: 0, afternoon: 0, evening: 1 });
	});
});

describe('cycle phases', () => {
	// Cycles of 28 days with 5 period days; headache on the 2 days before each period.
	const days: Record<string, DaySpec> = {};
	for (let i = 0; i < 112; i++) days[addDays('2026-01-01', i)] = { types: [] };
	for (let cycle = 0; cycle < 4; cycle++) {
		const s = cycle * 28 + 10;
		for (let k = 0; k < 5; k++) days[addDays('2026-01-01', s + k)].types!.push(PERIOD);
		days[addDays('2026-01-01', s - 1)].types!.push(HEADACHE);
		days[addDays('2026-01-01', s - 2)].types!.push(HEADACHE);
	}
	const diary = indexDiary(diaryOf(days));

	it('finds the phase before the period', () => {
		const result = byCyclePhase(diary, HEADACHE, PERIOD, '2026-01-01', addDays('2026-01-01', 111))!;
		expect(result.cycles).toBe(4);
		const before = result.phases.find((p) => p.phase === 'before')!;
		expect(before.rate).toBeCloseTo(8 / 12);
		expect(before.label).toBe('likely');
		expect(result.phases.find((p) => p.phase === 'early')!.symptomDays).toBe(0);
	});

	it('needs two cycles and another type', () => {
		expect(byCyclePhase(diary, PERIOD, PERIOD, '2026-01-01', '2026-04-01')).toBeNull();
		expect(byCyclePhase(indexDiary(diaryOf({ '2026-01-01': { types: [PERIOD] } })), HEADACHE, PERIOD, '2026-01-01', '2026-01-02')).toBeNull();
	});
});

describe('medications', () => {
	const med = { id: 'mag', name: 'Magnesium', dosage: '600', unit: 'mg', startDate: '2026-01-01', endDate: null, isActive: true } as Medication;
	const changes = [
		{ id: 'c1', medicationId: 'mag', changeType: 'dosage_change', oldDosage: '400 mg', newDosage: '600 mg', changeDate: '2026-03-02' }
	] as MedicationChange[];

	it('lists starts and dose changes', () => {
		const events = medicationEvents([med], changes);
		expect(events.map((e) => [e.date, e.kind, e.newDosage])).toEqual([
			['2026-03-02', 'dosage_change', '600 mg'],
			['2026-01-01', 'started', '400 mg']
		]);
	});

	it('compares 30 days before and after', () => {
		const days: Record<string, DaySpec> = {};
		// Headache every other day before the change, every 6th day after.
		for (let i = -30; i < 30; i++) days[addDays('2026-03-02', i)] = { types: (i < 0 ? i % 2 === 0 : i % 6 === 0) ? [HEADACHE] : [] };
		const diary = indexDiary(diaryOf(days));
		const [effect] = medicationEffects(diary, HEADACHE, medicationEvents([med], changes), '2026-02-01', '2026-04-30', '2026-04-30');
		expect(effect.event.kind).toBe('dosage_change');
		expect(effect.before).toMatchObject({ trackedDays: 30, symptomDays: 15 });
		expect(effect.after).toMatchObject({ trackedDays: 30, symptomDays: 5 });
		expect(effect.result).toBe('fewer');
	});

	it('says nothing with too few tracked days', () => {
		const diary = indexDiary(diaryOf({ '2026-03-01': {}, '2026-03-03': {} }));
		expect(medicationEffects(diary, HEADACHE, medicationEvents([med], changes), '2026-02-01', '2026-04-30', '2026-04-30')[0].result).toBeNull();
	});
});

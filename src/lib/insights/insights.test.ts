import { describe, expect, it } from 'vitest';
import type { Entry, EnvironmentalData, Medication, MedicationChange } from '$lib/types';
import { addDays, daysBetween, eachDay, monthsBefore, weekStart, weekdayIndex } from './dates.js';
import { fisherOneSided } from './fisher.js';
import {
	byCyclePhase,
	byTimeOfDay,
	byWeekday,
	benjaminiHochberg,
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

	it('counts months back without running over short months', () => {
		expect(monthsBefore('2026-10-14', 3)).toBe('2026-07-15');
		expect(monthsBefore('2026-05-31', 3)).toBe('2026-03-01');
		expect(monthsBefore('2026-08-31', 6)).toBe('2026-03-01');
		expect(monthsBefore('2026-12-31', 3)).toBe('2026-10-01');
		expect(monthsBefore('2026-03-15', 12)).toBe('2025-03-16');
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

describe('benjaminiHochberg', () => {
	it('keeps the smallest p-values that hold at q', () => {
		expect(benjaminiHochberg([0.001, 0.04, 0.3, 0.5], 0.1)).toEqual([true, true, false, false]);
		expect(benjaminiHochberg([0.04, 0.2], 0.1)).toEqual([true, false]);
		expect(benjaminiHochberg([0.06, 0.07, 0.08], 0.1)).toEqual([true, true, true]);
		expect(benjaminiHochberg([])).toEqual([]);
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
		expect(o).toMatchObject({ trackedDays: 30, symptomDays: 5, per30: 5, trend: 'better', avgIntensity: 4, reliefDays: 0 });
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

/** A small deterministic random generator for simulated diaries. */
function random(seed: number) {
	return () => ((seed = (seed * 1664525 + 1013904223) % 4294967296) / 4294967296);
}

describe('findings of the review', () => {
	it('rarely calls unrelated triggers a likely link', () => {
		let likely = 0;
		let tested = 0;
		for (let seed = 1; seed <= 40; seed++) {
			const rand = random(seed);
			const days: Record<string, DaySpec> = {};
			for (let i = 0; i < 90; i++) {
				days[addDays('2026-01-01', i)] = {
					types: rand() < 0.25 ? [HEADACHE] : [],
					triggers: Array.from({ length: 10 }, (_, k) => `t${k}`).filter(() => rand() < 0.2)
				};
			}
			const result = links(indexDiary(diaryOf(days)), HEADACHE, '2026-01-01', addDays('2026-01-01', 89), []);
			likely += result.filter((l) => l.label === 'likely').length;
			tested += result.length;
		}
		// Without the corrections about one in twenty was "likely" by chance; now it is 4 in 400.
		expect(likely / tested).toBeLessThanOrEqual(0.015);
	});

	it('doubles the p-value for picking the better of the two days', () => {
		const days: Record<string, DaySpec> = {};
		for (let i = 0; i < 30; i++) days[addDays('2026-01-01', i)] = { types: i % 3 === 0 ? [HEADACHE] : [], triggers: i % 3 === 0 ? ['x'] : [] };
		const [link] = links(indexDiary(diaryOf(days)), HEADACHE, '2026-01-01', '2026-01-30', []);
		expect(link.lag).toBe(0);
		expect(link.p).toBeCloseTo(2 * fisherOneSided(10, 0, 0, 20).greater, 10);
	});

	it('does not call a trend on one day of difference', () => {
		const days: Record<string, DaySpec> = {};
		// 14 tracked days each side: 3 headache days before, 2 after.
		for (let i = 0; i < 14; i++) {
			days[addDays('2026-01-01', i)] = { types: i < 3 ? [HEADACHE] : [] };
			days[addDays('2026-01-15', i)] = { types: i < 2 ? [HEADACHE] : [] };
		}
		expect(overview(indexDiary(diaryOf(days)), HEADACHE, '2026-01-15', '2026-01-28').trend).toBe('same');
		// With too few days there is no trend at all.
		expect(overview(indexDiary(diaryOf(days)), HEADACHE, '2026-01-22', '2026-01-28').trend).toBeNull();
	});

	it('does not see two periods in one period with a gap', () => {
		const days: Record<string, DaySpec> = {};
		for (let i = 0; i < 60; i++) days[addDays('2026-01-01', i)] = {};
		for (const d of ['2026-01-10', '2026-01-11', '2026-01-13', '2026-01-14']) days[d] = { types: [PERIOD] };
		delete days['2026-01-12'];
		const diary = indexDiary(diaryOf(days));
		expect(byCyclePhase(diary, HEADACHE, PERIOD, '2026-01-01', '2026-03-01')).toBeNull();
	});

	it('ends the comparison after a medication when it was stopped', () => {
		const med = { id: 'm', name: 'Magnesium', dosage: '400', unit: 'mg', startDate: '2026-03-01', endDate: '2026-03-10', isActive: false } as Medication;
		const stop = [{ id: 's', medicationId: 'm', changeType: 'stopped', changeDate: '2026-03-10' }] as MedicationChange[];
		const [event] = medicationEvents([med], stop);
		expect(event.stopDate).toBe('2026-03-10');
		const days: Record<string, DaySpec> = {};
		for (let i = -30; i < 30; i++) days[addDays('2026-03-01', i)] = {};
		const [effect] = medicationEffects(indexDiary(diaryOf(days)), HEADACHE, [event], '2026-02-01', '2026-04-30', '2026-04-30');
		expect(effect.after).toMatchObject({ from: '2026-03-01', to: '2026-03-09', trackedDays: 9 });
		expect(effect.result).toBeNull();
	});

	it('shows only medications meant for the type, and notes other changes', () => {
		const a = { id: 'a', name: 'Cetirizine', startDate: '2026-03-01', forTypes: ['builtin-allergy'] } as unknown as Medication;
		const b = { id: 'b', name: 'Magnesium', startDate: '2026-03-05', forTypes: [] } as unknown as Medication;
		const events = medicationEvents([a, b], []);
		const effects = medicationEffects(indexDiary([]), HEADACHE, events, '2026-02-01', '2026-04-30', '2026-04-30');
		expect(effects.map((e) => e.event.name)).toEqual(['Magnesium']);
		expect(effects[0].alsoChanged).toEqual(['Cetirizine']);
	});

	it('shows no starting dose when it is unknown', () => {
		const med = { id: 'm', name: 'X', dosage: '20', unit: 'mg', startDate: '2026-03-01' } as Medication;
		const change = [{ id: 'c', medicationId: 'm', changeType: 'dosage_change', oldDosage: null, newDosage: '20 mg', changeDate: '2026-03-10' }] as MedicationChange[];
		expect(medicationEvents([med], change).find((e) => e.kind === 'started')!.newDosage).toBeNull();
	});

	it('lists "fewer symptoms" strongest first and tests foods from meals', () => {
		const days: Record<string, DaySpec> = {};
		for (let i = 0; i < 60; i++) {
			const date = addDays('2026-01-01', i);
			const run = i % 2 === 0;
			// Running days: never a headache. Walking days: rarely. Pasta: always with a headache.
			days[date] = { types: !run && i % 3 !== 0 ? [HEADACHE] : [], triggers: [run ? 'run' : '', i % 5 === 0 ? 'walk' : ''].filter(Boolean) };
		}
		const entries = diaryOf(days).map((e) => {
			const pasta = e.logs!.some((l) => l.entryTypeId === HEADACHE) && e.date < '2026-01-20';
			return pasta
				? { ...e, logs: [...e.logs!, { id: `${e.date}-meal`, entryId: e.id, entryTypeId: 'builtin-meal', startTime: `${e.date}T10:00:00.000Z`, endTime: null, metricValues: { foods: ['pasta'] }, intensityCache: null, entryType: { id: 'builtin-meal', category: 'meal' } as never }] }
				: e;
		});
		const result = links(indexDiary(entries), HEADACHE, '2026-01-01', '2026-03-01', []);
		const fewer = result.filter((l) => l.label === 'fewer');
		expect(fewer[0].key).toBe('run');
		expect(result.some((l) => l.kind === 'food' && l.key === 'pasta')).toBe(true);
	});

	it('treats pollen as an exposure and asks more of it', () => {
		const days: Record<string, DaySpec> = {};
		const weather: EnvironmentalData[] = [];
		for (let i = 0; i < 40; i++) {
			const date = addDays('2026-04-01', i);
			const high = i % 4 === 0;
			days[date] = { types: high ? [HEADACHE] : [] };
			weather.push({ date, pollenBirch: high ? 200 : 5 } as EnvironmentalData);
		}
		const birch = links(indexDiary(diaryOf(days)), HEADACHE, '2026-04-01', '2026-05-10', weather).find((l) => l.kind === 'pollen')!;
		expect(birch).toMatchObject({ key: 'birch', label: 'likely', lag: 0 });
	});
});

import { beforeEach, describe, expect, it } from 'vitest';
import { useTestDatabase } from '$lib/server/testing.js';
import { environmentalData } from '../../db/schema.js';
import { addEntryTrigger, createEntry } from './entries.js';
import { createEntryTypeLog } from './entryTypeLogs.js';
import { getEnvironmentalCorrelations, getTriggerCorrelations } from './analysis.js';

let db: ReturnType<typeof useTestDatabase>['db'];

beforeEach(() => {
	db = useTestDatabase().db;
});

const day = (n: number) => `2026-03-${String(n).padStart(2, '0')}`;

function logDay(n: number, { headache = false, triggers = [] as string[] } = {}) {
	const entry = createEntry(day(n));
	if (headache) {
		createEntryTypeLog({
			entryId: entry.id,
			entryTypeId: 'builtin-headache',
			startTime: `${day(n)}T08:00:00Z`,
			metricValues: { intensity: 3 }
		});
	}
	for (const trigger of triggers) addEntryTrigger(entry.id, trigger);
}

describe('getTriggerCorrelations', () => {
	it('computes phi from the 2x2 table of diary days', () => {
		// Headache on days 1-4, stress on days 1-3 and 5, ten diary days in total:
		// both 3, stress only 1, headache only 1, neither 5 → phi = (15 - 1) / 24
		for (let n = 1; n <= 10; n++) {
			logDay(n, { headache: n <= 4, triggers: n <= 3 || n === 5 ? ['trig-stress'] : [] });
		}
		const [stress] = getTriggerCorrelations('builtin-headache', day(1), day(31));
		expect(stress.triggerId).toBe('trig-stress');
		expect(stress.phi).toBeCloseTo(14 / 24, 6);
		expect(stress.strength).toBe('strong');
		expect(stress).toMatchObject({ coOccurrences: 3, daysWithTrigger: 4, daysWithSymptom: 4, totalDays: 10 });
	});

	it('ignores triggers that never coincide with the symptom', () => {
		logDay(1, { headache: true });
		logDay(2, { triggers: ['trig-caffeine'] });
		expect(getTriggerCorrelations('builtin-headache', day(1), day(31))).toEqual([]);
	});
});

describe('getEnvironmentalCorrelations', () => {
	it('treats days with a pressure drop below the median as the high group', () => {
		const deltas = [-8, -6, -1, 0, 2, 3];
		deltas.forEach((delta, i) => {
			db.insert(environmentalData).values({ id: `env-${i}`, date: day(i + 1), pressureDeltaHpa: delta }).run();
		});
		// Headache on the two strongest drops. Median is 0, so -8, -6 and -1 are "high":
		// both 2, high only 1, headache only 0, neither 3 → phi = 6 / sqrt(72)
		logDay(1, { headache: true });
		logDay(2, { headache: true });
		const [pressure] = getEnvironmentalCorrelations('builtin-headache', day(1), day(31));
		expect(pressure.variable).toBe('pressure_delta');
		expect(pressure.phi).toBeCloseTo(6 / Math.sqrt(72), 6);
	});
});

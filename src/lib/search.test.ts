import { describe, expect, it } from 'vitest';
import type { Entry } from '$lib/types/index.js';
import { matchEntry, normalizeText } from './search.js';

const german: Record<string, string> = { Headache: 'Kopfschmerz', temples: 'Schläfen', Stress: 'Stress', nausea: 'Übelkeit' };
const translate = (text: string) => german[text] ?? text;

const entry = {
	id: 'e1',
	date: '2026-09-21',
	note: 'Long day at the Café',
	createdAt: '',
	updatedAt: '',
	logs: [
		{
			id: 'l1',
			entryId: 'e1',
			entryTypeId: 'builtin-headache',
			entryType: { id: 'builtin-headache', name: 'Headache' },
			startTime: '2026-09-21T10:00:00Z',
			endTime: null,
			metricValues: { intensity: 4, location: 'temples', symptoms: ['nausea'] },
			intensityCache: 4
		}
	],
	triggers: [{ id: 't1', triggerId: 'trig-stress', triggerName: 'Stress', triggerCategory: 'activity', note: null }]
} as unknown as Entry;

describe('matchEntry', () => {
	const match = (query: string, typeId: string | null = null) => matchEntry(entry, query, typeId, translate);

	it('finds stored and translated names', () => {
		expect(match('headache')).toBe(true);
		expect(match('Kopfschmerz')).toBe(true);
		expect(match('schlafen')).toBe(true);
		expect(match('übelkeit')).toBe(true);
	});

	it('searches notes and triggers, ignoring case and accents', () => {
		expect(match('cafe')).toBe(true);
		expect(match('STRESS')).toBe(true);
	});

	it('needs every word to match', () => {
		expect(match('headache temples')).toBe(true);
		expect(match('headache forehead')).toBe(false);
	});

	it('filters by entry type', () => {
		expect(match('', 'builtin-headache')).toBe(true);
		expect(match('', 'builtin-allergy')).toBe(false);
	});

	it('does not match numbers in form values', () => {
		expect(match('4')).toBe(false);
	});
});

describe('normalizeText', () => {
	it('drops accents and case', () => {
		expect(normalizeText('Übelkeit Café')).toBe('ubelkeit cafe');
	});
});

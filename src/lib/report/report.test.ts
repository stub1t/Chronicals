import { describe, expect, it } from 'vitest';
import type { Entry, EntryType, Medication, MedicationChange } from '$lib/types/index.js';
import { reportToCsv } from './csv.js';
import { escapeHtml, reportToHtml } from './html.js';
import { buildReport, formatDuration, type ReportInput } from './summary.js';

const headache = {
	id: 'builtin-headache',
	name: 'Headache',
	category: 'symptom',
	metricSchema: {
		fields: [
			{ key: 'intensity', label: 'Intensity', type: 'slider' },
			{ key: 'location', label: 'Location', type: 'select' },
			{ key: 'symptoms', label: 'Symptoms', type: 'checklist' }
		]
	}
} as unknown as EntryType;
const allergy = { id: 'builtin-allergy', name: 'Allergy', category: 'symptom', metricSchema: { fields: [] } } as unknown as EntryType;

function entry(date: string, logs: Array<{ start: string; end?: string; intensity?: number; values?: Record<string, unknown> }>, extra: Partial<Entry> = {}): Entry {
	return {
		id: date,
		date,
		note: null,
		wellbeing: null,
		locationLat: null,
		locationLon: null,
		locationName: null,
		locationSource: null,
		createdAt: '',
		updatedAt: '',
		triggers: [],
		logs: logs.map((l, i) => ({
			id: `${date}-${i}`,
			entryId: date,
			entryTypeId: headache.id,
			entryType: headache,
			startTime: `${date}T${l.start}:00Z`,
			endTime: l.end ? `${date}T${l.end}:00Z` : null,
			metricValues: { intensity: l.intensity, ...l.values },
			intensityCache: l.intensity ?? null
		})),
		...extra
	};
}

const german: Record<string, string> = { Headache: 'Kopfschmerz', Location: 'Ort', temples: 'Schläfen', Symptoms: 'Symptome', nausea: 'Übelkeit', Stress: 'Stress', Painkiller: 'Schmerzmittel' };
const format = { translate: (s: string) => german[s] ?? s, time: (iso: string) => iso.slice(11, 16), exposureName: (_kind: string, key: string) => key };

const input: ReportInput = {
	from: '2026-09-01',
	to: '2026-09-30',
	name: 'Alex',
	entryTypes: [headache, allergy],
	entries: [
		entry('2026-08-31', [{ start: '08:00', intensity: 5 }]),
		entry('2026-09-03', [{ start: '08:00', end: '10:30', intensity: 4, values: { location: 'temples', symptoms: ['nausea'], relief: ['Painkiller'] } }], {
			note: 'Long day',
			wellbeing: 2,
			triggers: [{ id: 't', triggerId: 'trig-stress', triggerName: 'Stress', triggerCategory: 'activity', note: null }]
		}),
		entry('2026-09-10', [{ start: '14:00', end: '14:30', intensity: 2 }, { start: '20:00', intensity: 3 }]),
		entry('2026-09-12', [], { note: '=HYPERLINK("http://evil")' })
	],
	medications: [
		{ id: 'm1', name: 'Magnesium', dosage: '400', unit: 'mg', startDate: '2026-08-01', endDate: null, notes: null, isActive: true },
		{ id: 'm2', name: 'Old', dosage: null, unit: null, startDate: '2026-01-01', endDate: '2026-02-01', notes: null, isActive: false }
	] as Medication[],
	changes: [
		{ id: 'c1', medicationId: 'm1', changeType: 'dosage_change', oldDosage: '400 mg', newDosage: '600 mg', changeDate: '2026-09-15', reason: null, notes: null },
		{ id: 'c2', medicationId: 'm1', changeType: 'started', oldDosage: null, newDosage: '400 mg', changeDate: '2026-08-01', reason: null, notes: null }
	] as MedicationChange[],
	weather: []
};

describe('buildReport', () => {
	const report = buildReport(input, format);

	it('lists the days of the period with what belongs to the day', () => {
		expect(report.days.map((d) => d.date)).toEqual(['2026-09-03', '2026-09-10', '2026-09-12']);
		expect(report).toMatchObject({ trackedDays: 3, totalDays: 30 });
		expect(report.days[0]).toMatchObject({ wellbeing: 2, triggers: ['Stress'], note: 'Long day', place: null });
	});

	it('writes each entry with translated details, intensity and what was taken', () => {
		expect(report.days[0].entries).toEqual([
			{
				type: 'Kopfschmerz',
				start: '08:00',
				end: '10:30',
				startTime: '2026-09-03T08:00:00Z',
				endTime: '2026-09-03T10:30:00Z',
				durationMinutes: 150,
				intensity: 4,
				details: 'Ort: Schläfen; Symptome: Übelkeit',
				taken: 'Schmerzmittel'
			}
		]);
		expect(report.days[1].entries.map((e) => [e.start, e.end])).toEqual([['14:00', '14:30'], ['20:00', '']]);
	});

	it('summarises each entry type that was logged', () => {
		expect(report.types).toHaveLength(1);
		expect(report.types[0]).toMatchObject({ name: 'Kopfschmerz', days: 2, share: 2 / 3, avgIntensity: 3, typicalMinutes: 90, reliefDays: 1 });
		// Too few days for any link.
		expect(report.types[0].links).toEqual([]);
	});

	it('lists medications active in the period with their changes in the period', () => {
		expect(report.medications.map((m) => m.medication.name)).toEqual(['Magnesium']);
		expect(report.medications[0].changes.map((c) => c.id)).toEqual(['c1']);
	});

	it('can leave out types, and then lists only their days', () => {
		const only = buildReport({ ...input, typeIds: ['builtin-allergy'] }, format);
		expect(only.types).toEqual([]);
		expect(only.days).toEqual([]);
	});

	it('joins a value and its unit, and names a meal by its kind', () => {
		const sugar = { id: 'builtin-hypo', name: 'Blood sugar', category: 'event', metricSchema: { fields: [{ key: 'blood_glucose', label: 'Reading' }] } } as unknown as EntryType;
		const meal = { id: 'builtin-meal', name: 'Meal', category: 'meal', metricSchema: { fields: [{ key: 'foods', label: 'Foods' }] } } as unknown as EntryType;
		const day = entry('2026-09-20', []);
		day.logs = [
			{ id: 'a', entryId: day.id, entryTypeId: sugar.id, entryType: sugar, startTime: '2026-09-20T09:00:00Z', endTime: null, metricValues: { blood_glucose: 220, blood_glucose_unit: 'mg/dL' }, intensityCache: null },
			{ id: 'b', entryId: day.id, entryTypeId: meal.id, entryType: meal, startTime: '2026-09-20T12:00:00Z', endTime: null, metricValues: { meal: 'lunch', foods: ['pasta'] }, intensityCache: null }
		];
		const r = buildReport({ ...input, entries: [day], entryTypes: [sugar, meal] }, format);
		expect(r.days[0].entries.map((e) => [e.type, e.details])).toEqual([
			['Blood sugar', 'Reading: 220 mg/dL'],
			['lunch', 'Foods: pasta']
		]);
	});
});

describe('reportToCsv', () => {
	const report = buildReport(input, format);
	const headings = ['Date', 'Well-being', 'Triggers', 'Note', 'Place', 'Type', 'Start', 'End', 'Minutes', 'Intensity', 'Scale', 'Details', 'Taken'];
	const wellbeing = (n: number) => ['', 'very bad', 'bad', 'so-so', 'good', 'very good'][n];

	it('starts with a BOM and repeats the day on each of its entries', () => {
		const csv = reportToCsv(report, { separator: ';', headings, wellbeing });
		const lines = csv.split('\r\n');
		expect(lines[0]).toBe('\uFEFFDate;Well-being;Triggers;Note;Place;Type;Start;End;Minutes;Intensity;Scale;Details;Taken');
		expect(lines[1]).toBe('2026-09-03;bad;Stress;Long day;;Kopfschmerz;20:00;22:30;150;4;5;"Ort: Schläfen; Symptome: Übelkeit";Schmerzmittel');
		expect(lines[2]).toBe('2026-09-10;;;;;Kopfschmerz;02:00;02:30;30;2;5;;');
		expect(lines[3]).toBe('2026-09-10;;;;;Kopfschmerz;08:00;;;3;5;;');
	});

	it('gives a day without entries one row', () => {
		const csv = reportToCsv(report, { separator: ',', headings, wellbeing });
		expect(csv.split('\r\n')[4]).toMatch(/^2026-09-12,,,"'=HYPERLINK\(""http:\/\/evil""\)",,,,,,,,,$/);
	});

	it('neutralises cells a spreadsheet would run as a formula', () => {
		const csv = reportToCsv(report, { separator: ',', headings, wellbeing });
		expect(csv).not.toMatch(/(^|,)=HYPERLINK/m);
	});
});

describe('formatDuration', () => {
	it('formats minutes', () => {
		expect(formatDuration(153)).toBe('2 h 33 min');
		expect(formatDuration(120)).toBe('2 h');
		expect(formatDuration(45)).toBe('45 min');
		expect(formatDuration(null)).toBe('');
	});
});

describe('reportToHtml', () => {
	const options = {
		t: ((key: string, vars?: Record<string, unknown>) => (vars ? `${key} ${JSON.stringify(vars)}` : key)) as never,
		date: (d: string) => d,
		day: (d: string) => `day ${d}`,
		number: (v: number) => String(v),
		wellbeing: (n: number) => `mood ${n}`,
		lang: 'de',
		dir: 'ltr' as const,
		created: '2026-10-14'
	};

	it('is one document with every section and the diary by day', () => {
		const html = reportToHtml(buildReport(input, format), options);
		expect(html.startsWith('<!doctype html>')).toBe(true);
		expect(html).toContain('<html lang="de" dir="ltr">');
		for (const key of ['report_summary', 'report_triggers', 'report_medications', 'report_diary', 'report_disclaimer']) expect(html).toContain(key);
		expect(html).toContain('Magnesium');
		// The day's triggers and mood appear once, in the day's row, not on each entry.
		expect(html.match(/report_col_triggers: Stress/g)).toHaveLength(1);
		expect(html).toContain('day 2026-09-03');
		expect(html).toContain('report_wellbeing: mood 2');
		expect(html).toContain('<td>4/5</td>');
		expect(html).not.toContain('<script');
	});

	it('escapes everything from the diary', () => {
		const evil: ReportInput = { ...input, name: '<img src=x onerror=alert(1)>', entries: [entry('2026-09-05', [], { note: '</td><script>alert(1)</script>' })] };
		const html = reportToHtml(buildReport(evil, format), options);
		expect(html).not.toContain('<img');
		expect(html).not.toContain('<script');
		expect(html).toContain('&lt;/td&gt;&lt;script&gt;');
		expect(escapeHtml(`"'&`)).toBe('&quot;&#39;&amp;');
	});

	it('follows the text direction', () => {
		expect(reportToHtml(buildReport(input, format), { ...options, lang: 'ar', dir: 'rtl' })).toContain('<html lang="ar" dir="rtl">');
	});
});
describe('report links', () => {
	it('lists triggers with a clear link', () => {
		// Stress on 10 of 30 days, each with a headache; 2 headaches on the other days.
		const entries: Entry[] = [];
		for (let i = 1; i <= 30; i++) {
			const date = `2026-09-${String(i).padStart(2, '0')}`;
			const stress = i % 3 === 0;
			entries.push(
				entry(date, stress || i === 1 || i === 2 ? [{ start: '09:00', intensity: 3 }] : [], {
					note: 'day',
					triggers: stress ? [{ id: `t${i}`, triggerId: 'trig-stress', triggerName: 'Stress', triggerCategory: 'activity', note: null }] : []
				})
			);
		}
		const report = buildReport({ ...input, entries }, format);
		const [link] = report.types[0].links;
		expect(link).toMatchObject({ key: 'trig-stress', name: 'Stress', label: 'likely', lag: 0, exposedDays: 10, withRate: 1 });
	});
});

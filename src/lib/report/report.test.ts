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

const german: Record<string, string> = { Headache: 'Kopfschmerz', Location: 'Ort', temples: 'Schläfen', Symptoms: 'Symptome', nausea: 'Übelkeit', Stress: 'Stress' };
const format = { translate: (s: string) => german[s] ?? s, time: (iso: string) => iso.slice(11, 16), weatherName: (key: string) => key };

const input: ReportInput = {
	from: '2026-09-01',
	to: '2026-09-30',
	name: 'Alex',
	entryTypes: [headache, allergy],
	entries: [
		entry('2026-08-31', [{ start: '08:00', intensity: 5 }]),
		entry('2026-09-03', [{ start: '08:00', end: '10:30', intensity: 4, values: { location: 'temples', symptoms: ['nausea'] } }], {
			note: 'Long day',
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

	it('only uses entries inside the period', () => {
		expect(report.daysWithEntries).toBe(3);
		expect(report.rows.map((r) => r.date)).toEqual(['2026-09-03', '2026-09-10', '2026-09-10', '2026-09-12']);
	});

	it('summarises each entry type that was logged', () => {
		expect(report.types).toHaveLength(1);
		const [summary] = report.types;
		expect(summary).toMatchObject({ name: 'Kopfschmerz', days: 2, logs: 3, avgIntensity: 3, avgDurationMinutes: 90, per30: 20 });
		expect(report.trackedDays).toBe(3);
		// Too few days for any link.
		expect(summary.links).toEqual([]);
	});

	it('lists medications active in the period with their changes in the period', () => {
		expect(report.medications.map((m) => m.medication.name)).toEqual(['Magnesium']);
		expect(report.medications[0].changes.map((c) => c.id)).toEqual(['c1']);
	});

	it('writes translated details without the intensity', () => {
		expect(report.rows[0]).toMatchObject({
			type: 'Kopfschmerz',
			start: '08:00',
			end: '10:30',
			durationMinutes: 150,
			intensity: 4,
			details: 'Ort: Schläfen; Symptome: Übelkeit',
			triggers: 'Stress',
			note: 'Long day'
		});
	});
});

describe('reportToCsv', () => {
	const report = buildReport(input, format);
	const headings = ['Date', 'Type', 'Start', 'End', 'Minutes', 'Intensity', 'Details', 'Triggers', 'Note'];

	it('starts with a BOM and quotes cells containing the separator', () => {
		const csv = reportToCsv(report, { separator: ';', headings });
		expect(csv.startsWith('\uFEFFDate;Type;Start;End;Minutes;Intensity;Details;Triggers;Note\r\n')).toBe(true);
		expect(csv.split('\r\n')[1]).toBe(
			'2026-09-03;Kopfschmerz;08:00;10:30;150;4;"Ort: Schläfen; Symptome: Übelkeit";Stress;Long day'
		);
	});

	it('leaves cells unquoted when they do not contain the separator', () => {
		const csv = reportToCsv(report, { separator: ',', headings });
		expect(csv.split('\r\n')[1]).toBe('2026-09-03,Kopfschmerz,08:00,10:30,150,4,Ort: Schläfen; Symptome: Übelkeit,Stress,Long day');
	});

	it('neutralises cells a spreadsheet would run as a formula', () => {
		const csv = reportToCsv(report, { separator: ',', headings });
		expect(csv).toContain(`"'=HYPERLINK(""http://evil"")"`);
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
		number: (v: number) => String(v),
		lang: 'de',
		dir: 'ltr' as const,
		created: '2026-10-14'
	};

	it('is one document with every section', () => {
		const html = reportToHtml(buildReport(input, format), options);
		expect(html.startsWith('<!doctype html>')).toBe(true);
		expect(html).toContain('<html lang="de" dir="ltr">');
		for (const key of ['report_summary', 'report_triggers', 'report_medications', 'report_entries', 'report_disclaimer']) expect(html).toContain(key);
		expect(html).toContain('Magnesium');
		expect(html).toContain('Ort: Schläfen; Symptome: Übelkeit');
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

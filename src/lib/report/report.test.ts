import { describe, expect, it } from 'vitest';
import type { Entry, EntryType, Medication, MedicationChange } from '$lib/types/index.js';
import { reportToCsv } from './csv.js';
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
const format = { translate: (s: string) => german[s] ?? s, time: (iso: string) => iso.slice(11, 16) };

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
	correlations: {
		'builtin-headache': {
			triggers: [
				{ triggerId: 'a', triggerName: 'Stress', phi: 0.3, strength: 'possible' },
				{ triggerId: 'b', triggerName: 'Caffeine', phi: 0.05, strength: 'none' },
				{ triggerId: 'c', triggerName: 'Poor sleep', phi: 0.5, strength: 'strong' }
			] as ReportInput['correlations'][string]['triggers'],
			environmental: []
		}
	}
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
		expect(summary).toMatchObject({ name: 'Kopfschmerz', days: 2, logs: 3, avgIntensity: 3, avgDurationMinutes: 90 });
		expect(summary.triggers.map((t) => t.triggerName)).toEqual(['Poor sleep', 'Stress']);
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

describe('reportToPdf', () => {
	it('produces a multi-section PDF', async () => {
		const { reportToPdf } = await import('./pdf.js');
		const blob = reportToPdf(buildReport(input, format), {
			t: ((key: string) => key) as never,
			date: (d) => d,
			translate: format.translate
		});
		const bytes = new Uint8Array(await blob.arrayBuffer());
		expect(new TextDecoder().decode(bytes.subarray(0, 5))).toBe('%PDF-');
		expect(bytes.length).toBeGreaterThan(3000);
	});

	it('maps text to what the built-in font can show', async () => {
		const { latin1 } = await import('./pdf.js');
		expect(latin1('Übelkeit 10:30 → “ok” \u{1F600}')).toBe('Übelkeit 10:30 -> "ok" ?');
	});
});

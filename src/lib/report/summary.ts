import type { Entry, EntryType, EntryTypeLog, EnvironmentalData, Medication, MedicationChange } from '$lib/types/index.js';
import { indexDiary, links, overview, type Link } from '$lib/insights/engine.js';

export interface ReportInput {
	from: string;
	to: string;
	name: string | null;
	/** Should start a day before `from`, so "the day before" triggers work on the first day. */
	entries: Entry[];
	weather: EnvironmentalData[];
	entryTypes: EntryType[];
	medications: Medication[];
	changes: MedicationChange[];
}

/** Language-dependent pieces, passed in so the report can be built and tested anywhere. */
export interface ReportFormat {
	/** Translate a built-in name, option or label stored in English. */
	translate: (text: string) => string;
	/** 'HH:MM' (or the locale's form) of an ISO datetime, in local time. */
	time: (iso: string) => string;
	/** Name of a weather condition used as a trigger ('pressure-drop', 'hot-day', 'high-uv'). */
	weatherName: (key: string) => string;
}

export type NamedLink = Link & { name: string };

export interface TypeSummary {
	entryType: EntryType;
	name: string;
	days: number;
	logs: number;
	/** Days with it per 30 tracked days. */
	per30: number | null;
	avgIntensity: number | null;
	avgDurationMinutes: number | null;
	/** Triggers, foods, weather and pollen with a likely link or fewer symptoms, strongest first. */
	links: NamedLink[];
}

export interface MedicationSummary {
	medication: Medication;
	changes: MedicationChange[];
}

export interface ReportRow {
	date: string;
	type: string;
	start: string;
	end: string;
	durationMinutes: number | null;
	intensity: number | null;
	details: string;
	triggers: string;
	note: string;
}

export interface Report {
	from: string;
	to: string;
	name: string | null;
	daysWithEntries: number;
	/** Days with any record (log, trigger, note or well-being). */
	trackedDays: number;
	types: TypeSummary[];
	medications: MedicationSummary[];
	rows: ReportRow[];
}

const INTENSITY_KEYS = new Set(['intensity', 'severity']);

function durationMinutes(log: EntryTypeLog): number | null {
	if (!log.endTime) return null;
	const minutes = Math.round((Date.parse(log.endTime) - Date.parse(log.startTime)) / 60000);
	return minutes > 0 ? minutes : null;
}

function average(values: number[]): number | null {
	return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
}

/** "Location: temples; Symptoms: aura, nausea" from a log's form values, without intensity. */
function details(log: EntryTypeLog, translate: (text: string) => string): string {
	const fields = log.entryType?.metricSchema?.fields ?? [];
	// "relief" is not a form field: it is the answer to "Taken anything for it?".
	const label = (key: string) => translate(key === 'relief' ? 'Taken' : (fields.find((f) => f.key === key)?.label ?? key));
	const show = (value: unknown): string => {
		if (Array.isArray(value)) return value.map(show).filter(Boolean).join(', ');
		if (typeof value === 'string') return translate(value);
		return value == null ? '' : String(value);
	};

	return Object.entries(log.metricValues ?? {})
		.filter(([key]) => !INTENSITY_KEYS.has(key))
		.map(([key, value]) => [label(key), show(value)] as const)
		.filter(([, value]) => value !== '')
		.map(([key, value]) => `${key}: ${value}`)
		.join('; ');
}

export function buildReport(input: ReportInput, format: ReportFormat): Report {
	const { translate, time, weatherName } = format;
	const entries = input.entries
		.filter((e) => e.date >= input.from && e.date <= input.to)
		.sort((a, b) => a.date.localeCompare(b.date));
	const diary = indexDiary(input.entries);
	const triggerNames = new Map(input.entries.flatMap((e) => (e.triggers ?? []).map((tr) => [tr.triggerId, tr.triggerName] as const)));
	const named = (link: Link): NamedLink => ({
		...link,
		name: link.kind === 'weather' ? weatherName(link.key) : translate(triggerNames.get(link.key) ?? link.key)
	});

	const types: TypeSummary[] = input.entryTypes
		.map((entryType) => {
			const logs = entries.flatMap((e) => (e.logs ?? []).filter((l) => l.entryTypeId === entryType.id));
			const analysed = entryType.category === 'symptom' || entryType.category === 'event';
			return {
				entryType,
				name: translate(entryType.name),
				days: entries.filter((e) => e.logs?.some((l) => l.entryTypeId === entryType.id)).length,
				logs: logs.length,
				per30: overview(diary, entryType.id, input.from, input.to).per30,
				avgIntensity: average(logs.map((l) => l.intensityCache).filter((v): v is number => v !== null)),
				avgDurationMinutes: average(logs.map(durationMinutes).filter((v): v is number => v !== null)),
				links: analysed
					? links(diary, entryType.id, input.from, input.to, input.weather)
							.filter((l) => l.label === 'likely' || l.label === 'fewer')
							.map(named)
					: []
			};
		})
		.filter((summary) => summary.logs > 0);

	const medications: MedicationSummary[] = input.medications
		.filter((m) => m.startDate <= input.to && (!m.endDate || m.endDate >= input.from))
		.map((medication) => ({
			medication,
			changes: input.changes
				.filter((c) => c.medicationId === medication.id && c.changeDate >= input.from && c.changeDate <= input.to)
				.sort((a, b) => a.changeDate.localeCompare(b.changeDate))
		}));

	const rows: ReportRow[] = entries.flatMap((entry) => {
		const triggers = (entry.triggers ?? []).map((t) => translate(t.triggerName)).join(', ');
		const note = entry.note ?? '';
		const logs = [...(entry.logs ?? [])].sort((a, b) => a.startTime.localeCompare(b.startTime));
		if (logs.length === 0) {
			return triggers || note
				? [{ date: entry.date, type: '', start: '', end: '', durationMinutes: null, intensity: null, details: '', triggers, note }]
				: [];
		}
		return logs.map((log) => ({
			date: entry.date,
			type: translate(log.entryType?.name ?? ''),
			start: time(log.startTime),
			end: log.endTime ? time(log.endTime) : '',
			durationMinutes: durationMinutes(log),
			intensity: log.intensityCache,
			details: details(log, translate),
			triggers,
			note
		}));
	});

	return {
		from: input.from,
		to: input.to,
		name: input.name,
		daysWithEntries: entries.length,
		trackedDays: entries.filter((e) => diary.get(e.date)?.tracked).length,
		types,
		medications,
		rows
	};
}

/** 153 → "2 h 33 min", 45 → "45 min". */
export function formatDuration(minutes: number | null): string {
	if (minutes === null) return '';
	const rounded = Math.round(minutes);
	const h = Math.floor(rounded / 60);
	const m = rounded % 60;
	return h ? (m ? `${h} h ${m} min` : `${h} h`) : `${m} min`;
}

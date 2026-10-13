import type {
	CorrelationResult,
	Entry,
	EntryType,
	EntryTypeLog,
	EnvironmentalCorrelation,
	Medication,
	MedicationChange
} from '$lib/types/index.js';

export interface ReportInput {
	from: string;
	to: string;
	name: string | null;
	entries: Entry[];
	entryTypes: EntryType[];
	medications: Medication[];
	changes: MedicationChange[];
	/** Correlations per entry type id, from /api/analysis/correlations. */
	correlations: Record<string, { triggers: CorrelationResult[]; environmental: EnvironmentalCorrelation[] }>;
}

/** Language-dependent pieces, passed in so the report can be built and tested anywhere. */
export interface ReportFormat {
	/** Translate a built-in name, option or label stored in English. */
	translate: (text: string) => string;
	/** 'HH:MM' (or the locale's form) of an ISO datetime, in local time. */
	time: (iso: string) => string;
}

export interface TypeSummary {
	entryType: EntryType;
	name: string;
	days: number;
	logs: number;
	avgIntensity: number | null;
	avgDurationMinutes: number | null;
	/** Triggers with a possible or strong association, strongest first. */
	triggers: CorrelationResult[];
	environmental: EnvironmentalCorrelation[];
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
	const label = (key: string) => translate(fields.find((f) => f.key === key)?.label ?? key);
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
	const { translate, time } = format;
	const entries = input.entries
		.filter((e) => e.date >= input.from && e.date <= input.to)
		.sort((a, b) => a.date.localeCompare(b.date));

	const types: TypeSummary[] = input.entryTypes
		.map((entryType) => {
			const logs = entries.flatMap((e) => (e.logs ?? []).filter((l) => l.entryTypeId === entryType.id));
			const correlations = input.correlations[entryType.id];
			return {
				entryType,
				name: translate(entryType.name),
				days: entries.filter((e) => e.logs?.some((l) => l.entryTypeId === entryType.id)).length,
				logs: logs.length,
				avgIntensity: average(logs.map((l) => l.intensityCache).filter((v): v is number => v !== null)),
				avgDurationMinutes: average(logs.map(durationMinutes).filter((v): v is number => v !== null)),
				triggers: (correlations?.triggers ?? []).filter((c) => c.strength !== 'none').sort((a, b) => b.phi - a.phi),
				environmental: (correlations?.environmental ?? []).filter((c) => c.strength !== 'none')
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

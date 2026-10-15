/**
 * The export report as data: a summary per entry type, the links found, the
 * medications, and the diary day by day. Language-dependent bits come in
 * through ReportFormat, so it can be built and tested anywhere.
 */
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
	/** Entry types to include; all when missing or empty. */
	typeIds?: string[];
}

export interface ReportFormat {
	/** Translate a built-in name, option or label stored in English. */
	translate: (text: string) => string;
	/** A clock time of an ISO datetime, in local time. */
	time: (iso: string) => string;
	/** Name of a weather condition ('pressure-drop'...) or pollen species ('birch') used as an exposure. */
	exposureName: (kind: 'weather' | 'pollen', key: string) => string;
}

export type NamedLink = Link & { name: string };

export interface TypeSummary {
	entryType: EntryType;
	name: string;
	/** Days with this type. */
	days: number;
	/** days / recorded days, 0 to 1. */
	share: number;
	avgIntensity: number | null;
	/** Median duration of entries with an end, in minutes. */
	typicalMinutes: number | null;
	/** Days on which something was taken against it. */
	reliefDays: number;
	/** Likely links and "fewer symptoms", strongest first ("worth watching" stays in the app). */
	links: NamedLink[];
}

export interface MedicationSummary {
	medication: Medication;
	changes: MedicationChange[];
}

export interface ReportEntry {
	type: string;
	/** Shown times, in the user's clock format. */
	start: string;
	end: string;
	/** ISO datetimes, for the spreadsheet. */
	startTime: string;
	endTime: string | null;
	durationMinutes: number | null;
	intensity: number | null;
	details: string;
	/** What was taken against it. */
	taken: string;
}

export interface ReportDay {
	date: string;
	wellbeing: number | null;
	triggers: string[];
	note: string;
	/** Where the day was spent when away from home. */
	place: string | null;
	entries: ReportEntry[];
}

export interface Report {
	from: string;
	to: string;
	name: string | null;
	/** Days from `from` to `to`. */
	totalDays: number;
	/** Days with any record (log, trigger, note or well-being). */
	trackedDays: number;
	types: TypeSummary[];
	medications: MedicationSummary[];
	days: ReportDay[];
}

/** Keys shown in their own column, or not at all. */
const HIDDEN_KEYS = new Set(['intensity', 'severity', 'relief']);

function durationMinutes(log: EntryTypeLog): number | null {
	if (!log.endTime) return null;
	const minutes = Math.round((Date.parse(log.endTime) - Date.parse(log.startTime)) / 60000);
	return minutes > 0 ? minutes : null;
}

const average = (values: number[]) => (values.length ? values.reduce((a, b) => a + b, 0) / values.length : null);

function median(values: number[]): number | null {
	if (!values.length) return null;
	const sorted = [...values].sort((a, b) => a - b);
	const mid = Math.floor(sorted.length / 2);
	return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

/** "Where it hurts: temples; Symptoms: aura, nausea; Blood sugar reading: 220 mg/dL" from a log's form values. */
function details(log: EntryTypeLog, translate: (text: string) => string): string {
	const fields = log.entryType?.metricSchema?.fields ?? [];
	const values = log.metricValues ?? {};
	const label = (key: string) => translate(fields.find((f) => f.key === key)?.label ?? key);
	const show = (value: unknown): string => {
		if (Array.isArray(value)) return value.map(show).filter(Boolean).join(', ');
		if (typeof value === 'string') return translate(value);
		return value == null ? '' : String(value);
	};
	// A meal's kind is its name in the list; its foods and text are the details.
	const meal = log.entryType?.category === 'meal';
	return Object.entries(values)
		.filter(([key]) => !HIDDEN_KEYS.has(key) && !key.endsWith('_unit') && !(meal && key === 'meal'))
		.map(([key, value]) => {
			const field = fields.find((f) => f.key === key);
			// A 1 to 5 scale (a period's flow) reads like intensity: "3/5".
			const scale = field?.type === 'slider' && field.config?.min === 1 && field.config?.max === 5 && typeof value === 'number';
			const unit = scale ? '/5' : typeof values[`${key}_unit`] === 'string' ? ` ${values[`${key}_unit`]}` : '';
			return [label(key), show(value) && `${show(value)}${unit}`] as const;
		})
		.filter(([, value]) => value !== '')
		.map(([key, value]) => `${key}: ${value}`)
		.join('; ');
}

export function buildReport(input: ReportInput, format: ReportFormat): Report {
	const { translate, time, exposureName } = format;
	const include = (typeId: string) => !input.typeIds?.length || input.typeIds.includes(typeId);
	const entries = input.entries.filter((e) => e.date >= input.from && e.date <= input.to).sort((a, b) => a.date.localeCompare(b.date));
	const diary = indexDiary(input.entries);
	const triggerNames = new Map(input.entries.flatMap((e) => (e.triggers ?? []).map((tr) => [tr.triggerId, tr.triggerName] as const)));
	const named = (link: Link): NamedLink => ({
		...link,
		name:
			link.kind === 'weather' || link.kind === 'pollen'
				? exposureName(link.kind, link.key)
				: translate(link.kind === 'food' ? link.key : (triggerNames.get(link.key) ?? link.key))
	});
	const trackedDays = entries.filter((e) => diary.get(e.date)?.tracked).length;

	const types: TypeSummary[] = input.entryTypes
		.filter((type) => include(type.id) && type.category !== 'note')
		.map((entryType) => {
			const logs = entries.flatMap((e) => (e.logs ?? []).filter((l) => l.entryTypeId === entryType.id));
			const days = new Set(logs.map((l) => entries.find((e) => e.id === l.entryId)?.date)).size;
			const analysed = entryType.category === 'symptom' || entryType.category === 'event';
			return {
				entryType,
				name: translate(entryType.name),
				days,
				share: trackedDays ? days / trackedDays : 0,
				avgIntensity: average(logs.map((l) => l.intensityCache).filter((v): v is number => v !== null)),
				typicalMinutes: median(logs.map(durationMinutes).filter((v): v is number => v !== null)),
				reliefDays: analysed ? overview(diary, entryType.id, input.from, input.to).reliefDays : 0,
				links: analysed
					? links(diary, entryType.id, input.from, input.to, input.weather)
							.filter((l) => l.label === 'likely' || l.label === 'fewer')
							.map(named)
					: []
			};
		})
		.filter((summary) => summary.days > 0);

	const medications: MedicationSummary[] = input.medications
		.filter((m) => m.startDate <= input.to && (!m.endDate || m.endDate >= input.from))
		.map((medication) => ({
			medication,
			changes: input.changes
				.filter((c) => c.medicationId === medication.id && c.changeDate >= input.from && c.changeDate <= input.to)
				.sort((a, b) => a.changeDate.localeCompare(b.changeDate))
		}));

	const days: ReportDay[] = entries
		.map((entry) => ({
			date: entry.date,
			wellbeing: entry.wellbeing ?? null,
			triggers: (entry.triggers ?? []).map((tr) => translate(tr.triggerName)),
			note: entry.note?.trim() ?? '',
			place: entry.locationLat != null ? (entry.locationName ?? '') : null,
			entries: [...(entry.logs ?? [])]
				.filter((log) => include(log.entryTypeId))
				.sort((a, b) => a.startTime.localeCompare(b.startTime))
				.map((log) => ({
					type: translate((log.entryType?.category === 'meal' && (log.metricValues?.meal as string)) || log.entryType?.name || ''),
					start: time(log.startTime),
					end: log.endTime ? time(log.endTime) : '',
					startTime: log.startTime,
					endTime: log.endTime,
					durationMinutes: durationMinutes(log),
					intensity: log.intensityCache,
					details: details(log, translate),
					taken: Array.isArray(log.metricValues?.relief) ? (log.metricValues.relief as string[]).map(translate).join(', ') : ''
				}))
		}))
		// A report for some types lists only their days; a full report every day with anything in it.
		.filter((day) => (input.typeIds?.length ? day.entries.length > 0 : day.entries.length || day.triggers.length || day.note || day.wellbeing != null));

	const totalDays = Math.round((Date.parse(input.to) - Date.parse(input.from)) / 86_400_000) + 1;
	return { from: input.from, to: input.to, name: input.name, totalDays, trackedDays, types, medications, days };
}

/** 153 → "2 h 33 min", 45 → "45 min". */
export function formatDuration(minutes: number | null): string {
	if (minutes === null) return '';
	const rounded = Math.round(minutes);
	const h = Math.floor(rounded / 60);
	const m = rounded % 60;
	return h ? (m ? `${h} h ${m} min` : `${h} h`) : `${m} min`;
}

/**
 * The statistics behind Insights. Everything here is pure and works on the
 * diary as loaded from the API, so it runs the same in the browser and on the
 * phone.
 *
 * - A tracked day has any record: a log, a trigger, a note or a well-being rating.
 * - A symptom day is a tracked day with a log of the chosen entry type.
 * Untracked days are left out everywhere: no entry does not mean no symptom.
 */
import type { Entry, EntryType, EntryTypeLog, EnvironmentalData, Medication, MedicationChange } from '$lib/types';
import { addDays, daysBetween, eachDay, weekStart, weekdayIndex } from './dates.js';
import { fisherOneSided } from './fisher.js';

export interface DayRecord {
	date: string;
	tracked: boolean;
	logs: EntryTypeLog[];
	/** Entry types logged on the day. */
	types: Set<string>;
	triggers: Set<string>;
	wellbeing: number | null;
}

export type Diary = Map<string, DayRecord>;

export function indexDiary(entries: Entry[]): Diary {
	const diary: Diary = new Map();
	for (const entry of entries) {
		const logs = entry.logs ?? [];
		const triggers = new Set((entry.triggers ?? []).map((tr) => tr.triggerId));
		diary.set(entry.date, {
			date: entry.date,
			tracked: logs.length > 0 || triggers.size > 0 || !!entry.note?.trim() || entry.wellbeing != null,
			logs,
			types: new Set(logs.map((log) => log.entryTypeId)),
			triggers,
			wellbeing: entry.wellbeing ?? null
		});
	}
	return diary;
}

export function trackedDays(diary: Diary, from: string, to: string): string[] {
	return eachDay(from, to).filter((date) => diary.get(date)?.tracked);
}

const mean = (values: number[]) => (values.length ? values.reduce((a, b) => a + b, 0) / values.length : null);

function logsOf(diary: Diary, days: string[], typeId: string): EntryTypeLog[] {
	return days.flatMap((date) => diary.get(date)!.logs.filter((log) => log.entryTypeId === typeId));
}

function avgIntensity(logs: EntryTypeLog[]): number | null {
	return mean(logs.map((log) => log.intensityCache).filter((v): v is number => v != null));
}

// ─── How am I doing ───────────────────────────────────────────────────────────

export interface PeriodCounts {
	trackedDays: number;
	symptomDays: number;
	/** Symptom days per 30 tracked days; null without tracked days. */
	per30: number | null;
}

export interface Overview extends PeriodCounts {
	avgIntensity: number | null;
	/** Hours with the symptom, from logs that have an end. */
	hours: number;
	previous: PeriodCounts;
	/** Compared with the period of the same length just before; null with too little data. */
	trend: 'better' | 'worse' | 'same' | null;
}

/** Below this, rates and comparisons are not shown as findings. */
export const MIN_TRACKED_DAYS = 10;

function counts(diary: Diary, typeId: string, from: string, to: string): PeriodCounts {
	const days = trackedDays(diary, from, to);
	const symptomDays = days.filter((date) => diary.get(date)!.types.has(typeId)).length;
	return { trackedDays: days.length, symptomDays, per30: days.length ? (symptomDays / days.length) * 30 : null };
}

export function overview(diary: Diary, typeId: string, from: string, to: string): Overview {
	const current = counts(diary, typeId, from, to);
	const length = daysBetween(from, to) + 1;
	const previous = counts(diary, typeId, addDays(from, -length), addDays(from, -1));
	const logs = logsOf(diary, trackedDays(diary, from, to), typeId);
	const hours = logs.reduce((sum, log) => {
		if (!log.endTime) return sum;
		const h = (Date.parse(log.endTime) - Date.parse(log.startTime)) / 3_600_000;
		return h > 0 ? sum + h : sum;
	}, 0);

	let trend: Overview['trend'] = null;
	if (current.trackedDays >= MIN_TRACKED_DAYS && previous.trackedDays >= MIN_TRACKED_DAYS) {
		const now = current.per30!;
		const before = previous.per30!;
		// At least a fifth fewer (or more) days and one day per month apart.
		if (now <= before * 0.8 && before - now >= 1) trend = 'better';
		else if (now >= before * 1.25 && now - before >= 1) trend = 'worse';
		else trend = 'same';
	}
	return { ...current, avgIntensity: avgIntensity(logs), hours, previous, trend };
}

export interface Week {
	start: string;
	trackedDays: number;
	symptomDays: number;
	avgIntensity: number | null;
	avgWellbeing: number | null;
}

export function weeks(diary: Diary, typeId: string, from: string, to: string): Week[] {
	const result: Week[] = [];
	for (let start = weekStart(from); start <= to; start = addDays(start, 7)) {
		const end = addDays(start, 6);
		const days = trackedDays(diary, start < from ? from : start, end > to ? to : end);
		const records = days.map((date) => diary.get(date)!);
		result.push({
			start,
			trackedDays: days.length,
			symptomDays: records.filter((r) => r.types.has(typeId)).length,
			avgIntensity: avgIntensity(logsOf(diary, days, typeId)),
			avgWellbeing: mean(records.map((r) => r.wellbeing).filter((v): v is number => v != null))
		});
	}
	return result;
}

/** Per day: null when not tracked, 0 when tracked without the symptom, else the highest intensity (3 when not rated). */
export function dailyLevels(diary: Diary, typeId: string, from: string, to: string): Array<{ date: string; level: number | null }> {
	return eachDay(from, to).map((date) => {
		const day = diary.get(date);
		if (!day?.tracked) return { date, level: null };
		const logs = day.logs.filter((log) => log.entryTypeId === typeId);
		if (!logs.length) return { date, level: 0 };
		return { date, level: Math.max(...logs.map((log) => log.intensityCache ?? 3)) };
	});
}

// ─── What goes along with it ──────────────────────────────────────────────────

export type LinkLabel = 'likely' | 'possible' | 'fewer' | 'none' | 'not-enough';

export interface Rates {
	/** Days with the exposure / without it, among tracked days. */
	exposedDays: number;
	unexposedDays: number;
	/** Share of those days with the symptom, 0 to 1. */
	withRate: number;
	withoutRate: number;
	/** withRate / withoutRate (Infinity when the symptom never came without it). */
	ratio: number;
	/** One-sided Fisher p-value in the direction of the label. */
	p: number;
	label: LinkLabel;
}

/** Fewer than this many days on either side give "not enough data yet". */
export const MIN_EXPOSED_DAYS = 5;

/** Turns a 2×2 count into rates and a label. */
export function compare(a: number, b: number, c: number, d: number): Rates {
	const exposedDays = a + b;
	const unexposedDays = c + d;
	const withRate = exposedDays ? a / exposedDays : 0;
	const withoutRate = unexposedDays ? c / unexposedDays : 0;
	const ratio = withoutRate ? withRate / withoutRate : withRate ? Infinity : 1;
	const base = { exposedDays, unexposedDays, withRate, withoutRate, ratio };
	if (exposedDays < MIN_EXPOSED_DAYS || unexposedDays < MIN_EXPOSED_DAYS) return { ...base, p: 1, label: 'not-enough' };

	const { greater, less } = fisherOneSided(a, b, c, d);
	if (greater < 0.05 && ratio >= 1.5) return { ...base, p: greater, label: 'likely' };
	if (greater < 0.2 && ratio >= 1.25) return { ...base, p: greater, label: 'possible' };
	if (less < 0.05 && ratio <= 0.67) return { ...base, p: less, label: 'fewer' };
	return { ...base, p: Math.min(greater, less), label: 'none' };
}

/**
 * Rates for one exposure. `exposure` answers for a date: true, false, or null
 * when unknown (that day is then left out). With `lag` 1 the exposure is read
 * from the day before each tracked day.
 */
export function exposureRates(
	diary: Diary,
	typeId: string,
	from: string,
	to: string,
	exposure: (date: string) => boolean | null,
	lag: 0 | 1
): Rates {
	let a = 0, b = 0, c = 0, d = 0;
	for (const date of trackedDays(diary, from, to)) {
		const exposed = exposure(lag ? addDays(date, -1) : date);
		if (exposed === null) continue;
		const symptom = diary.get(date)!.types.has(typeId);
		if (exposed) symptom ? a++ : b++;
		else symptom ? c++ : d++;
	}
	return compare(a, b, c, d);
}

export type WeatherKey = 'pressure-drop' | 'hot-day' | 'high-uv';

export const WEATHER_EXPOSURES: Record<WeatherKey, (w: EnvironmentalData) => boolean | null> = {
	'pressure-drop': (w) => (w.pressureDeltaHpa == null ? null : w.pressureDeltaHpa <= -5),
	'hot-day': (w) => (w.temperatureC == null ? null : w.temperatureC >= 28),
	'high-uv': (w) => (w.uvIndex == null ? null : w.uvIndex >= 6)
};

export interface Link extends Rates {
	/** Trigger id, or the weather key. */
	key: string;
	kind: 'trigger' | 'weather';
	/** 0: the same day, 1: the day before. */
	lag: 0 | 1;
}

const LABEL_RANK: Record<LinkLabel, number> = { likely: 4, possible: 3, fewer: 2, none: 1, 'not-enough': 0 };

function better(x: Link, y: Link): Link {
	if (LABEL_RANK[x.label] !== LABEL_RANK[y.label]) return LABEL_RANK[x.label] > LABEL_RANK[y.label] ? x : y;
	return y.p < x.p ? y : x;
}

/**
 * Every trigger used in the period and the weather conditions, each with the
 * stronger of "same day" and "day before", strongest links first.
 */
export function links(diary: Diary, typeId: string, from: string, to: string, weather: EnvironmentalData[]): Link[] {
	const used = new Set<string>();
	for (const date of eachDay(addDays(from, -1), to)) diary.get(date)?.triggers.forEach((id) => used.add(id));

	const result: Link[] = [];
	const best = (key: string, kind: Link['kind'], exposure: (date: string) => boolean | null) => {
		const [same, before] = ([0, 1] as const).map((lag) => ({ key, kind, lag, ...exposureRates(diary, typeId, from, to, exposure, lag) }));
		result.push(better(same, before));
	};

	for (const id of used) {
		// A trigger counts as "not there" only on tracked days.
		best(id, 'trigger', (date) => (diary.get(date)?.tracked ? diary.get(date)!.triggers.has(id) : null));
	}

	const byDate = new Map(weather.map((w) => [w.date, w]));
	for (const [key, test] of Object.entries(WEATHER_EXPOSURES)) {
		best(key, 'weather', (date) => {
			const w = byDate.get(date);
			return w ? test(w) : null;
		});
	}

	return result
		.filter((link) => link.exposedDays > 0)
		.sort((x, y) => LABEL_RANK[y.label] - LABEL_RANK[x.label] || (y.ratio === x.ratio ? x.p - y.p : y.ratio - x.ratio));
}

// ─── When does it happen ──────────────────────────────────────────────────────

export interface Share {
	trackedDays: number;
	symptomDays: number;
	rate: number;
}

/** Monday first. */
export function byWeekday(diary: Diary, typeId: string, from: string, to: string): Share[] {
	const result = Array.from({ length: 7 }, () => ({ trackedDays: 0, symptomDays: 0, rate: 0 }));
	for (const date of trackedDays(diary, from, to)) {
		const slot = result[weekdayIndex(date)];
		slot.trackedDays++;
		if (diary.get(date)!.types.has(typeId)) slot.symptomDays++;
	}
	for (const slot of result) slot.rate = slot.trackedDays ? slot.symptomDays / slot.trackedDays : 0;
	return result;
}

export type DayPart = 'night' | 'morning' | 'afternoon' | 'evening';

/** How many logs started at night (0-6 h), in the morning (6-12), afternoon (12-18) and evening (18-24), local time. */
export function byTimeOfDay(diary: Diary, typeId: string, from: string, to: string): Record<DayPart, number> {
	const result: Record<DayPart, number> = { night: 0, morning: 0, afternoon: 0, evening: 0 };
	for (const log of logsOf(diary, trackedDays(diary, from, to), typeId)) {
		const hour = new Date(log.startTime).getHours();
		result[hour < 6 ? 'night' : hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening']++;
	}
	return result;
}

export type CyclePhase = 'before' | 'early' | 'late' | 'rest';

export interface CycleResult {
	/** Period starts found around the period. */
	cycles: number;
	phases: Array<{ phase: CyclePhase } & Share & { label: LinkLabel }>;
}

/**
 * Symptom rate by cycle phase: the 3 days before a period, period days 1 to 3,
 * later period days, and the rest. Needs at least two period starts.
 */
export function byCyclePhase(diary: Diary, typeId: string, periodTypeId: string, from: string, to: string): CycleResult | null {
	if (typeId === periodTypeId) return null;
	const hasPeriod = (date: string) => !!diary.get(date)?.types.has(periodTypeId);
	const starts = eachDay(addDays(from, -45), addDays(to, 3)).filter((date) => hasPeriod(date) && !hasPeriod(addDays(date, -1)));
	if (starts.length < 2) return null;

	const phaseOf = (date: string): CyclePhase => {
		const next = starts.find((s) => s > date);
		if (next && daysBetween(date, next) <= 3) return 'before';
		const last = starts.filter((s) => s <= date).at(-1);
		if (last !== undefined) {
			const offset = daysBetween(last, date);
			if (offset <= 2) return 'early';
			if (hasPeriod(date)) return 'late';
		}
		return 'rest';
	};

	const days = trackedDays(diary, from, to).map((date) => ({ phase: phaseOf(date), symptom: diary.get(date)!.types.has(typeId) }));
	const phases = (['before', 'early', 'late', 'rest'] as const).map((phase) => {
		const inside = days.filter((d) => d.phase === phase);
		const outside = days.filter((d) => d.phase !== phase);
		const a = inside.filter((d) => d.symptom).length;
		const c = outside.filter((d) => d.symptom).length;
		return {
			phase,
			trackedDays: inside.length,
			symptomDays: a,
			rate: inside.length ? a / inside.length : 0,
			label: phase === 'rest' ? ('none' as const) : compare(a, inside.length - a, c, outside.length - c).label
		};
	});
	return { cycles: starts.length, phases };
}

// ─── Do my medications help ───────────────────────────────────────────────────

export interface MedicationEvent {
	medicationId: string;
	name: string;
	date: string;
	kind: 'started' | 'dosage_change';
	oldDosage: string | null;
	newDosage: string | null;
}

/** Starts and dose changes of every medication, newest first. */
export function medicationEvents(medications: Medication[], changes: MedicationChange[]): MedicationEvent[] {
	const events: MedicationEvent[] = [];
	const dose = (m: Medication) => (m.dosage ? `${m.dosage}${m.unit ? ` ${m.unit}` : ''}` : null);
	for (const med of medications) {
		const own = changes.filter((c) => c.medicationId === med.id).sort((a, b) => a.changeDate.localeCompare(b.changeDate));
		// The first dose change tells which dose the medication was started with.
		const firstChange = own.find((c) => c.changeType === 'dosage_change');
		events.push({ medicationId: med.id, name: med.name, date: med.startDate, kind: 'started', oldDosage: null, newDosage: firstChange?.oldDosage ?? dose(med) });
		for (const change of own) {
			if (change.changeType === 'stopped') continue;
			if (change.changeType === 'started' && change.changeDate === med.startDate) continue;
			events.push({ medicationId: med.id, name: med.name, date: change.changeDate, kind: change.changeType, oldDosage: change.oldDosage, newDosage: change.newDosage });
		}
	}
	return events.sort((a, b) => b.date.localeCompare(a.date));
}

export interface MedicationEffect {
	event: MedicationEvent;
	before: PeriodCounts & { avgIntensity: number | null };
	after: PeriodCounts & { avgIntensity: number | null };
	/** null when either side has fewer than MIN_TRACKED_DAYS tracked days. */
	result: 'fewer' | 'more' | 'similar' | null;
}

export const MEDICATION_WINDOW = 30;

/** The 30 days before each event against the 30 days from it on (up to `today`). */
export function medicationEffects(diary: Diary, typeId: string, events: MedicationEvent[], from: string, to: string, today: string): MedicationEffect[] {
	return events
		.filter((event) => event.date >= from && event.date <= to)
		.map((event) => {
			const beforeFrom = addDays(event.date, -MEDICATION_WINDOW);
			const beforeTo = addDays(event.date, -1);
			const afterTo = [addDays(event.date, MEDICATION_WINDOW - 1), today].sort()[0];
			const side = (start: string, end: string) => ({
				...counts(diary, typeId, start, end),
				avgIntensity: avgIntensity(logsOf(diary, trackedDays(diary, start, end), typeId))
			});
			const before = side(beforeFrom, beforeTo);
			const after = side(event.date, afterTo);

			let result: MedicationEffect['result'] = null;
			if (before.trackedDays >= MIN_TRACKED_DAYS && after.trackedDays >= MIN_TRACKED_DAYS) {
				const { greater, less } = fisherOneSided(
					after.symptomDays,
					after.trackedDays - after.symptomDays,
					before.symptomDays,
					before.trackedDays - before.symptomDays
				);
				result = less < 0.1 ? 'fewer' : greater < 0.1 ? 'more' : 'similar';
			}
			return { event, before, after, result };
		});
}

// ─── Which types to offer ─────────────────────────────────────────────────────

/** Entry types worth analysing (symptoms and events with logs in the period), most logged first. */
export function analysableTypes(diary: Diary, entryTypes: EntryType[], from: string, to: string): Array<{ type: EntryType; days: number }> {
	const days = trackedDays(diary, from, to);
	return entryTypes
		.filter((type) => type.category === 'symptom' || type.category === 'event')
		.map((type) => ({ type, days: days.filter((date) => diary.get(date)!.types.has(type.id)).length }))
		.filter((item) => item.days > 0)
		.sort((a, b) => b.days - a.days);
}

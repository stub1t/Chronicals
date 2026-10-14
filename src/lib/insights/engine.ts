/**
 * The statistics behind the analysis. Everything here is pure and works on
 * the diary as loaded from the API, so it runs the same in the browser and on
 * the phone.
 *
 * - A tracked day has any record: a log, a trigger, a note or a well-being rating.
 * - A symptom day is a tracked day with a log of the chosen entry type.
 * Untracked days are left out everywhere: no entry does not mean no symptom.
 */
import type { Entry, EntryType, EntryTypeLog, EnvironmentalData, Medication, MedicationChange } from '$lib/types';
import { POLLEN, POLLEN_FIELD, pollenLevel } from '$lib/weather/pollen.js';
import { addDays, daysBetween, eachDay, weekStart, weekdayIndex } from './dates.js';
import { fisherOneSided } from './fisher.js';

export interface DayRecord {
	date: string;
	tracked: boolean;
	logs: EntryTypeLog[];
	/** Entry types logged on the day. */
	types: Set<string>;
	triggers: Set<string>;
	/** Foods from the day's meals. */
	foods: Set<string>;
	wellbeing: number | null;
}

export type Diary = Map<string, DayRecord>;

export function indexDiary(entries: Entry[]): Diary {
	const diary: Diary = new Map();
	for (const entry of entries) {
		const logs = entry.logs ?? [];
		const triggers = new Set((entry.triggers ?? []).map((tr) => tr.triggerId));
		const foods = new Set(
			logs
				.filter((log) => log.entryType?.category === 'meal' && Array.isArray(log.metricValues?.foods))
				.flatMap((log) => log.metricValues.foods as string[])
		);
		diary.set(entry.date, {
			date: entry.date,
			tracked: logs.length > 0 || triggers.size > 0 || !!entry.note?.trim() || entry.wellbeing != null,
			logs,
			types: new Set(logs.map((log) => log.entryTypeId)),
			triggers,
			foods,
			wellbeing: entry.wellbeing ?? null
		});
	}
	return diary;
}

export function trackedDays(diary: Diary, from: string, to: string): string[] {
	return eachDay(from, to).filter((date) => diary.get(date)?.tracked);
}

/** The first tracked day, for "all time". */
export function firstTrackedDay(diary: Diary): string | null {
	return [...diary.values()].filter((d) => d.tracked).map((d) => d.date).sort()[0] ?? null;
}

const mean = (values: number[]) => (values.length ? values.reduce((a, b) => a + b, 0) / values.length : null);

function logsOf(diary: Diary, days: string[], typeId: string): EntryTypeLog[] {
	return days.flatMap((date) => diary.get(date)!.logs.filter((log) => log.entryTypeId === typeId));
}

function avgIntensity(logs: EntryTypeLog[]): number | null {
	return mean(logs.map((log) => log.intensityCache).filter((v): v is number => v != null));
}

const tookSomething = (log: EntryTypeLog) => Array.isArray(log.metricValues?.relief) && (log.metricValues.relief as unknown[]).length > 0;

// ─── How am I doing ───────────────────────────────────────────────────────────

export interface PeriodCounts {
	from: string;
	to: string;
	trackedDays: number;
	symptomDays: number;
	/** Symptom days per 30 tracked days; null without tracked days. */
	per30: number | null;
}

export interface Overview extends PeriodCounts {
	avgIntensity: number | null;
	/** Symptom days on which something was taken against it. */
	reliefDays: number;
	/** The period of the same length just before. */
	previous: PeriodCounts;
	/** null with too little data on either side. */
	trend: 'better' | 'worse' | 'same' | null;
}

/** Below this on either side there is no trend. */
export const MIN_TREND_DAYS = 14;

function counts(diary: Diary, typeId: string, from: string, to: string): PeriodCounts {
	const days = trackedDays(diary, from, to);
	const symptomDays = days.filter((date) => diary.get(date)!.types.has(typeId)).length;
	return { from, to, trackedDays: days.length, symptomDays, per30: days.length ? (symptomDays / days.length) * 30 : null };
}

export function overview(diary: Diary, typeId: string, from: string, to: string): Overview {
	const current = counts(diary, typeId, from, to);
	const length = daysBetween(from, to) + 1;
	const previous = counts(diary, typeId, addDays(from, -length), addDays(from, -1));
	const days = trackedDays(diary, from, to);
	const logs = logsOf(diary, days, typeId);
	const reliefDays = days.filter((date) => diary.get(date)!.logs.some((log) => log.entryTypeId === typeId && tookSomething(log))).length;

	let trend: Overview['trend'] = null;
	if (current.trackedDays >= MIN_TREND_DAYS && previous.trackedDays >= MIN_TREND_DAYS) {
		const now = current.per30!;
		const before = previous.per30!;
		const { greater, less } = fisherOneSided(
			current.symptomDays,
			current.trackedDays - current.symptomDays,
			previous.symptomDays,
			previous.trackedDays - previous.symptomDays
		);
		// A fifth fewer (or more), at least one day a month apart, and unlikely to be chance.
		if (now <= before * 0.8 && before - now >= 1 && less < 0.2) trend = 'better';
		else if (now >= before * 1.25 && now - before >= 1 && greater < 0.2) trend = 'worse';
		else trend = 'same';
	}
	return { ...current, avgIntensity: avgIntensity(logs), reliefDays, previous, trend };
}

export interface Bucket {
	/** First day of the week or month. */
	start: string;
	trackedDays: number;
	symptomDays: number;
	/** Share of tracked days with the symptom, 0 to 1; null without tracked days. */
	rate: number | null;
	avgWellbeing: number | null;
}

function bucket(diary: Diary, typeId: string, start: string, from: string, to: string): Bucket {
	const days = trackedDays(diary, from, to);
	const records = days.map((date) => diary.get(date)!);
	const symptomDays = records.filter((r) => r.types.has(typeId)).length;
	return {
		start,
		trackedDays: days.length,
		symptomDays,
		rate: days.length ? symptomDays / days.length : null,
		avgWellbeing: mean(records.map((r) => r.wellbeing).filter((v): v is number => v != null))
	};
}

/** One bucket per week (Monday start), clipped to the period. */
export function weeks(diary: Diary, typeId: string, from: string, to: string): Bucket[] {
	const result: Bucket[] = [];
	for (let start = weekStart(from); start <= to; start = addDays(start, 7)) {
		const end = addDays(start, 6);
		result.push(bucket(diary, typeId, start, start < from ? from : start, end > to ? to : end));
	}
	return result;
}

/** One bucket per calendar month, clipped to the period. */
export function months(diary: Diary, typeId: string, from: string, to: string): Bucket[] {
	const result: Bucket[] = [];
	for (let start = `${from.slice(0, 7)}-01`; start <= to; ) {
		const next = new Date(Date.UTC(+start.slice(0, 4), +start.slice(5, 7), 1)).toISOString().slice(0, 10);
		const end = addDays(next, -1);
		result.push(bucket(diary, typeId, start, start < from ? from : start, end > to ? to : end));
		start = next;
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

/** 'watch': worth keeping an eye on, not strong enough to call a link. */
export type LinkLabel = 'likely' | 'watch' | 'fewer' | 'none' | 'not-enough';

export interface Rates {
	/** Tracked days with the exposure / without it. */
	exposedDays: number;
	unexposedDays: number;
	/** Days with both the exposure and the symptom. */
	exposedSymptomDays: number;
	/** Share of those days with the symptom, 0 to 1. */
	withRate: number;
	withoutRate: number;
	/** withRate / withoutRate (Infinity when the symptom never came without it). */
	ratio: number;
}

/** Fewer than this many days on either side give "not enough days yet". */
export const MIN_EXPOSED_DAYS = 5;

function rates(a: number, b: number, c: number, d: number): Rates & { greater: number; less: number } {
	const exposedDays = a + b;
	const unexposedDays = c + d;
	const withRate = exposedDays ? a / exposedDays : 0;
	const withoutRate = unexposedDays ? c / unexposedDays : 0;
	const ratio = withoutRate ? withRate / withoutRate : withRate ? Infinity : 1;
	const { greater, less } = fisherOneSided(a, b, c, d);
	return { exposedDays, unexposedDays, exposedSymptomDays: a, withRate, withoutRate, ratio, greater, less };
}

/**
 * Rates for one exposure. `exposure` answers for a date: true, false, or null
 * when unknown (that day is then left out). With `lag` 1 the exposure is read
 * from the day before each tracked day.
 */
function exposureRates(diary: Diary, typeId: string, from: string, to: string, exposure: (date: string) => boolean | null, lag: 0 | 1) {
	let a = 0, b = 0, c = 0, d = 0;
	for (const date of trackedDays(diary, from, to)) {
		const exposed = exposure(lag ? addDays(date, -1) : date);
		if (exposed === null) continue;
		const symptom = diary.get(date)!.types.has(typeId);
		if (exposed) symptom ? a++ : b++;
		else symptom ? c++ : d++;
	}
	return rates(a, b, c, d);
}

export type WeatherKey = 'pressure-drop' | 'hot-day' | 'high-uv';

export const WEATHER_EXPOSURES: Record<WeatherKey, (w: EnvironmentalData) => boolean | null> = {
	'pressure-drop': (w) => (w.pressureDeltaHpa == null ? null : w.pressureDeltaHpa <= -5),
	'hot-day': (w) => (w.temperatureC == null ? null : w.temperatureC >= 28),
	'high-uv': (w) => (w.uvIndex == null ? null : w.uvIndex >= 6)
};

export type LinkKind = 'trigger' | 'weather' | 'pollen' | 'food';

export interface Link extends Rates {
	/** Trigger id, weather key ('pressure-drop'), pollen species ('birch') or food ('pasta'). */
	key: string;
	kind: LinkKind;
	/** 0: the same day, 1: the day before. */
	lag: 0 | 1;
	/** One-sided p-value in the direction of the effect, doubled for picking the better of two days. */
	p: number;
	label: LinkLabel;
}

/** Benjamini-Hochberg: which of the p-values hold at false discovery rate q. */
export function benjaminiHochberg(ps: number[], q = 0.1): boolean[] {
	const order = ps.map((p, i) => ({ p, i })).sort((x, y) => x.p - y.p);
	let cutoff = -1;
	order.forEach(({ p }, rank) => {
		if (p <= ((rank + 1) / order.length) * q) cutoff = rank;
	});
	const pass = ps.map(() => false);
	order.forEach(({ i }, rank) => (pass[i] = rank <= cutoff));
	return pass;
}

const LABEL_ORDER: Record<LinkLabel, number> = { likely: 0, fewer: 1, watch: 2, none: 3, 'not-enough': 4 };
/** Weather and pollen come in seasonal streaks, which makes chance links more likely; they need stronger evidence. */
const SEASONAL: ReadonlySet<LinkKind> = new Set(['weather', 'pollen']);

/**
 * Every trigger used in the period, foods eaten on at least 5 days, the
 * weather and pollen, each with the stronger of "same day" and "day before".
 * Clear links first.
 */
export function links(diary: Diary, typeId: string, from: string, to: string, weather: EnvironmentalData[]): Link[] {
	const known = (date: string) => !!diary.get(date)?.tracked;
	const sources: Array<{ key: string; kind: LinkKind; exposure: (date: string) => boolean | null }> = [];

	const window = eachDay(addDays(from, -1), to);
	const usedTriggers = new Set<string>();
	const foodDays = new Map<string, number>();
	for (const date of window) {
		const day = diary.get(date);
		day?.triggers.forEach((id) => usedTriggers.add(id));
		day?.foods.forEach((food) => foodDays.set(food, (foodDays.get(food) ?? 0) + 1));
	}
	for (const id of usedTriggers) sources.push({ key: id, kind: 'trigger', exposure: (date) => (known(date) ? diary.get(date)!.triggers.has(id) : null) });
	for (const [food, n] of foodDays) {
		if (n >= MIN_EXPOSED_DAYS) sources.push({ key: food, kind: 'food', exposure: (date) => (known(date) ? diary.get(date)!.foods.has(food) : null) });
	}

	const byDate = new Map(weather.map((w) => [w.date, w]));
	for (const [key, test] of Object.entries(WEATHER_EXPOSURES)) {
		sources.push({ key, kind: 'weather', exposure: (date) => (byDate.has(date) ? test(byDate.get(date)!) : null) });
	}
	for (const species of POLLEN) {
		const high = (w: EnvironmentalData | undefined) => {
			const level = pollenLevel(species, w?.[POLLEN_FIELD[species]]);
			return level === null ? null : level === 'high';
		};
		// Only species that reached "high" at all are worth testing.
		if (!weather.some((w) => high(w))) continue;
		sources.push({ key: species, kind: 'pollen', exposure: (date) => high(byDate.get(date)) });
	}

	const candidates = sources.map(({ key, kind, exposure }) => {
		const [same, before] = ([0, 1] as const).map((lag) => ({ lag, ...exposureRates(diary, typeId, from, to, exposure, lag) }));
		const best = Math.min(same.greater, same.less) <= Math.min(before.greater, before.less) ? same : before;
		const up = best.ratio >= 1;
		const p = Math.min(1, 2 * (up ? best.greater : best.less));
		const enough = best.exposedDays >= MIN_EXPOSED_DAYS && best.unexposedDays >= MIN_EXPOSED_DAYS;
		const { lag, exposedDays, unexposedDays, exposedSymptomDays, withRate, withoutRate, ratio } = best;
		return { key, kind, up, enough, lag, exposedDays, unexposedDays, exposedSymptomDays, withRate, withoutRate, ratio, p };
	});

	// Benjamini-Hochberg over everything tested for this type.
	const testable = candidates.filter((c) => c.enough);
	const passed = new Set(benjaminiHochberg(testable.map((c) => c.p)).flatMap((ok, i) => (ok ? [testable[i]] : [])));

	const result: Link[] = candidates.map((candidate) => {
		const { up, enough, ...c } = candidate;
		const significant = passed.has(candidate);
		let label: LinkLabel = 'none';
		if (!enough) label = 'not-enough';
		else if (up && significant && c.p < (SEASONAL.has(c.kind) ? 0.01 : 0.05) && c.ratio >= 1.5 && c.exposedSymptomDays >= 3) label = 'likely';
		else if (!up && significant && c.p < 0.05 && c.ratio <= 0.67) label = 'fewer';
		else if (up && c.p < 0.2 && c.ratio >= 1.25 && c.exposedSymptomDays >= 2) label = 'watch';
		return { ...c, label };
	});

	return result
		.filter((link) => link.exposedDays > 0)
		.sort((x, y) => {
			if (x.label !== y.label) return LABEL_ORDER[x.label] - LABEL_ORDER[y.label];
			// Strongest first: highest ratio for links, lowest for "fewer".
			if (x.label === 'fewer') return x.ratio - y.ratio;
			return y.ratio === x.ratio ? x.p - y.p : y.ratio - x.ratio;
		});
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

/** The weekday that stands out, if one clearly does (one-sided Fisher, corrected for picking 1 of 7). */
export function weekdayPattern(shares: Share[]): { weekday: number; rate: number; otherRate: number } | null {
	const weekday = shares.reduce((best, s, i) => (s.rate > shares[best].rate ? i : best), 0);
	const s = shares[weekday];
	const other = shares.filter((_, i) => i !== weekday).reduce((sum, o) => ({ tracked: sum.tracked + o.trackedDays, symptom: sum.symptom + o.symptomDays }), { tracked: 0, symptom: 0 });
	if (s.trackedDays < MIN_EXPOSED_DAYS || other.tracked < MIN_EXPOSED_DAYS) return null;
	const { greater } = fisherOneSided(s.symptomDays, s.trackedDays - s.symptomDays, other.symptom, other.tracked - other.symptom);
	const otherRate = other.symptom / other.tracked;
	return greater * 7 < 0.05 && s.rate >= otherRate * 1.5 ? { weekday, rate: s.rate, otherRate } : null;
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

/** The part of the day when most logs start, if it is at least half of 5 or more. */
export function timePattern(parts: Record<DayPart, number>): { part: DayPart; count: number; total: number } | null {
	const total = Object.values(parts).reduce((a, b) => a + b, 0);
	const [part, count] = (Object.entries(parts) as Array<[DayPart, number]>).sort((a, b) => b[1] - a[1])[0];
	return total >= 5 && count / total >= 0.5 ? { part, count, total } : null;
}

export type CyclePhase = 'before' | 'early' | 'late' | 'rest';

export interface CycleResult {
	/** Period starts inside the period. */
	cycles: number;
	phases: Array<{ phase: CyclePhase } & Share & { label: LinkLabel }>;
}

/**
 * Symptom rate by cycle phase: the 3 days before a period, period days 1 to 3,
 * later period days, and the rest. A start needs 10 period-free days before it,
 * so a gap in logging does not create a second period. Needs two starts in the period.
 */
export function byCyclePhase(diary: Diary, typeId: string, periodTypeId: string, from: string, to: string): CycleResult | null {
	if (typeId === periodTypeId) return null;
	const hasPeriod = (date: string) => !!diary.get(date)?.types.has(periodTypeId);
	const starts = eachDay(addDays(from, -45), addDays(to, 3)).filter(
		(date) => hasPeriod(date) && !Array.from({ length: 10 }, (_, i) => addDays(date, -1 - i)).some(hasPeriod)
	);
	const cycles = starts.filter((s) => s >= from && s <= to).length;
	if (cycles < 2) return null;

	const phaseOf = (date: string): CyclePhase | null => {
		const next = starts.find((s) => s > date);
		if (next && daysBetween(date, next) <= 3) return 'before';
		const last = starts.filter((s) => s <= date).at(-1);
		if (last !== undefined) {
			const offset = daysBetween(last, date);
			if (offset <= 2) return 'early';
			if (hasPeriod(date)) return 'late';
		}
		// The last days of the period may be "before" a period that is not logged yet.
		if (!next && daysBetween(date, to) < 3) return null;
		return 'rest';
	};

	const days = trackedDays(diary, from, to)
		.map((date) => ({ phase: phaseOf(date), symptom: diary.get(date)!.types.has(typeId) }))
		.filter((d): d is { phase: CyclePhase; symptom: boolean } => d.phase !== null);
	const phases = (['before', 'early', 'late', 'rest'] as const).map((phase) => {
		const inside = days.filter((d) => d.phase === phase);
		const outside = days.filter((d) => d.phase !== phase);
		const a = inside.filter((d) => d.symptom).length;
		const c = outside.filter((d) => d.symptom).length;
		const r = rates(a, inside.length - a, c, outside.length - c);
		const enough = inside.length >= MIN_EXPOSED_DAYS && outside.length >= MIN_EXPOSED_DAYS;
		// Three phases are tested, so the bar is p < 0.05 / 3.
		const label: LinkLabel = phase === 'rest' ? 'none' : !enough ? 'not-enough' : r.greater * 3 < 0.05 && r.ratio >= 1.5 ? 'likely' : r.greater < 0.2 && r.ratio >= 1.25 ? 'watch' : 'none';
		return { phase, trackedDays: inside.length, symptomDays: a, rate: inside.length ? a / inside.length : 0, label };
	});
	return { cycles, phases };
}

// ─── Do my medications help ───────────────────────────────────────────────────

export interface MedicationEvent {
	medicationId: string;
	name: string;
	date: string;
	kind: 'started' | 'dosage_change';
	oldDosage: string | null;
	newDosage: string | null;
	/** When this medication was stopped after the event, if it was. */
	stopDate: string | null;
	/** Entry types it is taken for; empty: not said. */
	forTypes: string[];
}

/** Starts and dose changes of every medication, newest first. */
export function medicationEvents(medications: Medication[], changes: MedicationChange[]): MedicationEvent[] {
	const events: MedicationEvent[] = [];
	const dose = (m: Medication) => (m.dosage ? `${m.dosage}${m.unit ? ` ${m.unit}` : ''}` : null);
	for (const med of medications) {
		const own = changes.filter((c) => c.medicationId === med.id).sort((a, b) => a.changeDate.localeCompare(b.changeDate));
		const stops = own.filter((c) => c.changeType === 'stopped').map((c) => c.changeDate);
		const stopAfter = (date: string) => stops.find((s) => s >= date) ?? (med.endDate && med.endDate >= date ? med.endDate : null);
		// The first dose change tells which dose the medication was started with.
		const firstChange = own.find((c) => c.changeType === 'dosage_change');
		const base = { medicationId: med.id, name: med.name, forTypes: med.forTypes ?? [] };
		events.push({ ...base, date: med.startDate, kind: 'started', oldDosage: null, newDosage: firstChange ? firstChange.oldDosage : dose(med), stopDate: stopAfter(med.startDate) });
		for (const change of own) {
			if (change.changeType === 'stopped') continue;
			if (change.changeType === 'started' && change.changeDate === med.startDate) continue;
			events.push({ ...base, date: change.changeDate, kind: change.changeType, oldDosage: change.oldDosage, newDosage: change.newDosage, stopDate: stopAfter(change.changeDate) });
		}
	}
	return events.sort((a, b) => b.date.localeCompare(a.date));
}

export interface MedicationEffect {
	event: MedicationEvent;
	before: PeriodCounts & { avgIntensity: number | null };
	after: PeriodCounts & { avgIntensity: number | null };
	/** null when either side has fewer than MIN_MEDICATION_DAYS tracked days. */
	result: 'fewer' | 'more' | 'similar' | null;
	/** Other medications started or changed within the compared days. */
	alsoChanged: string[];
	/** The medication is linked to this type (or not linked to any). */
	linked: boolean;
}

export const MEDICATION_WINDOW = 30;
export const MIN_MEDICATION_DAYS = 10;

/** The 30 days before each event against the days from it on (up to 30, today, or the stop). */
export function medicationEffects(diary: Diary, typeId: string, events: MedicationEvent[], from: string, to: string, today: string): MedicationEffect[] {
	return events
		.filter((event) => event.date >= from && event.date <= to)
		.filter((event) => event.forTypes.length === 0 || event.forTypes.includes(typeId))
		.map((event) => {
			const beforeFrom = addDays(event.date, -MEDICATION_WINDOW);
			const beforeTo = addDays(event.date, -1);
			const ends = [addDays(event.date, MEDICATION_WINDOW - 1), today, ...(event.stopDate ? [addDays(event.stopDate, -1)] : [])];
			const afterTo = ends.sort()[0];
			const side = (start: string, end: string) => ({
				...counts(diary, typeId, start, end < start ? start : end),
				avgIntensity: avgIntensity(logsOf(diary, trackedDays(diary, start, end), typeId))
			});
			const before = side(beforeFrom, beforeTo);
			const after = side(event.date, afterTo);

			let result: MedicationEffect['result'] = null;
			if (before.trackedDays >= MIN_MEDICATION_DAYS && after.trackedDays >= MIN_MEDICATION_DAYS) {
				const r = rates(after.symptomDays, after.trackedDays - after.symptomDays, before.symptomDays, before.trackedDays - before.symptomDays);
				result = r.less < 0.1 && r.ratio <= 0.67 ? 'fewer' : r.greater < 0.1 && r.ratio >= 1.5 ? 'more' : 'similar';
			}
			const alsoChanged = [
				...new Set(
					events.filter((e) => e.medicationId !== event.medicationId && e.date >= beforeFrom && e.date <= afterTo).map((e) => e.name)
				)
			];
			return { event, before, after, result, alsoChanged, linked: event.forTypes.includes(typeId) };
		});
}

// ─── Which types to offer, and all of them at once ────────────────────────────

/** Entry types worth analysing (symptoms and events with logs in the period), most logged first. */
export function analysableTypes(diary: Diary, entryTypes: EntryType[], from: string, to: string): Array<{ type: EntryType; days: number }> {
	const days = trackedDays(diary, from, to);
	return entryTypes
		.filter((type) => type.category === 'symptom' || type.category === 'event')
		.map((type) => ({ type, days: days.filter((date) => diary.get(date)!.types.has(type.id)).length }))
		.filter((item) => item.days > 0)
		.sort((a, b) => b.days - a.days);
}

/**
 * One diary day and everything you can do to it, shared by the Today screen
 * and the calendar's day sheet. All changes go through the API, so the web
 * app and the Android app behave the same.
 */
import { api, getJson } from '$lib/api.js';
import type { Entry, EntryTypeLog } from '$lib/types/index.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

async function send<T>(path: string, method: string, body?: unknown): Promise<T> {
	const res = await api(path, { method, headers: JSON_HEADERS, body: body === undefined ? undefined : JSON.stringify(body) });
	if (!res.ok) {
		const err = await res.json().catch(() => null);
		throw new Error(err?.message ?? err?.error ?? res.statusText);
	}
	return res.status === 204 ? (undefined as T) : res.json();
}

export interface DayPlace {
	lat: number;
	lon: number;
	name: string | null;
	source: 'auto' | 'manual';
}

/** A day with something in it; a day that only has a place (set while travelling) counts as empty. */
export function isTracked(entry: Entry | null | undefined): boolean {
	return !!entry && ((entry.logs?.length ?? 0) > 0 || (entry.triggers?.length ?? 0) > 0 || !!entry.note?.trim() || entry.wellbeing != null);
}

export interface LogInput {
	entryTypeId: string;
	startTime: string;
	endTime: string | null;
	metricValues: Record<string, unknown>;
}

export class DiaryDay {
	date: string;
	entry = $state<Entry | null>(null);
	loading = $state(true);

	constructor(date: string, initial?: Entry | null) {
		this.date = date;
		if (initial !== undefined) {
			this.entry = initial;
			this.loading = false;
		}
	}

	async load(): Promise<void> {
		this.loading = true;
		try {
			this.entry = await getJson<Entry | null>(globalThis.fetch, `/api/entries?date=${this.date}`);
		} finally {
			this.loading = false;
		}
	}

	/** Creates the day's entry if needed (one per day) and sets the given fields. */
	private async upsert(fields: { note?: string | null; wellbeing?: number | null; location?: DayPlace | null } = {}): Promise<Entry> {
		this.entry = await send<Entry>('/api/entries', 'POST', { date: this.date, ...fields });
		return this.entry;
	}

	setWellbeing(value: number | null): Promise<Entry> {
		return this.upsert({ wellbeing: value });
	}

	saveNote(note: string): Promise<Entry> {
		return this.upsert({ note });
	}

	/** Where the day was spent; null goes back to home. */
	setPlace(place: DayPlace | null): Promise<Entry> {
		return this.upsert({ location: place });
	}

	async addLog(input: LogInput): Promise<EntryTypeLog> {
		const entry = this.entry ?? (await this.upsert());
		const log = await send<EntryTypeLog>('/api/entry-type-logs', 'POST', { entryId: entry.id, ...input });
		await this.load();
		return log;
	}

	async updateLog(id: string, input: Partial<Omit<LogInput, 'entryTypeId'>>): Promise<void> {
		await send('/api/entry-type-logs/' + id, 'PUT', input);
		await this.load();
	}

	/** Mark an ongoing symptom as over, now (or at its start, if that is still ahead). */
	endLog(id: string): Promise<void> {
		const start = this.entry?.logs?.find((l) => l.id === id)?.startTime;
		const now = new Date().toISOString();
		return this.updateLog(id, { endTime: start && Date.parse(start) > Date.parse(now) ? start : now });
	}

	async deleteLog(id: string): Promise<void> {
		await send('/api/entry-type-logs/' + id, 'DELETE');
		await this.load();
	}

	hasTrigger(triggerId: string): boolean {
		return this.entry?.triggers?.some((t) => t.triggerId === triggerId) ?? false;
	}

	/** Triggers with a change on the way; a second tap meanwhile is ignored, so it can't undo or repeat the first. */
	private toggling = new Set<string>();

	async toggleTrigger(triggerId: string): Promise<void> {
		if (this.toggling.has(triggerId)) return;
		this.toggling.add(triggerId);
		try {
			const entry = this.entry ?? (await this.upsert());
			const action = this.hasTrigger(triggerId) ? 'remove_trigger' : 'add_trigger';
			this.entry = await send<Entry>(`/api/entries/${entry.id}`, 'PATCH', { action, triggerId });
		} finally {
			this.toggling.delete(triggerId);
		}
	}
}

/** ISO timestamp for a local date ('YYYY-MM-DD') and time ('HH:MM'). */
export function localDateTime(date: string, time: string): string {
	return new Date(`${date}T${time}:00`).toISOString();
}

/** 'HH:MM' in local time of an ISO timestamp. */
export function localTime(iso: string): string {
	const d = new Date(iso);
	return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

import { getJson } from '$lib/api.js';
import { toDateString } from '$lib/utils/date';
import type { Entry, EntryType, Trigger, UserProfile } from '$lib/types/index.js';

function rank(ids: string[]): string[] {
	const counts = new Map<string, number>();
	for (const id of ids) counts.set(id, (counts.get(id) ?? 0) + 1);
	return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([id]) => id);
}

/**
 * What a day view needs: entry types, triggers, the profile, the day's entry,
 * and the last 90 days (ending at `date`), which order the quick-log tiles and
 * trigger chips by use.
 */
export async function loadDayData(fetchFn: typeof fetch, date: string) {
	const since = new Date(`${date}T12:00:00`);
	since.setDate(since.getDate() - 90);
	const [entryTypes, triggers, profile, recent] = await Promise.all([
		getJson<EntryType[]>(fetchFn, '/api/entry-types'),
		getJson<Trigger[]>(fetchFn, '/api/triggers'),
		getJson<UserProfile>(fetchFn, '/api/profile'),
		getJson<Entry[]>(fetchFn, `/api/entries?from=${toDateString(since)}&to=${date}`)
	]);
	return {
		entryTypes,
		triggers,
		profile,
		recent,
		entry: recent.find((e) => e.date === date) ?? null,
		typeRanking: rank(recent.flatMap((e) => (e.logs ?? []).map((l) => l.entryTypeId))),
		triggerRanking: rank(recent.flatMap((e) => (e.triggers ?? []).map((tr) => tr.triggerId)))
	};
}

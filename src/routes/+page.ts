import { getJson } from '$lib/api.js';
import { toDateString } from '$lib/utils/date';
import type { Entry, EntryType, Trigger, UserProfile } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const today = toDateString(new Date());
	const since = new Date();
	since.setDate(since.getDate() - 90);
	// The last 90 days order the quick-log tiles and trigger chips by use and fill the week strip.
	const [entryTypes, triggers, profile, recent] = await Promise.all([
		getJson<EntryType[]>(fetch, '/api/entry-types'),
		getJson<Trigger[]>(fetch, '/api/triggers'),
		getJson<UserProfile>(fetch, '/api/profile'),
		getJson<Entry[]>(fetch, `/api/entries?from=${toDateString(since)}&to=${today}`)
	]);
	// Most used first.
	const rank = (ids: string[]) => {
		const counts = new Map<string, number>();
		for (const id of ids) counts.set(id, (counts.get(id) ?? 0) + 1);
		return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([id]) => id);
	};
	const typeRanking = rank(recent.flatMap((e) => (e.logs ?? []).map((l) => l.entryTypeId)));
	const triggerRanking = rank(recent.flatMap((e) => (e.triggers ?? []).map((tr) => tr.triggerId)));

	return { today, entryTypes, triggers, profile, recent, typeRanking, triggerRanking };
};

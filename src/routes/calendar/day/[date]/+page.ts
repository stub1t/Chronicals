import { getJson } from '$lib/api.js';
import type { EntryType, Trigger, Entry, EnvironmentalData } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch }) => {
	const date = encodeURIComponent(params.date);
	const [entry, entryTypes, allTriggers, envData] = await Promise.all([
		getJson<Entry | null>(fetch, `/api/entries?date=${date}`),
		getJson<EntryType[]>(fetch, '/api/entry-types'),
		getJson<Trigger[]>(fetch, '/api/triggers'),
		getJson<EnvironmentalData | null>(fetch, `/api/environmental?date=${date}&cached=1`)
	]);
	return { date: params.date, entry, entryTypes, allTriggers, envData };
};

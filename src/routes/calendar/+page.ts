import { getJson } from '$lib/api.js';
import type { EntryType, Trigger } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [entryTypes, allTriggers] = await Promise.all([
		getJson<EntryType[]>(fetch, '/api/entry-types'),
		getJson<Trigger[]>(fetch, '/api/triggers')
	]);
	return { entryTypes, allTriggers };
};

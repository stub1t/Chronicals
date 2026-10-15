import { getJson } from '$lib/api.js';
import { toDateString } from '$lib/utils/date';
import type { Entry, EntryType } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const today = toDateString(new Date());
	const [entryTypes, entry] = await Promise.all([
		getJson<EntryType[]>(fetch, '/api/entry-types'),
		getJson<Entry | null>(fetch, `/api/entries?date=${today}`)
	]);
	return { today, mealType: entryTypes.find((type) => type.category === 'meal') ?? null, entry };
};

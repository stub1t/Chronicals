import { getJson } from '$lib/api.js';
import type { EntryType } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => ({
	entryTypes: await getJson<EntryType[]>(fetch, '/api/entry-types')
});

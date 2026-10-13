import { getJson } from '$lib/api.js';
import type { EntryType, UserProfile } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [entryTypes, profile] = await Promise.all([
		getJson<EntryType[]>(fetch, '/api/entry-types'),
		getJson<UserProfile>(fetch, '/api/profile')
	]);
	return { entryTypes, profile };
};

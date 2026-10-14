import { getJson } from '$lib/api.js';
import type { EntryType, UserProfile } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [profile, entryTypes] = await Promise.all([
		getJson<UserProfile>(fetch, '/api/profile'),
		getJson<EntryType[]>(fetch, '/api/entry-types')
	]);
	return { profile, entryTypes };
};

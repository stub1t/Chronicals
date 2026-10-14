import { getJson } from '$lib/api.js';
import type { EntryType, Medication, Trigger, UserProfile } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [profile, medications, entryTypes, triggers] = await Promise.all([
		getJson<UserProfile>(fetch, '/api/profile'),
		getJson<Medication[]>(fetch, '/api/medications'),
		getJson<EntryType[]>(fetch, '/api/entry-types'),
		getJson<Trigger[]>(fetch, '/api/triggers')
	]);
	return { profile, medications, entryTypes, triggers };
};

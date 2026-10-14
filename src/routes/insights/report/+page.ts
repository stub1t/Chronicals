import { getJson } from '$lib/api.js';
import type { EntryType, Medication, MedicationChange, UserProfile } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [entryTypes, profile, medications, changes] = await Promise.all([
		getJson<EntryType[]>(fetch, '/api/entry-types'),
		getJson<UserProfile>(fetch, '/api/profile'),
		getJson<Medication[]>(fetch, '/api/medications'),
		getJson<MedicationChange[]>(fetch, '/api/medication-changes')
	]);
	return { entryTypes, profile, medications, changes };
};

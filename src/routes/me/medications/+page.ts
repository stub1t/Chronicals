import { getJson } from '$lib/api.js';
import type { EntryType, Medication, MedicationChange } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [medications, changes, entryTypes] = await Promise.all([
		getJson<Medication[]>(fetch, '/api/medications'),
		getJson<MedicationChange[]>(fetch, '/api/medication-changes'),
		getJson<EntryType[]>(fetch, '/api/entry-types')
	]);
	return { medications, changes, entryTypes };
};

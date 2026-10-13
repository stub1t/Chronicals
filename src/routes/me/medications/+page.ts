import { getJson } from '$lib/api.js';
import type { Medication, MedicationChange } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [medications, changes] = await Promise.all([
		getJson<Medication[]>(fetch, '/api/medications'),
		getJson<MedicationChange[]>(fetch, '/api/medication-changes')
	]);
	return { medications, changes };
};

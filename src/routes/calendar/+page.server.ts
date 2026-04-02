import { getAllEntryTypes } from '$lib/server/queries/entryTypes.js';
import { getAllTriggers } from '$lib/server/queries/triggers.js';

export const load = () => ({
	entryTypes: getAllEntryTypes(),
	allTriggers: getAllTriggers()
});

import { getAllEntryTypes } from '$lib/server/queries/entryTypes.js';

export function load() {
	return { entryTypes: getAllEntryTypes() };
}

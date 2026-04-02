import { getAllEntryTypes } from '$lib/server/queries/entryTypes.js';

export const load = () => ({ entryTypes: getAllEntryTypes() });

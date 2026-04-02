import { getEntryByDate } from '$lib/server/queries/entries.js';
import { getAllEntryTypes } from '$lib/server/queries/entryTypes.js';
import { getAllTriggers } from '$lib/server/queries/triggers.js';
import { getEnvDataForDate } from '$lib/server/weather.js';

export function load({ params }: { params: { date: string } }) {
	return {
		date: params.date,
		entry: getEntryByDate(params.date),
		entryTypes: getAllEntryTypes(),
		allTriggers: getAllTriggers(),
		envData: getEnvDataForDate(params.date)
	};
}

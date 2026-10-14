import { getJson } from '$lib/api.js';
import { addDays, daysBetween } from '$lib/insights/dates.js';
import { toDateString } from '$lib/utils/date';
import type { Entry, EntryType, EnvironmentalData, Medication, MedicationChange, Trigger } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export type Period = '3m' | '6m' | '12m' | 'all';
const MONTHS: Record<Exclude<Period, 'all'>, number> = { '3m': 3, '6m': 6, '12m': 12 };

export const load: PageLoad = async ({ fetch, url }) => {
	const param = url.searchParams.get('period');
	const period: Period = param === '6m' || param === '12m' || param === 'all' ? param : '3m';
	const today = toDateString(new Date());

	let from: string;
	if (period === 'all') from = '1900-01-01';
	else {
		const d = new Date();
		d.setMonth(d.getMonth() - MONTHS[period]);
		from = addDays(toDateString(d), 1);
	}
	// The period before, for the trend; and one more day for "the day before" triggers.
	const loadFrom = period === 'all' ? from : addDays(from, -(daysBetween(from, today) + 2));

	const [entries, entryTypes, triggers, medications, changes] = await Promise.all([
		getJson<Entry[]>(fetch, `/api/entries?from=${loadFrom}&to=${today}`),
		getJson<EntryType[]>(fetch, '/api/entry-types'),
		getJson<Trigger[]>(fetch, '/api/triggers'),
		getJson<Medication[]>(fetch, '/api/medications'),
		getJson<MedicationChange[]>(fetch, '/api/medication-changes')
	]);

	// "All" starts at the first diary day.
	if (period === 'all') from = entries.map((e) => e.date).sort()[0] ?? today;
	// Weather for the period and the day before (for "the day before" links), fetched where missing.
	const weather = await getJson<EnvironmentalData[]>(fetch, `/api/environmental?from=${addDays(from, -1)}&to=${today}&fill=1`);

	return { period, from, to: today, entries, weather, entryTypes, triggers, medications, changes, type: url.searchParams.get('type') };
};

import { getJson } from '$lib/api.js';
import { getDaysInMonth, toDateString, toYearMonth } from '$lib/utils/date';
import type { Entry } from '$lib/types/index.js';
import type { PageLoad } from './$types';

// The shown month and selected day live in the URL (?month=2026-10&day=2026-10-14),
// so coming back from a day page returns to the same place.
export const load: PageLoad = async ({ fetch, url }) => {
	const today = toDateString(new Date());
	const param = url.searchParams.get('month');
	const yearMonth = param && /^\d{4}-(0[1-9]|1[0-2])$/.test(param) ? param : toYearMonth(new Date());
	const day = url.searchParams.get('day');
	const last = String(getDaysInMonth(yearMonth)).padStart(2, '0');
	const entries = await getJson<Entry[]>(fetch, `/api/entries?from=${yearMonth}-01&to=${yearMonth}-${last}`);
	return {
		today,
		yearMonth,
		selected: day && day.startsWith(yearMonth) ? day : today.startsWith(yearMonth) ? today : null,
		entries
	};
};

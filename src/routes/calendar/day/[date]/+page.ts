import { error } from '@sveltejs/kit';
import { loadDayData } from '$lib/diary/day-data.js';
import { toDateString } from '$lib/utils/date';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch }) => {
	// A real date only: Date.parse would accept 2026-02-31 as 3 March.
	const parsed = Date.parse(`${params.date}T00:00:00Z`);
	if (!/^\d{4}-\d{2}-\d{2}$/.test(params.date) || Number.isNaN(parsed) || new Date(parsed).toISOString().slice(0, 10) !== params.date) error(404);
	return { date: params.date, today: toDateString(new Date()), ...(await loadDayData(fetch, params.date)) };
};

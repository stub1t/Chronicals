import { error } from '@sveltejs/kit';
import { loadDayData } from '$lib/diary/day-data.js';
import { toDateString } from '$lib/utils/date';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch }) => {
	if (!/^\d{4}-\d{2}-\d{2}$/.test(params.date) || Number.isNaN(Date.parse(params.date))) error(404);
	return { date: params.date, today: toDateString(new Date()), ...(await loadDayData(fetch, params.date)) };
};

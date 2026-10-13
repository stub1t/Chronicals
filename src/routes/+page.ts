import { loadDayData } from '$lib/diary/day-data.js';
import { toDateString } from '$lib/utils/date';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const today = toDateString(new Date());
	return { today, ...(await loadDayData(fetch, today)) };
};

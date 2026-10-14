import { getJson } from '$lib/api.js';
import { toDateString } from '$lib/utils/date';
import type { Entry } from '$lib/types/index.js';
import type { PageLoad } from './$types';

/** Monday of the week containing `date` ('YYYY-MM-DD'). */
function mondayOf(date: string): Date {
	const d = new Date(`${date}T12:00:00`);
	d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
	return d;
}

export const load: PageLoad = async ({ fetch, url }) => {
	const today = toDateString(new Date());
	const param = url.searchParams.get('week');
	const valid = param && /^\d{4}-\d{2}-\d{2}$/.test(param) && !Number.isNaN(Date.parse(`${param}T12:00:00`));
	const monday = mondayOf(valid ? param : today);
	const days = Array.from({ length: 7 }, (_, i) => {
		const d = new Date(monday);
		d.setDate(monday.getDate() + i);
		return toDateString(d);
	});
	const entries = await getJson<Entry[]>(fetch, `/api/entries?from=${days[0]}&to=${days[6]}`);
	return { today, days, entries };
};

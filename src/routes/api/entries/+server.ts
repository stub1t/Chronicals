import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getMonthEntries, getMonthDotData, createEntry } from '$lib/server/queries/entries.js';

export const GET: RequestHandler = ({ url }) => {
	const month = url.searchParams.get('month'); // 'YYYY-MM'
	const dotOnly = url.searchParams.get('dots') === '1';

	if (!month) return json({ error: 'month param required' }, { status: 400 });

	if (dotOnly) {
		return json(getMonthDotData(month));
	}
	return json(getMonthEntries(month));
};

export const POST: RequestHandler = async ({ request }) => {
	const body = await request.json();
	const { date, note } = body;
	if (!date) return json({ error: 'date required' }, { status: 400 });

	const entry = createEntry(date, note);
	return json(entry, { status: 201 });
};

import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getMonthEntries, getMonthDotData, createEntry } from '$lib/server/queries/entries.js';
import { assertDate, assertYearMonth, optionalText, parseJsonRequest } from '$lib/server/security.js';

export const GET: RequestHandler = ({ url }) => {
	const month = url.searchParams.get('month');
	const dotOnly = url.searchParams.get('dots') === '1';

	if (!month) return json({ error: 'month param required' }, { status: 400 });

	const safeMonth = assertYearMonth(month, 'month');
	return json(dotOnly ? getMonthDotData(safeMonth) : getMonthEntries(safeMonth));
};

export const POST: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const entry = createEntry(assertDate(body.date, 'date'), optionalText(body.note));
	return json(entry, { status: 201 });
};

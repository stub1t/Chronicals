import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getMonthlyStats } from '$lib/server/queries/analysis.js';
import { assertDate, assertIdentifier, assertYearMonth } from '$lib/server/security.js';

export const GET: RequestHandler = ({ url }) => {
	const entryTypeId = url.searchParams.get('entryTypeId');
	const month = url.searchParams.get('month');
	const startDate = url.searchParams.get('startDate');
	const endDate = url.searchParams.get('endDate');

	if (!entryTypeId || (!month && (!startDate || !endDate))) {
		return json({ error: 'entryTypeId and (month or startDate+endDate) required' }, { status: 400 });
	}

	return json(
		getMonthlyStats(
			assertIdentifier(entryTypeId, 'entryTypeId'),
			month ? assertYearMonth(month, 'month') : 'custom',
			startDate ? assertDate(startDate, 'startDate') : undefined,
			endDate ? assertDate(endDate, 'endDate') : undefined
		)
	);
};

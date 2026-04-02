import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getMonthlyStats } from '$lib/server/queries/analysis.js';

export const GET: RequestHandler = ({ url }) => {
	const entryTypeId = url.searchParams.get('entryTypeId');
	const month = url.searchParams.get('month') ?? 'custom';
	const startDate = url.searchParams.get('startDate') ?? undefined;
	const endDate = url.searchParams.get('endDate') ?? undefined;
	if (!entryTypeId || (!url.searchParams.has('month') && (!startDate || !endDate))) {
		return json({ error: 'entryTypeId and (month or startDate+endDate) required' }, { status: 400 });
	}
	return json(getMonthlyStats(entryTypeId, month, startDate, endDate));
};

import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { createEntryTypeLog } from '$lib/server/queries/entryTypeLogs.js';

export const POST: RequestHandler = async ({ request }) => {
	const body = await request.json();
	const { entryId, entryTypeId, startTime, endTime, metricValues } = body;
	if (!entryId || !entryTypeId || !startTime) {
		return json({ error: 'entryId, entryTypeId, startTime required' }, { status: 400 });
	}
	const log = createEntryTypeLog({ entryId, entryTypeId, startTime, endTime, metricValues: metricValues ?? {} });
	return json(log, { status: 201 });
};

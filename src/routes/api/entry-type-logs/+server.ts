import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { createEntryTypeLog } from '$lib/server/queries/entryTypeLogs.js';
import { assertIdentifier, assertIsoDatetime, optionalMetricValues, parseJsonRequest } from '$lib/server/security.js';

export const POST: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const log = createEntryTypeLog({
		entryId: assertIdentifier(body.entryId, 'entryId'),
		entryTypeId: assertIdentifier(body.entryTypeId, 'entryTypeId'),
		startTime: assertIsoDatetime(body.startTime, 'startTime'),
		endTime: body.endTime == null ? null : assertIsoDatetime(body.endTime, 'endTime'),
		metricValues: optionalMetricValues(body.metricValues) ?? {}
	});
	return json(log, { status: 201 });
};

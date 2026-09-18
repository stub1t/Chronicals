import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { updateEntryTypeLog, deleteEntryTypeLog } from '$lib/server/queries/entryTypeLogs.js';
import { assertIdentifier, assertIsoDatetime, optionalMetricValues, parseJsonRequest } from '$lib/server/security.js';

export const PUT: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const log = updateEntryTypeLog(assertIdentifier(event.params.id, 'id'), {
		...(body.startTime !== undefined && { startTime: assertIsoDatetime(body.startTime, 'startTime') }),
		...(body.endTime !== undefined && {
			endTime: body.endTime === null ? null : assertIsoDatetime(body.endTime, 'endTime')
		}),
		...(body.metricValues !== undefined && { metricValues: optionalMetricValues(body.metricValues) ?? {} })
	});
	if (!log) return json({ error: 'Not found' }, { status: 404 });
	return json(log);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteEntryTypeLog(assertIdentifier(params.id, 'id'));
	return new Response(null, { status: 204 });
};

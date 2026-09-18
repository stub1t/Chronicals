import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { updateEntryType, deleteEntryType } from '$lib/server/queries/entryTypes.js';
import { assertIdentifier, assertMetricSchema, optionalColor, optionalText, parseJsonRequest, requiredText } from '$lib/server/security.js';

export const PUT: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const type = updateEntryType(assertIdentifier(event.params.id, 'id'), {
		...(body.name !== undefined && { name: requiredText(body.name, 'name', 120) }),
		...(body.icon !== undefined && { icon: optionalText(body.icon, 32) }),
		...(body.color !== undefined && { color: optionalColor(body.color) ?? null }),
		...(body.metricSchema !== undefined && { metricSchema: assertMetricSchema(body.metricSchema) })
	});
	if (!type) return json({ error: 'Not found' }, { status: 404 });
	return json(type);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteEntryType(assertIdentifier(params.id, 'id'));
	return new Response(null, { status: 204 });
};

import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { updateTrigger, deleteTrigger } from '$lib/server/queries/triggers.js';
import { assertIdentifier, optionalTriggerCategory, parseJsonRequest, requiredText } from '$lib/server/security.js';

export const PUT: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const trigger = updateTrigger(assertIdentifier(event.params.id, 'id'), {
		...(body.name !== undefined && { name: requiredText(body.name, 'name', 120) }),
		...(body.category !== undefined && { category: optionalTriggerCategory(body.category) })
	});
	if (!trigger) return json({ error: 'Not found' }, { status: 404 });
	return json(trigger);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteTrigger(assertIdentifier(params.id, 'id'));
	return new Response(null, { status: 204 });
};

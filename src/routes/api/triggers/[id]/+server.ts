import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { updateTrigger, deleteTrigger } from '$lib/server/queries/triggers.js';
import type { TriggerCategory } from '$lib/types/index.js';

export const PUT: RequestHandler = async ({ params, request }) => {
	const body = await request.json();
	const trigger = updateTrigger(params.id, { name: body.name, category: body.category as TriggerCategory });
	if (!trigger) return json({ error: 'Not found' }, { status: 404 });
	return json(trigger);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteTrigger(params.id);
	return new Response(null, { status: 204 });
};

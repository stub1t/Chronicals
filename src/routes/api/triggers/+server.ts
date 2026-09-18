import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getAllTriggers, getTriggersByCategory, createTrigger } from '$lib/server/queries/triggers.js';
import { assertTriggerCategory, optionalTriggerCategory, parseJsonRequest, requiredText } from '$lib/server/security.js';

export const GET: RequestHandler = ({ url }) => {
	const category = url.searchParams.get('category');
	if (category) return json(getTriggersByCategory(assertTriggerCategory(category)));
	return json(getAllTriggers());
};

export const POST: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const trigger = createTrigger({
		name: requiredText(body.name, 'name', 120),
		category: optionalTriggerCategory(body.category)
	});
	return json(trigger, { status: 201 });
};

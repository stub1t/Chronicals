import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getAllTriggers, getTriggersByCategory, createTrigger } from '$lib/server/queries/triggers.js';
import type { TriggerCategory } from '$lib/types/index.js';

export const GET: RequestHandler = ({ url }) => {
	const category = url.searchParams.get('category');
	if (category) return json(getTriggersByCategory(category));
	return json(getAllTriggers());
};

export const POST: RequestHandler = async ({ request }) => {
	const body = await request.json();
	const trigger = createTrigger({ name: body.name, category: body.category as TriggerCategory });
	return json(trigger, { status: 201 });
};

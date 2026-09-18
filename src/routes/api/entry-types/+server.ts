import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getAllEntryTypes, createEntryType } from '$lib/server/queries/entryTypes.js';
import { assertMetricSchema, optionalColor, optionalEntryCategory, optionalText, parseJsonRequest, requiredText } from '$lib/server/security.js';

export const GET: RequestHandler = () => json(getAllEntryTypes());

export const POST: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const type = createEntryType({
		name: requiredText(body.name, 'name', 120),
		icon: optionalText(body.icon, 32) ?? undefined,
		color: optionalColor(body.color) ?? undefined,
		metricSchema: assertMetricSchema(body.metricSchema),
		category: optionalEntryCategory(body.category)
	});
	return json(type, { status: 201 });
};

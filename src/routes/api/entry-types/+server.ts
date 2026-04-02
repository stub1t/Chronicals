import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getAllEntryTypes, createEntryType } from '$lib/server/queries/entryTypes.js';

export const GET: RequestHandler = () => json(getAllEntryTypes());

export const POST: RequestHandler = async ({ request }) => {
	const body = await request.json();
	const type = createEntryType(body);
	return json(type, { status: 201 });
};

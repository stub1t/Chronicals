import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { updateEntryType, deleteEntryType } from '$lib/server/queries/entryTypes.js';

export const PUT: RequestHandler = async ({ params, request }) => {
	const body = await request.json();
	const type = updateEntryType(params.id, body);
	if (!type) return json({ error: 'Not found' }, { status: 404 });
	return json(type);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteEntryType(params.id);
	return new Response(null, { status: 204 });
};

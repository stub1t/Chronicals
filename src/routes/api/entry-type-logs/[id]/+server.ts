import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { updateEntryTypeLog, deleteEntryTypeLog } from '$lib/server/queries/entryTypeLogs.js';

export const PUT: RequestHandler = async ({ params, request }) => {
	const body = await request.json();
	const log = updateEntryTypeLog(params.id, body);
	if (!log) return json({ error: 'Not found' }, { status: 404 });
	return json(log);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteEntryTypeLog(params.id);
	return new Response(null, { status: 204 });
};

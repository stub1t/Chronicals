import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getEntry, updateEntry, deleteEntry, addEntryTrigger, removeEntryTrigger } from '$lib/server/queries/entries.js';

export const GET: RequestHandler = ({ params }) => {
	const entry = getEntry(params.id);
	if (!entry) return json({ error: 'Not found' }, { status: 404 });
	return json(entry);
};

export const PUT: RequestHandler = async ({ params, request }) => {
	const body = await request.json();
	const entry = updateEntry(params.id, { note: body.note });
	if (!entry) return json({ error: 'Not found' }, { status: 404 });
	return json(entry);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteEntry(params.id);
	return new Response(null, { status: 204 });
};

export const PATCH: RequestHandler = async ({ params, request }) => {
	const body = await request.json();
	if (body.action === 'add_trigger') {
		addEntryTrigger(params.id, body.triggerId, body.note);
	} else if (body.action === 'remove_trigger') {
		removeEntryTrigger(params.id, body.triggerId);
	}
	const entry = getEntry(params.id);
	return json(entry);
};

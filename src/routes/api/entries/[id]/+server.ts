import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getEntry, updateEntry, deleteEntry, addEntryTrigger, removeEntryTrigger } from '$lib/server/queries/entries.js';
import { assertIdentifier, optionalText, parseJsonRequest, requiredText } from '$lib/server/security.js';

export const GET: RequestHandler = ({ params }) => {
	const entry = getEntry(assertIdentifier(params.id, 'id'));
	if (!entry) return json({ error: 'Not found' }, { status: 404 });
	return json(entry);
};

export const PUT: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const entry = updateEntry(assertIdentifier(event.params.id, 'id'), { note: optionalText(body.note) ?? undefined });
	if (!entry) return json({ error: 'Not found' }, { status: 404 });
	return json(entry);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteEntry(assertIdentifier(params.id, 'id'));
	return new Response(null, { status: 204 });
};

export const PATCH: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const entryId = assertIdentifier(event.params.id, 'id');
	const action = requiredText(body.action, 'action', 32);

	if (action === 'add_trigger') {
		addEntryTrigger(entryId, assertIdentifier(body.triggerId, 'triggerId'), optionalText(body.note));
	} else if (action === 'remove_trigger') {
		removeEntryTrigger(entryId, assertIdentifier(body.triggerId, 'triggerId'));
	} else {
		return json({ error: 'Invalid action' }, { status: 400 });
	}

	const entry = getEntry(entryId);
	return json(entry);
};

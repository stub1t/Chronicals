import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { updateMedication, deleteMedication } from '$lib/server/queries/profile.js';

export const PUT: RequestHandler = async ({ params, request }) => {
	const body = await request.json();
	const med = updateMedication(params.id, body);
	if (!med) return json({ error: 'Not found' }, { status: 404 });
	return json(med);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteMedication(params.id);
	return new Response(null, { status: 204 });
};

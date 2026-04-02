import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getMedications, createMedication } from '$lib/server/queries/profile.js';

export const GET: RequestHandler = () => json(getMedications());

export const POST: RequestHandler = async ({ request }) => {
	const body = await request.json();
	const med = createMedication(body);
	return json(med, { status: 201 });
};

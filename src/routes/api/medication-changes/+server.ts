import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getMedicationChanges, createMedicationChange } from '$lib/server/queries/profile.js';
import type { MedicationChangeType } from '$lib/types/index.js';

export const GET: RequestHandler = ({ url }) => {
	const medicationId = url.searchParams.get('medicationId') ?? undefined;
	return json(getMedicationChanges(medicationId));
};

export const POST: RequestHandler = async ({ request }) => {
	const body = await request.json();
	const change = createMedicationChange({ ...body, changeType: body.changeType as MedicationChangeType });
	return json(change, { status: 201 });
};

import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getMedications, createMedication } from '$lib/server/queries/profile.js';
import { assertDate, optionalText, parseJsonRequest, requiredText } from '$lib/server/security.js';

export const GET: RequestHandler = () => json(getMedications());

export const POST: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const med = createMedication({
		name: requiredText(body.name, 'name', 120),
		dosage: optionalText(body.dosage, 64) ?? undefined,
		unit: optionalText(body.unit, 32) ?? undefined,
		startDate: assertDate(body.startDate, 'startDate'),
		notes: optionalText(body.notes, 1000) ?? undefined
	});
	return json(med, { status: 201 });
};

import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { updateMedication, deleteMedication } from '$lib/server/queries/profile.js';
import { assertBoolean, assertDate, assertIdentifier, optionalText, parseJsonRequest, requiredText } from '$lib/server/security.js';

export const PUT: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const med = updateMedication(assertIdentifier(event.params.id, 'id'), {
		...(body.name !== undefined && { name: requiredText(body.name, 'name', 120) }),
		...(body.dosage !== undefined && { dosage: optionalText(body.dosage, 64) }),
		...(body.unit !== undefined && { unit: optionalText(body.unit, 32) }),
		...(body.startDate !== undefined && { startDate: assertDate(body.startDate, 'startDate') }),
		...(body.endDate !== undefined && { endDate: body.endDate === null ? null : assertDate(body.endDate, 'endDate') }),
		...(body.notes !== undefined && { notes: optionalText(body.notes, 1000) }),
		...(body.isActive !== undefined && { isActive: assertBoolean(body.isActive, 'isActive') })
	});
	if (!med) return json({ error: 'Not found' }, { status: 404 });
	return json(med);
};

export const DELETE: RequestHandler = ({ params }) => {
	deleteMedication(assertIdentifier(params.id, 'id'));
	return new Response(null, { status: 204 });
};

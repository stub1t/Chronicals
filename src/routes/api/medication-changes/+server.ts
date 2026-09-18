import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getMedicationChanges, createMedicationChange } from '$lib/server/queries/profile.js';
import { assertDate, assertIdentifier, assertMedicationChangeType, optionalText, parseJsonRequest } from '$lib/server/security.js';

export const GET: RequestHandler = ({ url }) => {
	const medicationId = url.searchParams.get('medicationId') ?? undefined;
	return json(getMedicationChanges(medicationId ? assertIdentifier(medicationId, 'medicationId') : undefined));
};

export const POST: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const change = createMedicationChange({
		medicationId: body.medicationId == null ? undefined : assertIdentifier(body.medicationId, 'medicationId'),
		changeType: assertMedicationChangeType(body.changeType),
		oldDosage: optionalText(body.oldDosage, 64) ?? undefined,
		newDosage: optionalText(body.newDosage, 64) ?? undefined,
		changeDate: assertDate(body.changeDate, 'changeDate'),
		reason: optionalText(body.reason, 500) ?? undefined,
		notes: optionalText(body.notes, 1000) ?? undefined
	});
	return json(change, { status: 201 });
};

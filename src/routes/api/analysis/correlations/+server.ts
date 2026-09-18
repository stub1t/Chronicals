import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import {
	getTriggerCorrelations,
	getEnvironmentalCorrelations,
	getMedicationFlags
} from '$lib/server/queries/analysis.js';
import { assertDate, assertIdentifier } from '$lib/server/security.js';

export const GET: RequestHandler = ({ url }) => {
	const entryTypeId = url.searchParams.get('entryTypeId');
	const startDate = url.searchParams.get('startDate');
	const endDate = url.searchParams.get('endDate');

	if (!entryTypeId || !startDate || !endDate) {
		return json({ error: 'entryTypeId, startDate, endDate required' }, { status: 400 });
	}

	const safeEntryTypeId = assertIdentifier(entryTypeId, 'entryTypeId');
	const safeStartDate = assertDate(startDate, 'startDate');
	const safeEndDate = assertDate(endDate, 'endDate');

	return json({
		triggers: getTriggerCorrelations(safeEntryTypeId, safeStartDate, safeEndDate),
		environmental: getEnvironmentalCorrelations(safeEntryTypeId, safeStartDate, safeEndDate),
		medicationFlags: getMedicationFlags(safeStartDate, safeEndDate)
	});
};

import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import {
	getTriggerCorrelations,
	getEnvironmentalCorrelations,
	getMedicationFlags
} from '$lib/server/queries/analysis.js';

export const GET: RequestHandler = ({ url }) => {
	const entryTypeId = url.searchParams.get('entryTypeId');
	const startDate = url.searchParams.get('startDate');
	const endDate = url.searchParams.get('endDate');

	if (!entryTypeId || !startDate || !endDate) {
		return json({ error: 'entryTypeId, startDate, endDate required' }, { status: 400 });
	}

	return json({
		triggers: getTriggerCorrelations(entryTypeId, startDate, endDate),
		environmental: getEnvironmentalCorrelations(entryTypeId, startDate, endDate),
		medicationFlags: getMedicationFlags(startDate, endDate)
	});
};

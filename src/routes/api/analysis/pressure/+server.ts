import { json } from '@sveltejs/kit';
import { gte, lte, and, eq } from 'drizzle-orm';
import { db } from '$lib/db/index.js';
import { environmentalData, entries, entryTypeLogs } from '$lib/db/schema.js';

export function GET({ url }: { url: URL }) {
	const startDate = url.searchParams.get('startDate');
	const endDate = url.searchParams.get('endDate');
	const entryTypeId = url.searchParams.get('entryTypeId');

	if (!startDate || !endDate) {
		return json({ error: 'startDate and endDate required' }, { status: 400 });
	}

	const envRows = db
		.select({ date: environmentalData.date, delta: environmentalData.pressureDeltaHpa })
		.from(environmentalData)
		.where(and(gte(environmentalData.date, startDate), lte(environmentalData.date, endDate)))
		.all();

	const symptomDates = new Set<string>();
	if (entryTypeId) {
		const rows = db
			.select({ date: entries.date })
			.from(entryTypeLogs)
			.innerJoin(entries, eq(entryTypeLogs.entryId, entries.id))
			.where(
				and(
					eq(entryTypeLogs.entryTypeId, entryTypeId),
					gte(entries.date, startDate),
					lte(entries.date, endDate)
				)
			)
			.all();
		for (const r of rows) if (r.date) symptomDates.add(r.date);
	}

	return json(
		envRows.map((r) => ({
			date: r.date,
			delta: r.delta,
			hasSymptom: symptomDates.has(r.date)
		}))
	);
}

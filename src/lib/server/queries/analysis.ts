import { eq, and, gte, lte, isNotNull } from 'drizzle-orm';
import { db } from '../../db/index.js';
import {
	entries,
	entryTypeLogs,
	entryTriggers,
	triggers,
	environmentalData,
	medicationChanges
} from '../../db/schema.js';
import type {
	MonthlyStats,
	CorrelationResult,
	EnvironmentalCorrelation
} from '../../types/index.js';

// ─── Monthly Stats ─────────────────────────────────────────────────────────────

export function getMonthlyStats(
	entryTypeId: string,
	yearMonth: string,
	startDateOverride?: string,
	endDateOverride?: string
): MonthlyStats {
	const start = startDateOverride ?? `${yearMonth}-01`;
	const end = endDateOverride ?? `${yearMonth}-31`;

	const logs = db
		.select({
			date: entries.date,
			intensity: entryTypeLogs.intensityCache,
			startTime: entryTypeLogs.startTime,
			endTime: entryTypeLogs.endTime
		})
		.from(entryTypeLogs)
		.leftJoin(entries, eq(entryTypeLogs.entryId, entries.id))
		.where(
			and(
				eq(entryTypeLogs.entryTypeId, entryTypeId),
				gte(entries.date, start),
				lte(entries.date, end)
			)
		)
		.all();

	const frequency = logs.length;
	const intensities = logs.map((l) => l.intensity).filter((v): v is number => v !== null);
	const avgIntensity = intensities.length ? intensities.reduce((a, b) => a + b, 0) / intensities.length : null;

	const durations = logs
		.filter((l) => l.startTime && l.endTime)
		.map((l) => {
			const ms = new Date(l.endTime!).getTime() - new Date(l.startTime).getTime();
			return ms / 60000; // to minutes
		})
		.filter((d) => d > 0);
	const avgDurationMinutes =
		durations.length ? durations.reduce((a, b) => a + b, 0) / durations.length : null;

	const occurrenceDates = [...new Set(logs.map((l) => l.date).filter((d): d is string => !!d))];

	return { entryTypeId, month: yearMonth, frequency, avgIntensity, avgDurationMinutes, occurrenceDates };
}

// ─── Trigger Correlations ─────────────────────────────────────────────────────

export function getTriggerCorrelations(
	entryTypeId: string,
	startDate: string,
	endDate: string
): CorrelationResult[] {
	// Get all entry dates in range
	const allEntryDates = db
		.select({ date: entries.date })
		.from(entries)
		.where(and(gte(entries.date, startDate), lte(entries.date, endDate)))
		.all()
		.map((r) => r.date);

	if (allEntryDates.length === 0) return [];

	// Get dates where symptom was present
	const symptomDates = new Set(
		db
			.select({ date: entries.date })
			.from(entryTypeLogs)
			.leftJoin(entries, eq(entryTypeLogs.entryId, entries.id))
			.where(
				and(
					eq(entryTypeLogs.entryTypeId, entryTypeId),
					gte(entries.date, startDate),
					lte(entries.date, endDate)
				)
			)
			.all()
			.map((r) => r.date)
			.filter((d): d is string => !!d)
	);

	// Get avg intensity on symptom days
	const intensityByDate = new Map<string, number>();
	db
		.select({ date: entries.date, intensity: entryTypeLogs.intensityCache })
		.from(entryTypeLogs)
		.leftJoin(entries, eq(entryTypeLogs.entryId, entries.id))
		.where(
			and(
				eq(entryTypeLogs.entryTypeId, entryTypeId),
				gte(entries.date, startDate),
				lte(entries.date, endDate),
				isNotNull(entryTypeLogs.intensityCache)
			)
		)
		.all()
		.forEach((r) => {
			if (r.date && r.intensity !== null) intensityByDate.set(r.date, r.intensity);
		});

	// Get all triggers with their active dates
	const allTriggers = db.select().from(triggers).all();
	const results: CorrelationResult[] = [];

	for (const trigger of allTriggers) {
		const triggerDates = new Set(
			db
				.select({ date: entries.date })
				.from(entryTriggers)
				.leftJoin(entries, eq(entryTriggers.entryId, entries.id))
				.where(
					and(
						eq(entryTriggers.triggerId, trigger.id),
						gte(entries.date, startDate),
						lte(entries.date, endDate)
					)
				)
				.all()
				.map((r) => r.date)
				.filter((d): d is string => !!d)
		);

		if (triggerDates.size === 0) continue;

		// 2×2 contingency
		let both = 0, triggerOnly = 0, symptomOnly = 0, neither = 0;
		const intensityWith: number[] = [];
		const intensityWithout: number[] = [];

		for (const date of allEntryDates) {
			const hasSymptom = symptomDates.has(date);
			const hasTrigger = triggerDates.has(date);
			if (hasSymptom && hasTrigger) {
				both++;
				const i = intensityByDate.get(date);
				if (i !== undefined) intensityWith.push(i);
			} else if (!hasSymptom && hasTrigger) {
				triggerOnly++;
			} else if (hasSymptom && !hasTrigger) {
				symptomOnly++;
				const i = intensityByDate.get(date);
				if (i !== undefined) intensityWithout.push(i);
			} else {
				neither++;
			}
		}

		if (both === 0) continue;

		const phi = phiCoefficient(both, triggerOnly, symptomOnly, neither);

		results.push({
			triggerId: trigger.id,
			triggerName: trigger.name,
			triggerCategory: trigger.category,
			coOccurrences: both,
			daysWithTrigger: both + triggerOnly,
			daysWithSymptom: both + symptomOnly,
			totalDays: allEntryDates.length,
			phi,
			strength: phi > 0.4 ? 'strong' : phi > 0.2 ? 'possible' : 'none',
			avgIntensityWith: intensityWith.length ? avg(intensityWith) : null,
			avgIntensityWithout: intensityWithout.length ? avg(intensityWithout) : null
		});
	}

	return results.sort((a, b) => b.phi - a.phi);
}

// ─── Environmental Correlations ───────────────────────────────────────────────

export function getEnvironmentalCorrelations(
	entryTypeId: string,
	startDate: string,
	endDate: string
): EnvironmentalCorrelation[] {
	const envRows = db
		.select()
		.from(environmentalData)
		.where(and(gte(environmentalData.date, startDate), lte(environmentalData.date, endDate)))
		.all();

	if (envRows.length < 4) return [];

	const symptomDates = new Set(
		db
			.select({ date: entries.date })
			.from(entryTypeLogs)
			.leftJoin(entries, eq(entryTypeLogs.entryId, entries.id))
			.where(
				and(
					eq(entryTypeLogs.entryTypeId, entryTypeId),
					gte(entries.date, startDate),
					lte(entries.date, endDate)
				)
			)
			.all()
			.map((r) => r.date)
			.filter((d): d is string => !!d)
	);

	const intensityByDate = new Map<string, number>();
	db
		.select({ date: entries.date, intensity: entryTypeLogs.intensityCache })
		.from(entryTypeLogs)
		.leftJoin(entries, eq(entryTypeLogs.entryId, entries.id))
		.where(
			and(
				eq(entryTypeLogs.entryTypeId, entryTypeId),
				gte(entries.date, startDate),
				lte(entries.date, endDate),
				isNotNull(entryTypeLogs.intensityCache)
			)
		)
		.all()
		.forEach((r) => {
			if (r.date && r.intensity !== null) intensityByDate.set(r.date, r.intensity);
		});

	const envVariables: Array<{
		key: keyof typeof envRows[0];
		label: string;
		variable: string;
	}> = [
		{ key: 'pressureDeltaHpa', label: 'Luftdruckabfall', variable: 'pressure_delta' },
		{ key: 'uvIndex', label: 'UV-Index', variable: 'uv_index' }
	];

	const results: EnvironmentalCorrelation[] = [];

	for (const { key, label, variable } of envVariables) {
		const values = envRows
			.map((r) => ({ date: r.date, value: r[key] as number | null }))
			.filter((r): r is { date: string; value: number } => r.value !== null);

		if (values.length < 4) continue;

		// Bin at median
		const sorted = [...values].sort((a, b) => a.value - b.value);
		const median = sorted[Math.floor(sorted.length / 2)].value;

		// For pressure delta, "high" means a strong drop below the median.
		const highDates = new Set(
			variable === 'pressure_delta'
				? values.filter((v) => v.value < median).map((v) => v.date)
				: values.filter((v) => v.value >= median).map((v) => v.date)
		);

		let both = 0, highOnly = 0, symptomOnly = 0, neither = 0;
		const intensityHigh: number[] = [];
		const intensityLow: number[] = [];
		const allDates = values.map((v) => v.date);

		for (const date of allDates) {
			const hasSymptom = symptomDates.has(date);
			const isHigh = highDates.has(date);
			if (hasSymptom && isHigh) {
				both++;
				const i = intensityByDate.get(date);
				if (i !== undefined) intensityHigh.push(i);
			} else if (!hasSymptom && isHigh) {
				highOnly++;
			} else if (hasSymptom && !isHigh) {
				symptomOnly++;
				const i = intensityByDate.get(date);
				if (i !== undefined) intensityLow.push(i);
			} else {
				neither++;
			}
		}

		if (both === 0) continue;

		const phi = phiCoefficient(both, highOnly, symptomOnly, neither);

		results.push({
			variable,
			label,
			phi,
			strength: phi > 0.4 ? 'strong' : phi > 0.2 ? 'possible' : 'none',
			avgIntensityHigh: intensityHigh.length ? avg(intensityHigh) : null,
			avgIntensityLow: intensityLow.length ? avg(intensityLow) : null
		});
	}

	return results.sort((a, b) => b.phi - a.phi);
}

// ─── Medication Change Flags ───────────────────────────────────────────────────

export function getMedicationFlags(
	startDate: string,
	endDate: string
): Array<{ changeDate: string; description: string }> {
	const changes = db
		.select()
		.from(medicationChanges)
		.where(and(gte(medicationChanges.changeDate, startDate), lte(medicationChanges.changeDate, endDate)))
		.all();

	return changes.map((c) => ({
		changeDate: c.changeDate,
		description: `${c.changeType}: ${c.newDosage ?? c.oldDosage ?? 'unknown dosage'}`
	}));
}

// ─── Math Helpers ─────────────────────────────────────────────────────────────

function phiCoefficient(a: number, b: number, c: number, d: number): number {
	const numerator = a * d - b * c;
	const denominator = Math.sqrt((a + b) * (c + d) * (a + c) * (b + d));
	return denominator === 0 ? 0 : numerator / denominator;
}

function avg(nums: number[]): number {
	return nums.reduce((a, b) => a + b, 0) / nums.length;
}

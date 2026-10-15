import { and, eq, ne, sql } from 'drizzle-orm';
import type { AppDatabase } from './db.js';
import * as schema from '../db/schema.js';

const BUILT_IN_ENTRY_TYPES = [
	{
		id: 'builtin-headache',
		name: 'Headache',
		icon: 'H',
		color: '#3b82f6',
		category: 'symptom',
		sortOrder: 0,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'intensity',
					label: 'Intensity',
					type: 'slider',
					required: true,
					config: { min: 1, max: 5, step: 1 }
				},
				{
					key: 'location',
					label: 'Where it hurts',
					type: 'select',
					required: false,
					config: {
						extendable: true,
						options: [
							'temples',
							'forehead',
							'back of head',
							'left side',
							'right side',
							'all over',
							'behind the eye',
							'neck'
						]
					}
				},
				{
					key: 'side_effects',
					label: 'Symptoms',
					type: 'checklist',
					required: false,
					config: {
						extendable: true,
						options: [
							'aura',
							'light sensitivity',
							'sound sensitivity',
							'nausea',
							'vomiting',
							'neck stiffness',
							'dizziness',
							'eye pain',
							'facial pain',
							'sinus pressure',
							'brain fog',
							'fatigue'
						]
					}
				},
				{
					key: 'notes',
					label: 'Notes',
					type: 'textarea',
					required: false,
					config: { placeholder: 'Additional notes...' }
				}
			]
		})
	},
	{
		id: 'builtin-stomachache',
		name: 'Stomachache',
		icon: 'S',
		color: '#a855f7',
		category: 'symptom',
		sortOrder: 1,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'food_intake',
					label: 'What did you eat before this started?',
					type: 'textarea',
					required: false,
					config: { placeholder: 'Describe recent food intake...' }
				},
				{
					key: 'intensity',
					label: 'Intensity',
					type: 'slider',
					required: true,
					config: { min: 1, max: 5, step: 1 }
				},
				{
					key: 'symptoms',
					label: 'Symptoms',
					type: 'checklist',
					required: false,
					config: {
						extendable: true,
						options: [
							'nausea',
							'cramps',
							'bloating',
							'reflux',
							'heartburn',
							'diarrhea',
							'constipation',
							'vomiting',
							'loss of appetite',
							'belching'
						]
					}
				},
				{
					key: 'notes',
					label: 'Notes',
					type: 'textarea',
					required: false,
					config: { placeholder: 'Additional notes...' }
				}
			]
		})
	},
	{
		id: 'builtin-allergy',
		name: 'Allergy / hay fever',
		icon: 'A',
		color: '#22c55e',
		category: 'symptom',
		sortOrder: 2,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'intensity',
					label: 'Intensity',
					type: 'slider',
					required: true,
					config: { min: 1, max: 5, step: 1 }
				},
				{
					key: 'symptoms',
					label: 'Symptoms',
					type: 'checklist',
					required: false,
					config: {
						extendable: true,
						options: [
							'sneezing',
							'itchy eyes',
							'runny nose',
							'hives',
							'skin rash',
							'shortness of breath',
							'itchy skin',
							'swollen eyes',
							'scratchy throat',
							'coughing',
							'ear pressure'
						]
					}
				},
				{
					key: 'notes',
					label: 'Notes',
					type: 'textarea',
					required: false,
					config: { placeholder: 'Additional notes...' }
				}
			]
		})
	},
	{
		// The id stays from the time this type was "Low blood sugar", so history is kept.
		id: 'builtin-hypo',
		name: 'Blood sugar',
		icon: '!',
		color: '#f59e0b',
		category: 'event',
		sortOrder: 3,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'direction',
					label: 'Blood sugar',
					type: 'select',
					required: true,
					config: { options: ['too low', 'too high'] }
				},
				{
					key: 'blood_glucose',
					label: 'Blood sugar reading',
					type: 'number',
					required: false,
					config: { units: ['mg/dL', 'mmol/L'] }
				},
				{
					key: 'intensity',
					label: 'Intensity',
					type: 'slider',
					required: true,
					config: { min: 1, max: 5, step: 1 }
				},
				{
					key: 'symptoms',
					label: 'Symptoms',
					type: 'checklist',
					required: false,
					config: {
						extendable: true,
						options: [
							'shaking',
							'sweating',
							'confusion',
							'dizziness',
							'fatigue',
							'palpitations',
							'irritability',
							'hunger',
							'thirst',
							'frequent urination',
							'dry mouth',
							'blurred vision',
							'pale skin',
							'headache'
						]
					}
				},
				{
					key: 'notes',
					label: 'Notes',
					type: 'textarea',
					required: false,
					config: { placeholder: 'Additional notes...' }
				}
			]
		})
	},
	{
		id: 'builtin-period',
		name: 'Period',
		icon: 'P',
		color: '#ec4899',
		category: 'symptom',
		sortOrder: 5,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'type',
					label: 'Type',
					type: 'select',
					required: true,
					config: { options: ['bleeding', 'spotting'] }
				},
				{
					key: 'flow',
					label: 'Flow',
					type: 'slider',
					required: false,
					config: { min: 1, max: 5, step: 1 }
				},
				{
					key: 'symptoms',
					label: 'Symptoms',
					type: 'checklist',
					required: false,
					config: {
						extendable: true,
						options: [
							'cramps',
							'bloating',
							'mood changes',
							'fatigue',
							'back pain',
							'headache',
							'breast tenderness',
							'acne'
						]
					}
				},
				{
					key: 'notes',
					label: 'Notes',
					type: 'textarea',
					required: false,
					config: { placeholder: 'Additional notes...' }
				}
			]
		})
	},
	{
		id: 'builtin-meal',
		name: 'Meal',
		icon: 'M',
		color: '#e07a5f',
		category: 'meal',
		sortOrder: 6,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'meal',
					label: 'Meal',
					type: 'select',
					required: false,
					config: { options: ['breakfast', 'lunch', 'dinner', 'snack'] }
				},
				{
					key: 'foods',
					label: 'Foods',
					type: 'checklist',
					required: false,
					config: {
						extendable: true,
						options: [
							'bread',
							'pasta',
							'dairy',
							'cheese',
							'meat',
							'fish',
							'fruit',
							'vegetables',
							'sweets',
							'chocolate',
							'coffee',
							'alcohol',
							'fast food'
						]
					}
				},
				{
					key: 'what',
					label: 'What exactly?',
					type: 'text',
					required: false,
					config: { placeholder: 'e.g. pasta with tomato sauce' }
				}
			]
		})
	},
	{
		id: 'builtin-note',
		name: 'Note',
		icon: 'N',
		color: '#94a3b8',
		category: 'note',
		sortOrder: 7,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'notes',
					label: 'Note',
					type: 'textarea',
					required: true,
					config: { placeholder: 'Write your note...' }
				}
			]
		})
	}
];

const BUILT_IN_TRIGGERS = [
	// Food
	{ id: 'trig-gluten', name: 'Gluten', category: 'food' },
	{ id: 'trig-dairy', name: 'Dairy', category: 'food' },
	{ id: 'trig-alcohol', name: 'Alcohol', category: 'food' },
	{ id: 'trig-caffeine', name: 'Caffeine', category: 'food' },
	{ id: 'trig-sugar', name: 'Sugar', category: 'food' },
	{ id: 'trig-processed', name: 'Processed food', category: 'food' },
	{ id: 'trig-spicy', name: 'Spicy food', category: 'food' },
	{ id: 'trig-nuts', name: 'Nuts', category: 'food' },
	{ id: 'trig-shellfish', name: 'Shellfish', category: 'food' },
	// Environment
	{ id: 'trig-weather-change', name: 'Weather change', category: 'environment' },
	{ id: 'trig-heat', name: 'Heat', category: 'environment' },
	{ id: 'trig-cold', name: 'Cold', category: 'environment' },
	{ id: 'trig-mold', name: 'Mold', category: 'environment' },
	{ id: 'trig-animal', name: 'Animal hair', category: 'environment' },
	{ id: 'trig-dust', name: 'Dust', category: 'environment' },
	{ id: 'trig-perfume', name: 'Perfume / chemicals', category: 'environment' },
	// Activity
	{ id: 'trig-stress', name: 'Stress', category: 'activity' },
	{ id: 'trig-poor-sleep', name: 'Poor sleep', category: 'activity' },
	{ id: 'trig-exercise', name: 'Exercise', category: 'activity' },
	{ id: 'trig-screen', name: 'Screen time', category: 'activity' },
	{ id: 'trig-travel', name: 'Travel', category: 'activity' },
	{ id: 'trig-dehydration', name: 'Dehydration', category: 'activity' },
	{ id: 'trig-skipped-meal', name: 'Skipped meal', category: 'activity' },
	// Medication
	{ id: 'trig-medication-change', name: 'Medication change', category: 'medication' }
];

export function seedBuiltIns(db: AppDatabase) {
	// Seed entry types: insert if missing, always refresh built-in data
	for (const type of BUILT_IN_ENTRY_TYPES) {
		const existing = db
			.select({ id: schema.entryTypes.id })
			.from(schema.entryTypes)
			.where(eq(schema.entryTypes.id, type.id))
			.get();
		if (!existing) {
			db.insert(schema.entryTypes).values({ ...type, isBuiltIn: true }).run();
		} else {
			// Refresh built-in presentation and schema so updates deploy automatically.
			// A renamed built-in keeps its old name if a custom type already has the new one.
			const nameTaken = db
				.select({ id: schema.entryTypes.id })
				.from(schema.entryTypes)
				.where(and(eq(schema.entryTypes.name, type.name), ne(schema.entryTypes.id, type.id)))
				.get();
			db.update(schema.entryTypes)
				.set({
					...(!nameTaken && { name: type.name }),
					icon: type.icon,
					color: type.color,
					category: type.category,
					sortOrder: type.sortOrder,
					metricSchema: type.metricSchema
				})
				.where(eq(schema.entryTypes.id, type.id))
				.run();
		}
	}

	upgradeLogValues(db);
	addBloodSugarDirection(db);
	periodFlowToScale(db);

	// Merge accidental custom period duplicates into the built-in type so old logs survive.
	const duplicatePeriods = db
		.select({ id: schema.entryTypes.id })
		.from(schema.entryTypes)
		.where(
			sql`lower(${schema.entryTypes.name}) = 'period' and ${schema.entryTypes.isBuiltIn} = 0`
		)
		.all();

	for (const duplicate of duplicatePeriods) {
		db.update(schema.entryTypeLogs)
			.set({ entryTypeId: 'builtin-period' })
			.where(eq(schema.entryTypeLogs.entryTypeId, duplicate.id))
			.run();

		db.delete(schema.entryTypes).where(eq(schema.entryTypes.id, duplicate.id)).run();
	}

	// Seed triggers
	for (const trigger of BUILT_IN_TRIGGERS) {
		const existing = db
			.select({ id: schema.triggers.id })
			.from(schema.triggers)
			.where(eq(schema.triggers.id, trigger.id))
			.get();
		if (!existing) {
			db.insert(schema.triggers).values({ ...trigger, isBuiltIn: true }).run();
		}
	}

	// An early pollen trigger, replaced by measured pollen in the weather data.
	db.delete(schema.triggers).where(eq(schema.triggers.id, 'trig-pollen')).run();

	medicationChangeLogsToTriggers(db);

	// Seed default profile row
	const profile = db
		.select({ id: schema.userProfile.id })
		.from(schema.userProfile)
		.where(eq(schema.userProfile.id, 'default'))
		.get();
	if (!profile) {
		db.insert(schema.userProfile).values({ id: 'default' }).run();
	}
}

/** Older 'severity' values (allergy 1 to 5, low blood sugar mild/moderate/severe) become 'intensity' 1 to 5. */
const SEVERITY_WORDS: Record<string, number> = { mild: 2, moderate: 3, severe: 4 };

/** Low blood sugar became Blood sugar with a direction; older logs were all "too low". */
function addBloodSugarDirection(db: AppDatabase) {
	const rows = db
		.select({ id: schema.entryTypeLogs.id, metricValues: schema.entryTypeLogs.metricValues })
		.from(schema.entryTypeLogs)
		.where(eq(schema.entryTypeLogs.entryTypeId, 'builtin-hypo'))
		.all();
	for (const row of rows) {
		const values = JSON.parse(row.metricValues) as Record<string, unknown>;
		if ('direction' in values) continue;
		db.update(schema.entryTypeLogs)
			.set({ metricValues: JSON.stringify({ direction: 'too low', ...values }) })
			.where(eq(schema.entryTypeLogs.id, row.id))
			.run();
	}
}

/** A period's flow was light, medium or heavy; it is a 1 to 5 scale now. */
const FLOW_WORDS: Record<string, number> = { light: 2, medium: 3, heavy: 4 };

function periodFlowToScale(db: AppDatabase) {
	const rows = db
		.select({ id: schema.entryTypeLogs.id, metricValues: schema.entryTypeLogs.metricValues })
		.from(schema.entryTypeLogs)
		.where(and(eq(schema.entryTypeLogs.entryTypeId, 'builtin-period'), sql`${schema.entryTypeLogs.metricValues} like '%"flow":"%'`))
		.all();
	for (const row of rows) {
		const values = JSON.parse(row.metricValues) as Record<string, unknown>;
		if (typeof values.flow !== 'string') continue;
		const { flow, ...rest } = values;
		const level = FLOW_WORDS[flow];
		db.update(schema.entryTypeLogs)
			.set({ metricValues: JSON.stringify(level ? { ...rest, flow: level } : rest) })
			.where(eq(schema.entryTypeLogs.id, row.id))
			.run();
	}
}

function upgradeLogValues(db: AppDatabase) {
	const rows = db
		.select({ id: schema.entryTypeLogs.id, metricValues: schema.entryTypeLogs.metricValues })
		.from(schema.entryTypeLogs)
		.where(sql`${schema.entryTypeLogs.metricValues} like '%"severity"%'`)
		.all();
	for (const row of rows) {
		const values = JSON.parse(row.metricValues) as Record<string, unknown>;
		if (!('severity' in values)) continue;
		const { severity, ...rest } = values;
		const intensity = typeof severity === 'number' ? severity : typeof severity === 'string' ? (SEVERITY_WORDS[severity] ?? null) : null;
		const next = intensity === null || 'intensity' in rest ? rest : { ...rest, intensity };
		db.update(schema.entryTypeLogs)
			.set({ metricValues: JSON.stringify(next), intensityCache: (next.intensity as number | undefined) ?? null })
			.where(eq(schema.entryTypeLogs.id, row.id))
			.run();
	}
}

/**
 * Medication change used to be an entry type. Its logs become the trigger
 * 'Medication change' on their day, with the details as the trigger's note,
 * and the old type is removed.
 */
function medicationChangeLogsToTriggers(db: AppDatabase) {
	const OLD_TYPE = 'builtin-medication-change';
	const logs = db.select().from(schema.entryTypeLogs).where(eq(schema.entryTypeLogs.entryTypeId, OLD_TYPE)).all();
	for (const log of logs) {
		const v = JSON.parse(log.metricValues) as Record<string, string | undefined>;
		const dose = [v.old_dosage, v.new_dosage].filter(Boolean).join(' → ');
		const note = [v.medication_name, v.change_type, dose, v.reason, v.notes].filter(Boolean).join(', ') || null;
		const linked = db
			.select({ id: schema.entryTriggers.id })
			.from(schema.entryTriggers)
			.where(and(eq(schema.entryTriggers.entryId, log.entryId), eq(schema.entryTriggers.triggerId, 'trig-medication-change')))
			.get();
		if (!linked) {
			db.insert(schema.entryTriggers)
				.values({ id: `medchange-${log.id}`, entryId: log.entryId, triggerId: 'trig-medication-change', note })
				.run();
		}
		db.delete(schema.entryTypeLogs).where(eq(schema.entryTypeLogs.id, log.id)).run();
	}
	db.delete(schema.entryTypes).where(eq(schema.entryTypes.id, OLD_TYPE)).run();
}

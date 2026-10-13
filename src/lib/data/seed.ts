import { eq, sql } from 'drizzle-orm';
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
					label: 'Location',
					type: 'select',
					required: false,
					config: {
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
		name: 'Allergy / Hayfever',
		icon: 'A',
		color: '#22c55e',
		category: 'symptom',
		sortOrder: 2,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'severity',
					label: 'Severity',
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
		id: 'builtin-hypo',
		name: 'Hypo',
		icon: '!',
		color: '#f59e0b',
		category: 'event',
		sortOrder: 3,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'severity',
					label: 'Severity',
					type: 'select',
					required: true,
					config: { options: ['mild', 'moderate', 'severe'] }
				},
				{
					key: 'symptoms',
					label: 'Symptoms',
					type: 'checklist',
					required: false,
					config: {
						options: [
							'shaking',
							'sweating',
							'confusion',
							'dizziness',
							'fatigue',
							'palpitations',
							'irritability',
							'hunger',
							'blurred vision',
							'pale skin',
							'headache'
						]
					}
				},
				{
					key: 'blood_glucose',
					label: 'Blood Glucose Reading',
					type: 'text',
					required: false,
					config: { placeholder: 'e.g. 3.2 mmol/L' }
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
		id: 'builtin-medication-change',
		name: 'Medication Change',
		icon: 'Rx',
		color: '#6b7280',
		category: 'event',
		sortOrder: 4,
		metricSchema: JSON.stringify({
			fields: [
				{
					key: 'change_type',
					label: 'Change Type',
					type: 'select',
					required: true,
					config: { options: ['started', 'dosage changed', 'stopped'] }
				},
				{
					key: 'medication_name',
					label: 'Medication',
					type: 'text',
					required: true,
					config: { placeholder: 'Medication name' }
				},
				{
					key: 'old_dosage',
					label: 'Previous Dosage',
					type: 'text',
					required: false,
					config: { placeholder: 'e.g. 10mg' }
				},
				{
					key: 'new_dosage',
					label: 'New Dosage',
					type: 'text',
					required: false,
					config: { placeholder: 'e.g. 20mg' }
				},
				{
					key: 'reason',
					label: 'Reason',
					type: 'text',
					required: false,
					config: { placeholder: 'Why was this changed?' }
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
					type: 'select',
					required: false,
					config: { options: ['light', 'medium', 'heavy'] }
				},
				{
					key: 'symptoms',
					label: 'Symptoms',
					type: 'checklist',
					required: false,
					config: {
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
		id: 'builtin-note',
		name: 'General Note',
		icon: 'N',
		color: '#94a3b8',
		category: 'note',
		sortOrder: 6,
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
	{ id: 'trig-skipped-meal', name: 'Skipped meal', category: 'activity' }
];

export function seedBuiltIns(db: AppDatabase) {
	// Seed entry types — insert if missing, always refresh built-in data
	for (const type of BUILT_IN_ENTRY_TYPES) {
		const existing = db
			.select({ id: schema.entryTypes.id })
			.from(schema.entryTypes)
			.where(eq(schema.entryTypes.id, type.id))
			.get();
		if (!existing) {
			db.insert(schema.entryTypes).values({ ...type, isBuiltIn: true }).run();
		} else {
			// Refresh built-in presentation and schema so updates deploy automatically
			db.update(schema.entryTypes)
				.set({
					name: type.name,
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

	// Remove pollen trigger (user doesn't want pollen data)
	db.delete(schema.triggers).where(eq(schema.triggers.id, 'trig-pollen')).run();

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

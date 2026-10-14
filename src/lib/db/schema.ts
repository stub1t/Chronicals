import { sqliteTable, text, integer, real, index, unique } from 'drizzle-orm/sqlite-core';
import { sql } from 'drizzle-orm';

// ─── Diary Entries ────────────────────────────────────────────────────────────

export const entries = sqliteTable('entries', {
	id: text('id').primaryKey(),
	date: text('date').notNull(), // 'YYYY-MM-DD'
	note: text('note'),
	wellbeing: integer('wellbeing'), // 1 (awful) to 5 (great), the daily "How are you?"
	createdAt: text('created_at').notNull().default(sql`(datetime('now'))`),
	updatedAt: text('updated_at').notNull().default(sql`(datetime('now'))`)
}, (t) => [
	index('entries_date_idx').on(t.date)
]);

// ─── Entry Type Registry ──────────────────────────────────────────────────────
// metricSchema JSON shape:
// { fields: Array<{ key, label, type: 'slider'|'select'|'checklist'|'textarea'|'text', required, config: { min?, max?, step?, options?, placeholder? } }> }

export const entryTypes = sqliteTable('entry_types', {
	id: text('id').primaryKey(),
	name: text('name').notNull().unique(),
	icon: text('icon'), // emoji or 'lucide:<key>'
	color: text('color'), // hex, e.g. '#3b82f6'
	metricSchema: text('metric_schema').notNull(), // JSON
	category: text('category').notNull().default('symptom'), // 'symptom' | 'event' | 'note' | 'meal'
	sortOrder: integer('sort_order').notNull().default(0),
	isBuiltIn: integer('is_built_in', { mode: 'boolean' }).notNull().default(false),
	/** Hidden types get no tile on the day screens; their past logs stay. */
	hidden: integer('hidden', { mode: 'boolean' }).notNull().default(false),
	/** JSON { fieldKey: string[] }: options the user added; the seed never touches it. */
	extraOptions: text('extra_options'),
	createdAt: text('created_at').notNull().default(sql`(datetime('now'))`)
});

// ─── Entry Type Logs ──────────────────────────────────────────────────────────

export const entryTypeLogs = sqliteTable('entry_type_logs', {
	id: text('id').primaryKey(),
	entryId: text('entry_id').notNull().references(() => entries.id, { onDelete: 'cascade' }),
	entryTypeId: text('entry_type_id').notNull().references(() => entryTypes.id),
	startTime: text('start_time').notNull(), // ISO datetime
	endTime: text('end_time'), // ISO datetime, null if ongoing
	metricValues: text('metric_values').notNull().default('{}'), // JSON
	intensityCache: real('intensity_cache'), // denormalized intensity 1-5 for fast queries
	createdAt: text('created_at').notNull().default(sql`(datetime('now'))`)
}, (t) => [
	index('etl_entry_idx').on(t.entryId),
	index('etl_type_idx').on(t.entryTypeId),
	index('etl_start_idx').on(t.startTime)
]);

// ─── Triggers ─────────────────────────────────────────────────────────────────

export const triggers = sqliteTable('triggers', {
	id: text('id').primaryKey(),
	name: text('name').notNull(),
	category: text('category').notNull(), // 'food' | 'environment' | 'activity' | 'medication' | 'custom'
	isBuiltIn: integer('is_built_in', { mode: 'boolean' }).notNull().default(false),
	/** Deleted by the user but kept, so past days keep it and the seed does not bring it back. */
	hidden: integer('hidden', { mode: 'boolean' }).notNull().default(false),
	createdAt: text('created_at').notNull().default(sql`(datetime('now'))`)
}, (t) => [
	index('triggers_category_idx').on(t.category),
	unique('triggers_name_unique').on(t.name)
]);

// ─── Entry ↔ Trigger Join ─────────────────────────────────────────────────────

export const entryTriggers = sqliteTable('entry_triggers', {
	id: text('id').primaryKey(),
	entryId: text('entry_id').notNull().references(() => entries.id, { onDelete: 'cascade' }),
	triggerId: text('trigger_id').notNull().references(() => triggers.id, { onDelete: 'cascade' }),
	note: text('note')
}, (t) => [
	index('et_entry_idx').on(t.entryId),
	index('et_trigger_idx').on(t.triggerId),
	unique('et_unique_pair').on(t.entryId, t.triggerId)
]);

// ─── Environmental Data ───────────────────────────────────────────────────────

export const environmentalData = sqliteTable('environmental_data', {
	id: text('id').primaryKey(),
	date: text('date').notNull().unique(), // 'YYYY-MM-DD'
	temperatureC: real('temperature_c'),
	airPressureHpa: real('air_pressure_hpa'),
	pressureDeltaHpa: real('pressure_delta_hpa'), // change from previous day
	weatherCondition: text('weather_condition'),
	pollenGrass: real('pollen_grass'),
	pollenBirch: real('pollen_birch'),
	pollenAlder: real('pollen_alder'),
	pollenMugwort: real('pollen_mugwort'),
	uvIndex: real('uv_index'),
	fetchedAt: text('fetched_at').notNull().default(sql`(datetime('now'))`)
}, (t) => [
	index('env_date_idx').on(t.date)
]);

// ─── User Profile ─────────────────────────────────────────────────────────────

export const userProfile = sqliteTable('user_profile', {
	id: text('id').primaryKey().default('default'),
	displayName: text('display_name'),
	bloodType: text('blood_type'), // 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'
	locationLat: real('location_lat'),
	locationLon: real('location_lon'),
	locationName: text('location_name'),
	updatedAt: text('updated_at').notNull().default(sql`(datetime('now'))`)
});

// ─── Medications ──────────────────────────────────────────────────────────────

export const medications = sqliteTable('medications', {
	id: text('id').primaryKey(),
	name: text('name').notNull(),
	dosage: text('dosage'),
	unit: text('unit'), // 'mg', 'ml', 'tablets', etc.
	startDate: text('start_date').notNull(),
	endDate: text('end_date'),
	notes: text('notes'),
	isActive: integer('is_active', { mode: 'boolean' }).notNull().default(true),
	reminderTimes: text('reminder_times'), // JSON array of 'HH:MM', used by the Android app's reminders
	createdAt: text('created_at').notNull().default(sql`(datetime('now'))`)
});

export const medicationChanges = sqliteTable('medication_changes', {
	id: text('id').primaryKey(),
	medicationId: text('medication_id').references(() => medications.id, { onDelete: 'set null' }),
	changeType: text('change_type').notNull(), // 'started' | 'dosage_change' | 'stopped'
	oldDosage: text('old_dosage'),
	newDosage: text('new_dosage'),
	changeDate: text('change_date').notNull(),
	reason: text('reason'),
	notes: text('notes'),
	createdAt: text('created_at').notNull().default(sql`(datetime('now'))`)
}, (t) => [
	index('mc_date_idx').on(t.changeDate)
]);

// ─── Metric Schema Types ───────────────────────────────────────────────────────

export type MetricFieldType = 'slider' | 'select' | 'checklist' | 'textarea' | 'text' | 'number';

export interface MetricFieldConfig {
	min?: number;
	max?: number;
	step?: number;
	options?: string[];
	placeholder?: string;
	/** Number fields: units to pick from; the choice is stored under `<key>_unit`. */
	units?: string[];
	/** Select and checklist fields: whether "+ Own" may add options. */
	extendable?: boolean;
}

export interface MetricField {
	key: string;
	label: string;
	type: MetricFieldType;
	required: boolean;
	config: MetricFieldConfig;
}

export interface MetricSchema {
	fields: MetricField[];
}

// ─── Entry Types ──────────────────────────────────────────────────────────────

/** Symptoms and events have a duration; notes and meals are a moment. */
export type EntryCategory = 'symptom' | 'event' | 'note' | 'meal';

export interface EntryType {
	id: string;
	name: string;
	icon: string | null;
	color: string | null;
	metricSchema: MetricSchema;
	category: EntryCategory;
	sortOrder: number;
	isBuiltIn: boolean;
	/** No tile on the day screens; past logs stay. */
	hidden: boolean;
	/** Options the user added to select and checklist fields, by field key. */
	extraOptions: Record<string, string[]>;
}

// ─── Entry Type Logs ──────────────────────────────────────────────────────────

export interface EntryTypeLog {
	id: string;
	entryId: string;
	entryTypeId: string;
	entryType?: EntryType;
	startTime: string; // ISO datetime
	endTime: string | null;
	metricValues: Record<string, unknown>;
	intensityCache: number | null;
}

// ─── Entries ──────────────────────────────────────────────────────────────────

export interface Entry {
	id: string;
	date: string; // 'YYYY-MM-DD'
	note: string | null;
	/** Daily well-being, 1 (awful) to 5 (great). */
	wellbeing: number | null;
	/** Where the day was spent when away from home; null: the home place. */
	locationLat: number | null;
	locationLon: number | null;
	locationName: string | null;
	locationSource: 'auto' | 'manual' | null;
	createdAt: string;
	updatedAt: string;
	logs?: EntryTypeLog[];
	triggers?: TriggerRef[];
}

export interface TriggerRef {
	id: string;
	triggerId: string;
	triggerName: string;
	triggerCategory: string;
	note: string | null;
}

// ─── Triggers ─────────────────────────────────────────────────────────────────

export type TriggerCategory = 'food' | 'environment' | 'activity' | 'medication' | 'custom';

export interface Trigger {
	id: string;
	name: string;
	category: TriggerCategory;
	isBuiltIn: boolean;
	/** Deleted: no longer offered, but kept on the days that have it. */
	hidden: boolean;
}

// ─── Environmental Data ───────────────────────────────────────────────────────

export type PollenLevel = 'LOW' | 'MODERATE' | 'HIGH' | null;

export interface PollenStatus {
	grass: PollenLevel;
	birch: PollenLevel;
	alder: PollenLevel;
	mugwort: PollenLevel;
}

export interface EnvironmentalData {
	id: string;
	date: string;
	temperatureC: number | null;
	airPressureHpa: number | null;
	pressureDeltaHpa: number | null;
	weatherCondition: string | null;
	pollenGrass: number | null;
	pollenBirch: number | null;
	pollenAlder: number | null;
	pollenMugwort: number | null;
	pollenOlive: number | null;
	pollenRagweed: number | null;
	uvIndex: number | null;
	/** The place the row was fetched for. */
	lat: number | null;
	lon: number | null;
	fetchedAt: string;
	// Computed
	pollenStatus?: PollenStatus;
}

// ─── User Profile ─────────────────────────────────────────────────────────────

export type BloodType = 'A+' | 'A-' | 'B+' | 'B-' | 'AB+' | 'AB-' | 'O+' | 'O-';

export interface UserProfile {
	id: string;
	displayName: string | null;
	bloodType: BloodType | null;
	locationLat: number | null;
	locationLon: number | null;
	locationName: string | null;
	/** Weather and pollen from Open-Meteo; nothing is sent while this is off. */
	weatherEnabled: boolean;
	updatedAt: string;
}

// ─── Medications ──────────────────────────────────────────────────────────────

export interface Medication {
	id: string;
	name: string;
	dosage: string | null;
	unit: string | null;
	startDate: string;
	endDate: string | null;
	notes: string | null;
	isActive: boolean;
	/** 'HH:MM' times for reminders in the Android app. */
	reminderTimes: string[];
	/** Entry types it is taken for, so the analysis compares it only there; empty: not said. */
	forTypes: string[];
}

export type MedicationChangeType = 'started' | 'dosage_change' | 'stopped';

export interface MedicationChange {
	id: string;
	medicationId: string | null;
	changeType: MedicationChangeType;
	oldDosage: string | null;
	newDosage: string | null;
	changeDate: string;
	reason: string | null;
	notes: string | null;
}

// ─── Theme ────────────────────────────────────────────────────────────────────

export type ThemePreference = 'system' | 'light' | 'dark';

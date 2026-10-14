// ─── Metric Schema Types ───────────────────────────────────────────────────────

export type MetricFieldType = 'slider' | 'select' | 'checklist' | 'textarea' | 'text';

export interface MetricFieldConfig {
	min?: number;
	max?: number;
	step?: number;
	options?: string[];
	placeholder?: string;
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

export type EntryCategory = 'symptom' | 'event' | 'note';

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

// ─── Calendar Dot Data ────────────────────────────────────────────────────────

export interface DayDot {
	color: string;
	intensity: number; // 1-5
	entryTypeId: string;
	entryTypeName: string;
	category: EntryCategory;
}

export interface CalendarDayData {
	date: string;
	dots: DayDot[];
	hasHypo: boolean;
	hasMedChange: boolean;
}

// ─── Triggers ─────────────────────────────────────────────────────────────────

export type TriggerCategory = 'food' | 'environment' | 'activity' | 'custom';

export interface Trigger {
	id: string;
	name: string;
	category: TriggerCategory;
	isBuiltIn: boolean;
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
	uvIndex: number | null;
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

// ─── Analysis ─────────────────────────────────────────────────────────────────

export interface MonthlyStats {
	entryTypeId: string;
	month: string; // 'YYYY-MM'
	frequency: number;
	avgIntensity: number | null;
	avgDurationMinutes: number | null;
	occurrenceDates: string[];
}

export interface CorrelationResult {
	triggerId: string;
	triggerName: string;
	triggerCategory: string;
	coOccurrences: number;
	daysWithTrigger: number;
	daysWithSymptom: number;
	totalDays: number;
	phi: number;
	strength: 'none' | 'possible' | 'strong';
	avgIntensityWith: number | null;
	avgIntensityWithout: number | null;
}

export interface EnvironmentalCorrelation {
	variable: string; // 'pressure_delta' | 'pollen_grass' | 'pollen_birch' | 'uv_index'
	label: string;
	phi: number;
	strength: 'none' | 'possible' | 'strong';
	avgIntensityHigh: number | null;
	avgIntensityLow: number | null;
}

// ─── Theme ────────────────────────────────────────────────────────────────────

export type ThemePreference = 'system' | 'light' | 'dark';

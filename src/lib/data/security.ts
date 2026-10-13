import { error, type RequestEvent } from '@sveltejs/kit';
import type { BloodType, EntryCategory, MedicationChangeType, TriggerCategory } from '$lib/types/index.js';

const MAX_JSON_BODY_BYTES = 32 * 1024;
const MAX_TEXT_LENGTH = 4000;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const YEAR_MONTH_RE = /^\d{4}-\d{2}$/;
const ISO_DATETIME_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/;
const IDENTIFIER_RE = /^[A-Za-z0-9_-]{1,128}$/;
const COLOR_RE = /^#[0-9a-fA-F]{6}$/;

const BLOOD_TYPES = new Set<BloodType>(['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']);
const ENTRY_CATEGORIES = new Set<EntryCategory>(['symptom', 'event', 'note']);
const TRIGGER_CATEGORIES = new Set<TriggerCategory>(['food', 'environment', 'activity', 'custom']);
const MEDICATION_CHANGE_TYPES = new Set<MedicationChangeType>(['started', 'dosage_change', 'stopped']);

function isPlainObject(value: unknown): value is Record<string, unknown> {
	return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function validateDateString(value: string): void {
	const date = new Date(`${value}T00:00:00Z`);
	if (Number.isNaN(date.getTime())) {
		throw error(400, 'Invalid date');
	}
}

export function enforceTrustedMutation(event: RequestEvent): void {
	if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(event.request.method)) return;

	const secFetchSite = event.request.headers.get('sec-fetch-site');
	if (secFetchSite && !['same-origin', 'same-site', 'none'].includes(secFetchSite)) {
		throw error(403, 'Cross-site request blocked');
	}

	const origin = event.request.headers.get('origin');
	if (origin && origin !== event.url.origin) {
		throw error(403, 'Cross-origin request blocked');
	}
}

export async function parseJsonRequest<T extends Record<string, unknown>>(
	event: Pick<RequestEvent, 'request'>
): Promise<T> {
	const contentType = event.request.headers.get('content-type') ?? '';
	if (!contentType.toLowerCase().includes('application/json')) {
		throw error(415, 'application/json required');
	}

	const contentLength = Number(event.request.headers.get('content-length') ?? '0');
	if (Number.isFinite(contentLength) && contentLength > MAX_JSON_BODY_BYTES) {
		throw error(413, 'Request body too large');
	}

	const body = await event.request.json().catch(() => {
		throw error(400, 'Malformed JSON');
	});

	if (!isPlainObject(body)) {
		throw error(400, 'JSON object required');
	}

	return body as T;
}

export function assertIdentifier(value: unknown, fieldName: string): string {
	if (typeof value !== 'string' || !IDENTIFIER_RE.test(value)) {
		throw error(400, `${fieldName} is invalid`);
	}
	return value;
}

export function assertDate(value: unknown, fieldName: string): string {
	if (typeof value !== 'string' || !DATE_RE.test(value)) {
		throw error(400, `${fieldName} must be YYYY-MM-DD`);
	}
	validateDateString(value);
	return value;
}

export function assertYearMonth(value: unknown, fieldName: string): string {
	if (typeof value !== 'string' || !YEAR_MONTH_RE.test(value)) {
		throw error(400, `${fieldName} must be YYYY-MM`);
	}
	return value;
}

export function assertIsoDatetime(value: unknown, fieldName: string): string {
	if (typeof value !== 'string' || !ISO_DATETIME_RE.test(value) || Number.isNaN(Date.parse(value))) {
		throw error(400, `${fieldName} must be a valid ISO datetime`);
	}
	return value;
}

export function optionalText(value: unknown, maxLength = MAX_TEXT_LENGTH): string | null {
	if (value === null || value === undefined || value === '') return null;
	if (typeof value !== 'string') {
		throw error(400, 'Text field is invalid');
	}
	const trimmed = value.trim();
	if (!trimmed) return null;
	if (trimmed.length > maxLength) {
		throw error(400, `Text field exceeds ${maxLength} characters`);
	}
	return trimmed;
}

export function requiredText(value: unknown, fieldName: string, maxLength = 200): string {
	const trimmed = optionalText(value, maxLength);
	if (!trimmed) {
		throw error(400, `${fieldName} is required`);
	}
	return trimmed;
}

export function optionalNumber(
	value: unknown,
	fieldName: string,
	min: number,
	max: number
): number | null {
	if (value === null || value === undefined || value === '') return null;
	if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
		throw error(400, `${fieldName} is invalid`);
	}
	return value;
}

export function assertBloodType(value: unknown): BloodType | null {
	if (value === null || value === undefined || value === '') return null;
	if (typeof value !== 'string' || !BLOOD_TYPES.has(value as BloodType)) {
		throw error(400, 'bloodType is invalid');
	}
	return value as BloodType;
}

export function assertTriggerCategory(value: unknown): TriggerCategory {
	if (typeof value !== 'string' || !TRIGGER_CATEGORIES.has(value as TriggerCategory)) {
		throw error(400, 'category is invalid');
	}
	return value as TriggerCategory;
}

export function optionalTriggerCategory(value: unknown): TriggerCategory | undefined {
	if (value === null || value === undefined || value === '') return undefined;
	return assertTriggerCategory(value);
}

export function assertEntryCategory(value: unknown): EntryCategory {
	if (typeof value !== 'string' || !ENTRY_CATEGORIES.has(value as EntryCategory)) {
		throw error(400, 'category is invalid');
	}
	return value as EntryCategory;
}

export function optionalEntryCategory(value: unknown): EntryCategory | undefined {
	if (value === null || value === undefined || value === '') return undefined;
	return assertEntryCategory(value);
}

export function assertMedicationChangeType(value: unknown): MedicationChangeType {
	if (typeof value !== 'string' || !MEDICATION_CHANGE_TYPES.has(value as MedicationChangeType)) {
		throw error(400, 'changeType is invalid');
	}
	return value as MedicationChangeType;
}

export function optionalMetricValues(value: unknown): Record<string, unknown> | undefined {
	if (value === null || value === undefined) return undefined;
	if (!isPlainObject(value)) {
		throw error(400, 'metricValues must be an object');
	}
	return value;
}

export function assertMetricSchema(value: unknown): Record<string, unknown> {
	if (!isPlainObject(value)) {
		throw error(400, 'metricSchema must be an object');
	}
	const fields = value.fields;
	if (fields !== undefined && !Array.isArray(fields)) {
		throw error(400, 'metricSchema.fields must be an array');
	}
	return value;
}

export function optionalColor(value: unknown): string | null | undefined {
	if (value === undefined) return undefined;
	if (value === null || value === '') return null;
	if (typeof value !== 'string' || !COLOR_RE.test(value)) {
		throw error(400, 'color must be a hex value');
	}
	return value;
}

/** Daily well-being: an integer 1 to 5, or null to clear it. */
export function optionalWellbeing(value: unknown): number | null | undefined {
	if (value === undefined) return undefined;
	if (value === null) return null;
	if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > 5) {
		throw error(400, 'wellbeing must be a whole number from 1 to 5');
	}
	return value;
}

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Up to 6 distinct 'HH:MM' times, sorted. `null` clears them. */
export function optionalReminderTimes(value: unknown): string[] | undefined {
	if (value === undefined) return undefined;
	if (value === null) return [];
	if (!Array.isArray(value) || value.length > 6 || !value.every((v) => typeof v === 'string' && TIME_RE.test(v))) {
		throw error(400, 'reminderTimes must be up to 6 times like 08:00');
	}
	return [...new Set(value as string[])].sort();
}

export function assertBoolean(value: unknown, fieldName: string): boolean {
	if (typeof value !== 'boolean') {
		throw error(400, `${fieldName} must be boolean`);
	}
	return value;
}

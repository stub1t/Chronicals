import type { Entry } from '$lib/types/index.js';

/** Lower case without accents, so "Schlafe" finds "Schläfe" and "cafe" finds "Café". */
export function normalizeText(text: string): string {
	return text
		.normalize('NFD')
		.replace(/\p{Diacritic}/gu, '')
		.toLowerCase();
}

/**
 * All searchable text of an entry: note, entry type names, triggers and the
 * values of each log's form. Built-in names and options are stored in English,
 * so each one is added in its stored and its translated form.
 */
export function entrySearchText(entry: Entry, translate: (text: string) => string): string {
	const parts: string[] = [];
	const add = (value: unknown) => {
		if (typeof value === 'string' && value) parts.push(value, translate(value));
		else if (Array.isArray(value)) value.forEach(add);
	};

	add(entry.note);
	for (const log of entry.logs ?? []) {
		add(log.entryType?.name);
		Object.values(log.metricValues ?? {}).forEach(add);
	}
	for (const trigger of entry.triggers ?? []) {
		add(trigger.triggerName);
		add(trigger.note);
	}
	return normalizeText(parts.join('\n'));
}

/**
 * True if the entry has a log of `entryTypeId` (when given) and its text
 * contains every word of `query`.
 */
export function matchEntry(
	entry: Entry,
	query: string,
	entryTypeId: string | null,
	translate: (text: string) => string
): boolean {
	if (entryTypeId && !(entry.logs ?? []).some((log) => log.entryTypeId === entryTypeId)) return false;
	const words = normalizeText(query).split(/\s+/).filter(Boolean);
	if (words.length === 0) return true;
	const text = entrySearchText(entry, translate);
	return words.every((word) => text.includes(word));
}

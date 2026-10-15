import type { EntryCategory } from '$lib/types';

/** Symptoms and events last a while; notes and meals are a moment in time. */
export const hasDuration = (category: EntryCategory | undefined | null): boolean =>
	category === 'symptom' || category === 'event';

/** Field key of what was taken for a symptom; own entries are kept in the type's extra options under it. */
export const RELIEF_KEY = 'relief';

/** Allergies are treated with other medicines; the other symptoms are pain. */
export const reliefLabelKey = (typeId: string | undefined) => (typeId === 'builtin-allergy' ? 'log_relief' : 'log_painkillers');

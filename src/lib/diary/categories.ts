import type { EntryCategory } from '$lib/types';

/** Symptoms and events last a while; notes and meals are a moment in time. */
export const hasDuration = (category: EntryCategory | undefined | null): boolean =>
	category === 'symptom' || category === 'event';

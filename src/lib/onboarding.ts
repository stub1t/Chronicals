/**
 * The introduction runs once per device, and only for an empty diary: a
 * device that opens an existing diary (another browser on the server, or a
 * restored backup) skips it.
 */
import { getJson } from '$lib/api.js';
import type { UserProfile } from '$lib/types/index.js';

const KEY = 'chronicals-onboarded';

export function markOnboarded(): void {
	try {
		localStorage.setItem(KEY, '1');
	} catch {
		// Without storage the empty-diary check below still applies.
	}
}

function onboardedHere(): boolean {
	try {
		return localStorage.getItem(KEY) === '1';
	} catch {
		return false;
	}
}

export async function needsOnboarding(fetchFn: typeof fetch = fetch): Promise<boolean> {
	if (onboardedHere()) return false;
	const [stats, profile] = await Promise.all([
		getJson<{ entries: number }>(fetchFn, '/api/stats'),
		getJson<UserProfile>(fetchFn, '/api/profile')
	]);
	if (stats.entries > 0 || profile.displayName) {
		markOnboarded();
		return false;
	}
	return true;
}

import { getAllTriggers } from '$lib/server/queries/triggers.js';

export function load() {
	return { triggers: getAllTriggers() };
}

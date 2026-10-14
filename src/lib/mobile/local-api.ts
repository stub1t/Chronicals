/**
 * In-process `/api/*` for the Android app: same route table as the server,
 * answered from the on-device database instead of over the network.
 */
import { isHttpError, json } from '@sveltejs/kit';
import { handleApiRequest } from '../data/api.js';
import { openDatabase, persist } from './database.js';

export async function localFetch(path: string, init?: RequestInit): Promise<Response> {
	const url = new URL(path, location.origin);
	const request = new Request(url, init);

	try {
		await openDatabase();
		const response = await handleApiRequest({ request, url, fetch });

		if (request.method !== 'GET' && response.ok) {
			await persist();
			// Reminders follow the medications, ongoing logs and a restored backup.
			if (/^\/api\/(medications|medication-changes|entry-type-logs|backup)\b/.test(url.pathname)) {
				void import('./reminders.js').then((m) => m.syncReminders()).catch(console.error);
			}
		}
		return response;
	} catch (err) {
		// Mirror SvelteKit's endpoint error responses. Never reject: callers would
		// treat that as "offline" and queue the mutation for a sync that can't happen.
		if (isHttpError(err)) return json(err.body, { status: err.status });
		console.error(err);
		return json({ message: 'Internal Error' }, { status: 500 });
	}
}

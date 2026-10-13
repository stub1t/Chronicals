import { error } from '@sveltejs/kit';

/**
 * Call the chronicals API. On the web this is a plain `fetch` to the server;
 * in the Android app (`__NATIVE_APP__`) the request is answered in-process by
 * the on-device database, so there is no network hop and no server.
 *
 * Load functions pass SvelteKit's `fetch` so SSR keeps working.
 */
export async function api(path: string, init?: RequestInit, fetchFn: typeof fetch = fetch): Promise<Response> {
	if (__NATIVE_APP__) {
		const { localFetch } = await import('$lib/mobile/local-api.js');
		return localFetch(path, init);
	}
	return fetchFn(path, init);
}

/** GET a JSON resource for a load function; non-2xx becomes a SvelteKit error page. */
export async function getJson<T>(fetchFn: typeof fetch, path: string): Promise<T> {
	const res = await api(path, undefined, fetchFn);
	if (!res.ok) {
		const body = await res.json().catch(() => null);
		error(res.status, body?.message ?? body?.error ?? res.statusText);
	}
	return res.json();
}

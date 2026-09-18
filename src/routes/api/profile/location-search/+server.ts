import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { requiredText } from '$lib/server/security.js';

export const GET: RequestHandler = async ({ url, fetch }) => {
	const query = requiredText(url.searchParams.get('q'), 'q', 120);
	const searchUrl = new URL('https://geocoding-api.open-meteo.com/v1/search');
	searchUrl.searchParams.set('name', query);
	searchUrl.searchParams.set('count', '5');
	searchUrl.searchParams.set('language', 'de');
	searchUrl.searchParams.set('format', 'json');

	const response = await fetch(searchUrl, {
		signal: AbortSignal.timeout(5000)
	}).catch(() => null);

	if (!response?.ok) {
		return json({ results: [] }, { status: 200 });
	}

	const payload = await response.json().catch(() => ({ results: [] }));
	return json({ results: Array.isArray(payload?.results) ? payload.results : [] });
};

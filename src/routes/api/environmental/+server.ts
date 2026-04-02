import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { fetchAndCacheEnvData, getEnvDataForDate } from '$lib/server/weather.js';
import { getProfile } from '$lib/server/queries/profile.js';

export const GET: RequestHandler = async ({ url }) => {
	const date = url.searchParams.get('date');
	if (!date) return json({ error: 'date required' }, { status: 400 });

	const profile = getProfile();

	// Return cached data if no location set
	if (!profile.locationLat || !profile.locationLon) {
		const cached = getEnvDataForDate(date);
		return json(cached ?? null);
	}

	const data = await fetchAndCacheEnvData(date, profile.locationLat, profile.locationLon);
	return json(data ?? null);
};

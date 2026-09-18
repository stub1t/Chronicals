import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { fetchAndCacheEnvData, getEnvDataForDate } from '$lib/server/weather.js';
import { getProfile } from '$lib/server/queries/profile.js';
import { assertDate } from '$lib/server/security.js';

export const GET: RequestHandler = async ({ url }) => {
	const date = assertDate(url.searchParams.get('date'), 'date');
	const profile = getProfile();

	if (profile.locationLat == null || profile.locationLon == null) {
		const cached = getEnvDataForDate(date);
		return json(cached ?? null);
	}

	const data = await fetchAndCacheEnvData(date, profile.locationLat, profile.locationLon);
	return json(data ?? null);
};

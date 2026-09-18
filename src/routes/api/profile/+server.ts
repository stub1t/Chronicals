import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getProfile, updateProfile } from '$lib/server/queries/profile.js';
import { assertBloodType, optionalNumber, optionalText, parseJsonRequest } from '$lib/server/security.js';

export const GET: RequestHandler = () => json(getProfile());

export const PUT: RequestHandler = async (event) => {
	const body = await parseJsonRequest(event);
	const profile = updateProfile({
		displayName: optionalText(body.displayName, 120),
		bloodType: assertBloodType(body.bloodType),
		locationLat: optionalNumber(body.locationLat, 'locationLat', -90, 90),
		locationLon: optionalNumber(body.locationLon, 'locationLon', -180, 180),
		locationName: optionalText(body.locationName, 160)
	});
	return json(profile);
};

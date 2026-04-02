import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getProfile, updateProfile } from '$lib/server/queries/profile.js';

export const GET: RequestHandler = () => json(getProfile());

export const PUT: RequestHandler = async ({ request }) => {
	const body = await request.json();
	const profile = updateProfile(body);
	return json(profile);
};

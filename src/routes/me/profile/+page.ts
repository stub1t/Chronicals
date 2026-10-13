import { getJson } from '$lib/api.js';
import type { UserProfile } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => ({
	profile: await getJson<UserProfile>(fetch, '/api/profile')
});

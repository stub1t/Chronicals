import { getJson } from '$lib/api.js';
import type { Trigger } from '$lib/types/index.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => ({
	triggers: await getJson<Trigger[]>(fetch, '/api/triggers')
});

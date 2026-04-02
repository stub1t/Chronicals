import { getProfile } from '$lib/server/queries/profile.js';

export const load = () => ({ profile: getProfile() });

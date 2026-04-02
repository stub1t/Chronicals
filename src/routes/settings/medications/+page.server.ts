import { getMedications, getMedicationChanges } from '$lib/server/queries/profile.js';

export const load = () => ({ medications: getMedications(), changes: getMedicationChanges() });

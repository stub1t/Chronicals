import type { RequestHandler } from './$types';
import { handleApiRequest } from '$lib/data/api.js';

// Every /api/* route lives in $lib/data/api.ts so the Android app can serve
// the same table in-process.
export const fallback: RequestHandler = (event) => handleApiRequest(event);

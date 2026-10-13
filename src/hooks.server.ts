import type { Handle } from '@sveltejs/kit';
import { dev } from '$app/environment';
import '$lib/server/db.js';
import { enforceTrustedMutation } from '$lib/data/security.js';

function buildCsp(): string {
	const connectSources = dev
		? "'self' ws: wss: https://api.open-meteo.com https://geocoding-api.open-meteo.com"
		: "'self' https://api.open-meteo.com https://geocoding-api.open-meteo.com";

	return [
		"default-src 'self'",
		"base-uri 'self'",
		"frame-ancestors 'none'",
		"form-action 'self'",
		"img-src 'self' data: blob:",
		"font-src 'self' data:",
		`connect-src ${connectSources}`,
		"object-src 'none'",
		"script-src 'self' 'unsafe-inline'",
		"style-src 'self' 'unsafe-inline'"
	].join('; ');
}

export const handle: Handle = async ({ event, resolve }) => {
	if (event.url.pathname.startsWith('/api/')) {
		enforceTrustedMutation(event);
	}

	const response = await resolve(event);

	response.headers.set('X-Content-Type-Options', 'nosniff');
	response.headers.set('X-Frame-Options', 'DENY');
	response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
	response.headers.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
	response.headers.set('Cross-Origin-Opener-Policy', 'same-origin');
	response.headers.set('Content-Security-Policy', buildCsp());

	if (event.url.pathname.startsWith('/api/')) {
		response.headers.set('Cache-Control', 'no-store');
	}

	return response;
};

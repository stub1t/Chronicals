import type { Handle } from '@sveltejs/kit';
import { isIP } from 'node:net';
import { building, dev } from '$app/environment';
import { env } from '$env/dynamic/private';
import '$lib/server/db.js';
import { enforceTrustedMutation } from '$lib/data/security.js';

// DNS rebinding protection: a page on attacker.example can point its own name at
// this server and then read /api as same-origin. Only answer to host names we
// know. IP literals are always fine, because rebinding needs a domain name the
// attacker controls.
const allowedHostnames = new Set(
	[env.ORIGIN ? new URL(env.ORIGIN).hostname : '', 'localhost', ...(env.ALLOWED_HOSTS ?? '').split(',')]
		.map((name) => name.trim().toLowerCase())
		.filter(Boolean)
);

function isAllowedHost(host: string | null): boolean {
	if (!host) return false;
	let hostname: string;
	try {
		hostname = new URL(`http://${host}`).hostname.toLowerCase();
	} catch {
		return false;
	}
	return isIP(hostname.replace(/^\[|\]$/g, '')) !== 0 || allowedHostnames.has(hostname);
}

function buildCsp(): string {
	// The browser only talks to this server. Open-Meteo is called by the server, and only with weather turned on.
	const connectSources = dev ? "'self' ws: wss:" : "'self'";

	return [
		"default-src 'self'",
		"base-uri 'self'",
		"frame-ancestors 'none'",
		"form-action 'self'",
		"img-src 'self' data: blob:",
		"font-src 'self' data:",
		`connect-src ${connectSources}`,
		"object-src 'none'",
		// The doctor report prints from a blob: frame of its own HTML.
		"frame-src 'self' blob:",
		"script-src 'self' 'unsafe-inline'",
		"style-src 'self' 'unsafe-inline'"
	].join('; ');
}

export const handle: Handle = async ({ event, resolve }) => {
	// SvelteKit's internal fetch during SSR sends no Host header; its URL comes
	// from the outer request, which was already checked. Build-time rendering
	// (the Android app's fallback page) uses an internal host name.
	if (!dev && !building && !isAllowedHost(event.request.headers.get('host') ?? event.url.host)) {
		return new Response('Unknown host. Add it to ALLOWED_HOSTS.', { status: 421 });
	}

	if (event.url.pathname.startsWith('/api/')) {
		enforceTrustedMutation(event);
	}

	const response = await resolve(event);

	response.headers.set('X-Content-Type-Options', 'nosniff');
	response.headers.set('X-Frame-Options', 'DENY');
	response.headers.set('Referrer-Policy', 'no-referrer');
	response.headers.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
	response.headers.set('Cross-Origin-Opener-Policy', 'same-origin');
	response.headers.set('Content-Security-Policy', buildCsp());

	if (event.url.pathname.startsWith('/api/')) {
		response.headers.set('Cache-Control', 'no-store');
	}

	return response;
};

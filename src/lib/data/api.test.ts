import { isHttpError } from '@sveltejs/kit';
import { beforeEach, describe, expect, it } from 'vitest';
import { useTestDatabase } from '$lib/server/testing.js';
import { handleApiRequest } from './api.js';

function call(method: string, path: string, body?: unknown) {
	const url = new URL(path, 'http://localhost');
	const request = new Request(url, {
		method,
		headers: body === undefined ? {} : { 'content-type': 'application/json' },
		body: body === undefined ? undefined : JSON.stringify(body)
	});
	return handleApiRequest({ request, url, fetch });
}

async function status(promise: Promise<Response>): Promise<number> {
	try {
		return (await promise).status;
	} catch (err) {
		if (isHttpError(err)) return err.status;
		throw err;
	}
}

beforeEach(() => {
	useTestDatabase();
});

describe('handleApiRequest', () => {
	it('creates an entry and reads it back by date', async () => {
		const created = await call('POST', '/api/entries', { date: '2026-03-29', note: 'hello' });
		expect(created.status).toBe(201);
		const entry = await (await call('GET', '/api/entries?date=2026-03-29')).json();
		expect(entry).toMatchObject({ date: '2026-03-29', note: 'hello', logs: [], triggers: [] });
	});

	it('returns null for a day without an entry', async () => {
		expect(await (await call('GET', '/api/entries?date=2026-03-30')).json()).toBeNull();
	});

	it('validates input', async () => {
		expect(await status(call('GET', '/api/entries?month=2026-3'))).toBe(400);
		expect(await status(call('POST', '/api/entries', { date: '29.03.2026' }))).toBe(400);
		expect(await status(call('GET', '/api/entries/not%20an%20id'))).toBe(400);
	});

	it('answers unknown paths with 404 and wrong methods with 405', async () => {
		expect(await status(call('GET', '/api/nothing'))).toBe(404);
		const res = await call('DELETE', '/api/entries');
		expect(res.status).toBe(405);
		expect(res.headers.get('allow')).toBe('GET, POST');
	});

	it('serves HEAD like GET', async () => {
		expect(await status(call('HEAD', '/api/health'))).toBe(200);
	});
});

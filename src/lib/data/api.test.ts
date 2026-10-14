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

	it('keeps one entry per day and updates only the given fields', async () => {
		const first = await call('POST', '/api/entries', { date: '2026-03-29', note: 'hello' });
		expect(first.status).toBe(201);
		const again = await call('POST', '/api/entries', { date: '2026-03-29', wellbeing: 4 });
		expect(again.status).toBe(200);
		const entry = await again.json();
		expect(entry).toMatchObject({ id: (await first.json()).id, note: 'hello', wellbeing: 4 });

		const updated = await (await call('PUT', `/api/entries/${entry.id}`, { wellbeing: 2 })).json();
		expect(updated).toMatchObject({ note: 'hello', wellbeing: 2 });
		const cleared = await (await call('PUT', `/api/entries/${entry.id}`, { note: '', wellbeing: null })).json();
		expect(cleared).toMatchObject({ note: null, wellbeing: null });
		expect(await status(call('PUT', `/api/entries/${entry.id}`, { wellbeing: 6 }))).toBe(400);
		expect(await status(call('PUT', `/api/entries/${entry.id}`, { wellbeing: 2.5 }))).toBe(400);
	});

	it('returns stored weather for a date range', async () => {
		const { db } = useTestDatabase();
		const { environmentalData } = await import('../db/schema.js');
		for (const [i, date] of ['2026-03-01', '2026-03-02', '2026-04-01'].entries()) {
			db.insert(environmentalData).values({ id: `w${i}`, date, airPressureHpa: 1000 + i }).run();
		}
		const rows = await (await call('GET', '/api/environmental?from=2026-03-01&to=2026-03-31')).json();
		expect(rows.map((r: { date: string }) => r.date)).toEqual(['2026-03-01', '2026-03-02']);
		expect(await status(call('GET', '/api/environmental?from=2026-04-01&to=2026-03-01'))).toBe(400);
	});

	it('returns null for a day without an entry', async () => {
		expect(await (await call('GET', '/api/entries?date=2026-03-30')).json()).toBeNull();
	});

	it('returns full entries for a date range in date order', async () => {
		for (const date of ['2026-03-30', '2026-03-01', '2026-04-02']) await call('POST', '/api/entries', { date });
		const list = await (await call('GET', '/api/entries?from=2026-03-01&to=2026-03-31')).json();
		expect(list.map((e: { date: string }) => e.date)).toEqual(['2026-03-01', '2026-03-30']);
		expect(await status(call('GET', '/api/entries?from=2026-04-01&to=2026-03-01'))).toBe(400);
		expect(await status(call('GET', '/api/entries?from=2026-04-01'))).toBe(400);
	});

	it('stores medication reminder times sorted and without duplicates', async () => {
		const res = await call('POST', '/api/medications', { name: 'Magnesium', startDate: '2026-01-01', reminderTimes: ['20:00', '08:00', '08:00'] });
		const med = await res.json();
		expect(med.reminderTimes).toEqual(['08:00', '20:00']);
		const cleared = await (await call('PUT', `/api/medications/${med.id}`, { reminderTimes: null })).json();
		expect(cleared.reminderTimes).toEqual([]);
		const bad = (reminderTimes: unknown) => status(call('POST', '/api/medications', { name: 'X', startDate: '2026-01-01', reminderTimes }));
		expect(await bad(['8:00'])).toBe(400);
		expect(await bad(['24:00'])).toBe(400);
		expect(await bad(Array.from({ length: 7 }, (_, i) => `0${i}:00`))).toBe(400);
		expect(await bad('08:00')).toBe(400);
	});

	it('hides built-in entry types but keeps them otherwise read-only', async () => {
		const hidden = await call('PUT', '/api/entry-types/builtin-period', { hidden: true });
		expect(await hidden.json()).toMatchObject({ id: 'builtin-period', hidden: true });
		expect(await status(call('PUT', '/api/entry-types/builtin-period', { name: 'Cycle' }))).toBe(404);
		expect(await status(call('PUT', '/api/entry-types/builtin-period', { hidden: 'yes' }))).toBe(400);
		const types = await (await call('GET', '/api/entry-types')).json();
		expect(types.find((t: { id: string }) => t.id === 'builtin-period').hidden).toBe(true);
		expect(types.find((t: { id: string }) => t.id === 'builtin-headache').hidden).toBe(false);
	});

	it('renames built-in types on start unless a custom type has the new name', async () => {
		const { db } = useTestDatabase();
		const { entryTypes } = await import('../db/schema.js');
		const { seedBuiltIns } = await import('./seed.js');
		const { eq } = await import('drizzle-orm');
		const name = (id: string) => db.select().from(entryTypes).where(eq(entryTypes.id, id)).get()!.name;

		db.update(entryTypes).set({ name: 'Hypo' }).where(eq(entryTypes.id, 'builtin-hypo')).run();
		seedBuiltIns(db);
		expect(name('builtin-hypo')).toBe('Blood sugar');

		db.update(entryTypes).set({ name: 'Hypo' }).where(eq(entryTypes.id, 'builtin-hypo')).run();
		await call('POST', '/api/entry-types', { name: 'Blood sugar', metricSchema: { fields: [] } });
		expect(() => seedBuiltIns(db)).not.toThrow();
		expect(name('builtin-hypo')).toBe('Hypo');
	});

	it('turns old severity values into intensity 1 to 5 on start', async () => {
		const { db } = useTestDatabase();
		const { entryTypeLogs } = await import('../db/schema.js');
		const { seedBuiltIns } = await import('./seed.js');
		const entry = await (await call('POST', '/api/entries', { date: '2026-03-01' })).json();
		const old = [
			['a', 'builtin-allergy', { severity: 4, symptoms: ['sneezing'] }],
			['b', 'builtin-hypo', { severity: 'moderate', blood_glucose: '58' }],
			['c', 'builtin-hypo', { severity: 'unknown' }]
		] as const;
		for (const [id, type, values] of old) {
			db.insert(entryTypeLogs)
				.values({ id, entryId: entry.id, entryTypeId: type, startTime: '2026-03-01T10:00:00.000Z', metricValues: JSON.stringify(values) })
				.run();
		}
		seedBuiltIns(db);
		const rows = Object.fromEntries(db.select().from(entryTypeLogs).all().map((r) => [r.id, r]));
		expect(JSON.parse(rows.a.metricValues)).toEqual({ intensity: 4, symptoms: ['sneezing'] });
		expect(rows.a.intensityCache).toBe(4);
		expect(JSON.parse(rows.b.metricValues)).toEqual({ direction: 'too low', intensity: 3, blood_glucose: '58' });
		expect(JSON.parse(rows.c.metricValues)).toEqual({ direction: 'too low' });
		expect(rows.c.intensityCache).toBeNull();
	});

	it('lets built-in triggers be renamed, deleted and brought back', async () => {
		const { db } = useTestDatabase();
		const { seedBuiltIns } = await import('./seed.js');
		const get = async (id: string) => ((await (await call('GET', '/api/triggers')).json()) as Array<{ id: string }>).find((t) => t.id === id);

		await call('DELETE', '/api/triggers/trig-stress');
		expect(await get('trig-stress')).toMatchObject({ hidden: true, isBuiltIn: true });
		seedBuiltIns(db);
		expect(await get('trig-stress')).toMatchObject({ hidden: true });
		// Adding the same name again brings it back.
		expect((await call('POST', '/api/triggers', { name: 'Stress', category: 'activity' })).status).toBe(201);
		expect(await get('trig-stress')).toMatchObject({ hidden: false });
		expect(await status(call('POST', '/api/triggers', { name: 'Stress' }))).toBe(409);

		const renamed = await (await call('PUT', '/api/triggers/trig-caffeine', { name: 'Coffee', category: 'custom' })).json();
		expect(renamed).toMatchObject({ name: 'Coffee', category: 'custom', isBuiltIn: false });
		seedBuiltIns(db);
		expect(await get('trig-caffeine')).toMatchObject({ name: 'Coffee' });
		expect(await status(call('PUT', '/api/triggers/trig-caffeine', { name: 'Dairy' }))).toBe(409);

		// An unused own trigger is removed; a used one is only hidden.
		const unused = await (await call('POST', '/api/triggers', { name: 'Unused' })).json();
		await call('DELETE', `/api/triggers/${unused.id}`);
		expect(await get(unused.id)).toBeUndefined();
		const used = await (await call('POST', '/api/triggers', { name: 'Used' })).json();
		const entry = await (await call('POST', '/api/entries', { date: '2026-03-01' })).json();
		await call('PATCH', `/api/entries/${entry.id}`, { action: 'add_trigger', triggerId: used.id });
		await call('DELETE', `/api/triggers/${used.id}`);
		expect(await get(used.id)).toMatchObject({ hidden: true });
		const day = await (await call('GET', '/api/entries?date=2026-03-01')).json();
		expect(day.triggers.map((t: { triggerName: string }) => t.triggerName)).toEqual(['Used']);
	});

	it('turns old medication change logs into the medication change trigger', async () => {
		const { db } = useTestDatabase();
		const { entryTypes, entryTypeLogs } = await import('../db/schema.js');
		const { seedBuiltIns } = await import('./seed.js');
		db.insert(entryTypes).values({ id: 'builtin-medication-change', name: 'Medication change', metricSchema: '{"fields":[]}', category: 'event', isBuiltIn: true }).run();
		const entry = await (await call('POST', '/api/entries', { date: '2026-03-02' })).json();
		db.insert(entryTypeLogs)
			.values({
				id: 'old',
				entryId: entry.id,
				entryTypeId: 'builtin-medication-change',
				startTime: '2026-03-02T09:00:00.000Z',
				metricValues: JSON.stringify({ medication_name: 'Magnesium', change_type: 'dosage changed', old_dosage: '400 mg', new_dosage: '600 mg' })
			})
			.run();
		seedBuiltIns(db);
		const day = await (await call('GET', '/api/entries?date=2026-03-02')).json();
		expect(day.logs).toEqual([]);
		expect(day.triggers).toMatchObject([{ triggerId: 'trig-medication-change', note: 'Magnesium, dosage changed, 400 mg → 600 mg' }]);
		const types = await (await call('GET', '/api/entry-types')).json();
		expect(types.some((t: { id: string }) => t.id === 'builtin-medication-change')).toBe(false);
	});

	it('marks medication starts and dose changes as a trigger on their day', async () => {
		const med = await (await call('POST', '/api/medications', { name: 'Magnesium', dosage: '400', unit: 'mg', startDate: '2026-03-01' })).json();
		await call('POST', '/api/medication-changes', { medicationId: med.id, changeType: 'dosage_change', oldDosage: '400 mg', newDosage: '600 mg', changeDate: '2026-03-10' });
		const start = await (await call('GET', '/api/entries?date=2026-03-01')).json();
		expect(start.triggers).toMatchObject([{ triggerId: 'trig-medication-change', note: 'Magnesium 400 mg' }]);
		const change = await (await call('GET', '/api/entries?date=2026-03-10')).json();
		expect(change.triggers).toMatchObject([{ triggerId: 'trig-medication-change', note: 'Magnesium: 400 mg → 600 mg' }]);
		// Not when the user deleted that trigger.
		await call('DELETE', '/api/triggers/trig-medication-change');
		await call('POST', '/api/medications', { name: 'Other', startDate: '2026-03-20' });
		expect(await (await call('GET', '/api/entries?date=2026-03-20')).json()).toBeNull();
	});

	it('keeps own options on built-in types across restarts', async () => {
		const { db } = useTestDatabase();
		const { seedBuiltIns } = await import('./seed.js');
		const saved = await (await call('PUT', '/api/entry-types/builtin-headache', { extraOptions: { location: ['jaw', ' jaw '] } })).json();
		expect(saved.extraOptions).toEqual({ location: ['jaw'] });
		seedBuiltIns(db);
		const types = await (await call('GET', '/api/entry-types')).json();
		expect(types.find((t: { id: string }) => t.id === 'builtin-headache').extraOptions).toEqual({ location: ['jaw'] });
		expect(await status(call('PUT', '/api/entry-types/builtin-headache', { extraOptions: { location: 'jaw' } }))).toBe(400);
		expect(await status(call('PUT', '/api/entry-types/builtin-headache', { extraOptions: { 'bad key!': ['x'] } }))).toBe(400);
		// Built-in types still refuse other changes.
		expect(await status(call('PUT', '/api/entry-types/builtin-headache', { extraOptions: {}, name: 'Migraine' }))).toBe(404);
	});

	it('offers a meal type and blood sugar with a direction', async () => {
		const types = await (await call('GET', '/api/entry-types')).json();
		const meal = types.find((t: { id: string }) => t.id === 'builtin-meal');
		expect(meal).toMatchObject({ name: 'Meal', category: 'meal' });
		expect(meal.metricSchema.fields.map((f: { key: string }) => f.key)).toEqual(['meal', 'foods', 'what']);
		const sugar = types.find((t: { id: string }) => t.id === 'builtin-hypo');
		expect(sugar.name).toBe('Blood sugar');
		expect(sugar.metricSchema.fields[0]).toMatchObject({ key: 'direction', config: { options: ['too low', 'too high'] } });
	});

	it('deletes only unused custom entry types', async () => {
		const type = await (await call('POST', '/api/entry-types', { name: 'Tinnitus', metricSchema: { fields: [] } })).json();
		const entry = await (await call('POST', '/api/entries', { date: '2026-03-01' })).json();
		const log = await (
			await call('POST', '/api/entry-type-logs', { entryId: entry.id, entryTypeId: type.id, startTime: '2026-03-01T10:00:00.000Z' })
		).json();
		expect(await status(call('DELETE', `/api/entry-types/${type.id}`))).toBe(409);
		await call('DELETE', `/api/entry-type-logs/${log.id}`);
		expect(await status(call('DELETE', `/api/entry-types/${type.id}`))).toBe(204);
		const types = await (await call('GET', '/api/entry-types')).json();
		expect(types.some((t: { id: string }) => t.id === type.id)).toBe(false);
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

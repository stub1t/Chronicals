import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useTestDatabase } from '$lib/server/testing.js';
import { ensureEnvData, getEnvDataInRange, parseDailyPollen, parseDailyWeather } from './weather.js';

const days = (from: string, n: number) =>
	Array.from({ length: n }, (_, i) => new Date(Date.parse(`${from}T12:00:00Z`) + i * 86_400_000).toISOString().slice(0, 10));

/** A fake Open-Meteo: pressure 1000 + day index, pollen only for birch at noon. */
function fakeOpenMeteo() {
	const calls: URL[] = [];
	const fetchMock = vi.fn(async (input: URL | string) => {
		const url = new URL(String(input));
		calls.push(url);
		const dates = days(url.searchParams.get('start_date')!, 1 + Math.round((Date.parse(url.searchParams.get('end_date')!) - Date.parse(url.searchParams.get('start_date')!)) / 86_400_000));
		if (url.hostname.startsWith('air-quality')) {
			const time = dates.flatMap((d) => [`${d}T00:00`, `${d}T12:00`]);
			return Response.json({ hourly: { time, birch_pollen: time.map((t) => (t.endsWith('12:00') ? 150 : 5)), grass_pollen: time.map(() => null) } });
		}
		return Response.json({
			daily: {
				time: dates,
				temperature_2m_max: dates.map(() => 20),
				temperature_2m_min: dates.map(() => 10),
				surface_pressure_mean: dates.map((_, i) => 1000 + i),
				uv_index_max: dates.map(() => 4),
				weather_code: dates.map(() => 61)
			}
		});
	});
	vi.stubGlobal('fetch', fetchMock);
	return calls;
}

describe('parsing', () => {
	it('reads daily weather and the daily pollen peak', () => {
		const weather = parseDailyWeather({ daily: { time: ['2026-03-01'], temperature_2m_max: [12], temperature_2m_min: [4], surface_pressure_mean: [1012.5], uv_index_max: [null], weather_code: [3] } });
		expect(weather.get('2026-03-01')).toEqual({ temperatureC: 8, airPressureHpa: 1012.5, uvIndex: null, weatherCondition: 'overcast' });
		const pollen = parseDailyPollen({ hourly: { time: ['2026-03-01T00:00', '2026-03-01T13:00', '2026-03-02T00:00'], birch_pollen: [3, 80, null] } });
		expect(pollen.get('2026-03-01')?.birch).toBe(80);
		expect(pollen.get('2026-03-02')?.birch).toBeNull();
	});
});

describe('ensureEnvData', () => {
	beforeEach(() => {
		vi.useFakeTimers({ toFake: ['Date'] });
		vi.setSystemTime(new Date('2026-03-20T12:00:00Z'));
	});
	afterEach(() => {
		vi.useRealTimers();
		vi.unstubAllGlobals();
	});

	it('fetches each place once, keeps past days, and refetches when a day moves', async () => {
		const { db } = useTestDatabase();
		const { userProfile, entries } = await import('../db/schema.js');
		db.update(userProfile).set({ locationLat: 48.2, locationLon: 16.37, locationName: 'Wien' }).run();
		db.insert(entries).values({ id: 'trip', date: '2026-03-05', locationLat: 41.39, locationLon: 2.17, locationName: 'Barcelona', locationSource: 'manual' }).run();
		const calls = fakeOpenMeteo();

		await ensureEnvData(days('2026-03-01', 7));
		const weatherCalls = calls.filter((u) => !u.hostname.startsWith('air-quality'));
		// Home (Mar 1-7 without the 5th, one range) and Barcelona.
		expect(weatherCalls.map((u) => u.searchParams.get('latitude')).sort()).toEqual(['41.39', '48.2']);
		const rows = getEnvDataInRange('2026-03-01', '2026-03-07');
		expect(rows).toHaveLength(7);
		expect(rows.find((r) => r.date === '2026-03-05')).toMatchObject({ lat: 41.39, pollenBirch: 150, pollenGrass: null, weatherCondition: 'rain' });
		// The day before each range is fetched too, so the first day has a pressure change.
		expect(rows[0].pressureDeltaHpa).toBe(1);

		calls.length = 0;
		await ensureEnvData(days('2026-03-01', 7));
		expect(calls).toHaveLength(0);

		db.update(entries).set({ locationLat: null, locationLon: null, locationName: null }).run();
		await ensureEnvData(days('2026-03-01', 7));
		expect(calls.filter((u) => !u.hostname.startsWith('air-quality')).map((u) => [u.searchParams.get('latitude'), u.searchParams.get('start_date')])).toEqual([['48.2', '2026-03-04']]);
	});

	it('takes old days from the archive and recent ones from the forecast', async () => {
		const { db } = useTestDatabase();
		const { userProfile } = await import('../db/schema.js');
		db.update(userProfile).set({ locationLat: 48.2, locationLon: 16.37 }).run();
		const calls = fakeOpenMeteo();
		await ensureEnvData(days('2025-12-01', 100));
		const hosts = calls.filter((u) => !u.hostname.startsWith('air-quality')).map((u) => u.hostname);
		expect(hosts).toEqual(['archive-api.open-meteo.com', 'api.open-meteo.com']);
		expect(getEnvDataInRange('2025-12-01', '2026-03-20')).toHaveLength(100);
	});

	it('does nothing without a place and keeps going when Open-Meteo fails', async () => {
		useTestDatabase();
		const calls = fakeOpenMeteo();
		await ensureEnvData(['2026-03-01']);
		expect(calls).toHaveLength(0);
		vi.stubGlobal('fetch', vi.fn(async () => new Response('down', { status: 503 })));
		const { db } = useTestDatabase();
		const { userProfile } = await import('../db/schema.js');
		db.update(userProfile).set({ locationLat: 48.2, locationLon: 16.37 }).run();
		await expect(ensureEnvData(['2026-03-01'])).resolves.toBeUndefined();
		expect(getEnvDataInRange('2026-03-01', '2026-03-01')).toEqual([]);
	});
});

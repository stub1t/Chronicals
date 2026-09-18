import { eq } from 'drizzle-orm';
import { db } from '../db/index.js';
import { environmentalData } from '../db/schema.js';
import type { EnvironmentalData } from '../types/index.js';
import { v4 as uuidv4 } from 'uuid';

function formatRow(row: typeof environmentalData.$inferSelect): EnvironmentalData {
	return {
		id: row.id,
		date: row.date,
		temperatureC: row.temperatureC,
		airPressureHpa: row.airPressureHpa,
		pressureDeltaHpa: row.pressureDeltaHpa,
		weatherCondition: row.weatherCondition,
		pollenGrass: row.pollenGrass,
		pollenBirch: row.pollenBirch,
		pollenAlder: row.pollenAlder,
		pollenMugwort: row.pollenMugwort,
		uvIndex: row.uvIndex,
		fetchedAt: row.fetchedAt
	};
}

function assertCoordinateRange(lat: number, lon: number): void {
	if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
		throw new Error('Invalid coordinates');
	}
}

export function getEnvDataForDate(date: string): EnvironmentalData | null {
	const row = db.select().from(environmentalData).where(eq(environmentalData.date, date)).get();
	return row ? formatRow(row) : null;
}

function getPreviousDayPressure(date: string): number | null {
	const d = new Date(date);
	d.setDate(d.getDate() - 1);
	const prevDate = d.toISOString().split('T')[0];
	const row = db
		.select({ pressure: environmentalData.airPressureHpa })
		.from(environmentalData)
		.where(eq(environmentalData.date, prevDate))
		.get();
	return row?.pressure ?? null;
}

export async function fetchAndCacheEnvData(
	date: string,
	lat: number,
	lon: number
): Promise<EnvironmentalData | null> {
	assertCoordinateRange(lat, lon);

	const existing = db.select().from(environmentalData).where(eq(environmentalData.date, date)).get();
	if (existing) {
		const fetchedAt = new Date(existing.fetchedAt).getTime();
		const sixHoursAgo = Date.now() - 6 * 60 * 60 * 1000;
		if (fetchedAt > sixHoursAgo) {
			return formatRow(existing);
		}
	}

	try {
		const weatherUrl = new URL('https://api.open-meteo.com/v1/forecast');
		weatherUrl.searchParams.set('latitude', String(lat));
		weatherUrl.searchParams.set('longitude', String(lon));
		weatherUrl.searchParams.set(
			'daily',
			'temperature_2m_max,temperature_2m_min,surface_pressure_mean,uv_index_max,precipitation_sum,weather_code'
		);
		weatherUrl.searchParams.set('start_date', date);
		weatherUrl.searchParams.set('end_date', date);
		weatherUrl.searchParams.set('timezone', 'auto');

		const weatherRes = await fetch(weatherUrl, { signal: AbortSignal.timeout(5000) });
		if (!weatherRes.ok) throw new Error(`Weather upstream failed: ${weatherRes.status}`);
		const weather = await weatherRes.json();

		const tempMax = weather?.daily?.temperature_2m_max?.[0] ?? null;
		const tempMin = weather?.daily?.temperature_2m_min?.[0] ?? null;
		const temperature = tempMax !== null && tempMin !== null ? (tempMax + tempMin) / 2 : null;
		const pressure = weather?.daily?.surface_pressure_mean?.[0] ?? null;
		const uvIndex = weather?.daily?.uv_index_max?.[0] ?? null;
		const weatherCode = weather?.daily?.weather_code?.[0] ?? null;
		const prevPressure = getPreviousDayPressure(date);
		const pressureDelta = pressure !== null && prevPressure !== null ? pressure - prevPressure : null;
		const weatherCondition = weatherCode !== null ? wmoCodeToCondition(weatherCode) : null;
		const id = existing?.id ?? uuidv4();

		if (existing) {
			db.update(environmentalData)
				.set({
					temperatureC: temperature,
					airPressureHpa: pressure,
					pressureDeltaHpa: pressureDelta,
					weatherCondition,
					pollenGrass: null,
					pollenBirch: null,
					pollenAlder: null,
					pollenMugwort: null,
					uvIndex,
					fetchedAt: new Date().toISOString()
				})
				.where(eq(environmentalData.date, date))
				.run();
		} else {
			db.insert(environmentalData)
				.values({
					id,
					date,
					temperatureC: temperature,
					airPressureHpa: pressure,
					pressureDeltaHpa: pressureDelta,
					weatherCondition,
					pollenGrass: null,
					pollenBirch: null,
					pollenAlder: null,
					pollenMugwort: null,
					uvIndex
				})
				.run();
		}

		const row = db.select().from(environmentalData).where(eq(environmentalData.date, date)).get()!;
		return formatRow(row);
	} catch {
		return existing ? formatRow(existing) : null;
	}
}

function wmoCodeToCondition(code: number): string {
	if (code === 0) return 'clear sky';
	if (code <= 2) return 'partly cloudy';
	if (code === 3) return 'overcast';
	if (code <= 49) return 'foggy';
	if (code <= 59) return 'drizzle';
	if (code <= 69) return 'rain';
	if (code <= 79) return 'snow';
	if (code <= 82) return 'rain showers';
	if (code <= 86) return 'snow showers';
	if (code <= 99) return 'thunderstorm';
	return 'unknown';
}

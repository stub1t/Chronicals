/**
 * Weather and pollen per diary day from Open-Meteo, for the place where the
 * day was spent (the day's own place, or home). Missing days are fetched in
 * one request per place over the whole date range; a past day fetched for
 * the right place is not fetched again.
 */
import { and, asc, eq, gte, inArray, lte } from 'drizzle-orm';
import { db } from './db.js';
import { entries, environmentalData, userProfile } from '../db/schema.js';
import type { EnvironmentalData } from '../types/index.js';
import { v4 as uuidv4 } from 'uuid';

type Row = typeof environmentalData.$inferSelect;

function formatRow(row: Row): EnvironmentalData {
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
		pollenOlive: row.pollenOlive,
		pollenRagweed: row.pollenRagweed,
		uvIndex: row.uvIndex,
		lat: row.lat,
		lon: row.lon,
		fetchedAt: row.fetchedAt
	};
}

export function getEnvDataForDate(date: string): EnvironmentalData | null {
	const row = db.select().from(environmentalData).where(eq(environmentalData.date, date)).get();
	return row ? formatRow(row) : null;
}

/** Stored weather for every day from `from` to `to` (inclusive), oldest first. Never fetches. */
export function getEnvDataInRange(from: string, to: string): EnvironmentalData[] {
	return db
		.select()
		.from(environmentalData)
		.where(and(gte(environmentalData.date, from), lte(environmentalData.date, to)))
		.orderBy(asc(environmentalData.date))
		.all()
		.map(formatRow);
}

// ─── Places ───────────────────────────────────────────────────────────────────

export interface Place {
	lat: number;
	lon: number;
}

/** About 5 km: closer places share their weather. */
const samePlace = (a: Place, b: Place) => Math.abs(a.lat - b.lat) < 0.05 && Math.abs(a.lon - b.lon) < 0.05;

/** Off until the user turns it on under Me. While off, nothing is sent to Open-Meteo. */
export function isWeatherEnabled(): boolean {
	return !!db.select({ on: userProfile.weatherEnabled }).from(userProfile).get()?.on;
}

function homePlace(): Place | null {
	const profile = db.select().from(userProfile).get();
	return profile?.locationLat != null && profile.locationLon != null ? { lat: profile.locationLat, lon: profile.locationLon } : null;
}

/** The place of each date: the day's own place if it has one, otherwise home. */
function placesFor(dates: string[]): Map<string, Place | null> {
	const home = homePlace();
	const own = new Map(
		db
			.select({ date: entries.date, lat: entries.locationLat, lon: entries.locationLon })
			.from(entries)
			.where(inArray(entries.date, dates))
			.all()
			.filter((r) => r.lat != null && r.lon != null)
			.map((r) => [r.date, { lat: r.lat!, lon: r.lon! }])
	);
	return new Map(dates.map((date) => [date, own.get(date) ?? home]));
}

// ─── Parsing (pure) ───────────────────────────────────────────────────────────

export interface DailyWeather {
	temperatureC: number | null;
	airPressureHpa: number | null;
	uvIndex: number | null;
	weatherCondition: string | null;
}

/** Open-Meteo `daily` weather (forecast or archive API) by date. */
export function parseDailyWeather(json: unknown): Map<string, DailyWeather> {
	const daily = (json as { daily?: Record<string, unknown[]> })?.daily;
	const result = new Map<string, DailyWeather>();
	if (!daily?.time) return result;
	const at = (key: string, i: number) => {
		const v = daily[key]?.[i];
		return typeof v === 'number' ? v : null;
	};
	daily.time.forEach((date, i) => {
		const max = at('temperature_2m_max', i);
		const min = at('temperature_2m_min', i);
		const code = at('weather_code', i);
		result.set(String(date), {
			temperatureC: max !== null && min !== null ? (max + min) / 2 : null,
			airPressureHpa: at('surface_pressure_mean', i),
			uvIndex: at('uv_index_max', i),
			weatherCondition: code !== null ? wmoCodeToCondition(code) : null
		});
	});
	return result;
}

export type DailyPollen = Record<'alder' | 'birch' | 'grass' | 'mugwort' | 'olive' | 'ragweed', number | null>;

/** Daily peaks from Open-Meteo's hourly air-quality pollen, by local date. */
export function parseDailyPollen(json: unknown): Map<string, DailyPollen> {
	const hourly = (json as { hourly?: Record<string, unknown[]> })?.hourly;
	const result = new Map<string, DailyPollen>();
	if (!hourly?.time) return result;
	const species = ['alder', 'birch', 'grass', 'mugwort', 'olive', 'ragweed'] as const;
	hourly.time.forEach((time, i) => {
		const date = String(time).slice(0, 10);
		const day = result.get(date) ?? { alder: null, birch: null, grass: null, mugwort: null, olive: null, ragweed: null };
		for (const s of species) {
			const v = hourly[`${s}_pollen`]?.[i];
			if (typeof v === 'number') day[s] = Math.max(day[s] ?? 0, v);
		}
		result.set(date, day);
	});
	return result;
}

// ─── Fetching ─────────────────────────────────────────────────────────────────

const DAY = 86_400_000;
const addDays = (date: string, n: number) => new Date(Date.parse(`${date}T12:00:00Z`) + n * DAY).toISOString().slice(0, 10);
const localToday = () => {
	const d = new Date();
	return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

async function getJson(url: URL): Promise<unknown> {
	const res = await fetch(url, { signal: AbortSignal.timeout(10_000) });
	if (!res.ok) throw new Error(`Open-Meteo answered ${res.status}`);
	return res.json();
}

/** Weather and pollen for one place and a date range. The recent past comes from the forecast API (with UV), older days from the archive. */
async function fetchRange(place: Place, from: string, to: string, today: string): Promise<{ weather: Map<string, DailyWeather>; pollen: Map<string, DailyPollen> }> {
	const split = addDays(today, -85);
	const daily = 'temperature_2m_max,temperature_2m_min,surface_pressure_mean,uv_index_max,weather_code';
	const ranges: Array<[string, string, string]> = [];
	if (from < split) ranges.push(['https://archive-api.open-meteo.com/v1/archive', from, to < split ? to : addDays(split, -1)]);
	if (to >= split) ranges.push(['https://api.open-meteo.com/v1/forecast', from > split ? from : split, to]);

	const weather = new Map<string, DailyWeather>();
	for (const [base, start, end] of ranges) {
		const url = new URL(base);
		url.search = new URLSearchParams({ latitude: String(place.lat), longitude: String(place.lon), daily, start_date: start, end_date: end, timezone: 'auto' }).toString();
		for (const [date, value] of parseDailyWeather(await getJson(url))) weather.set(date, value);
	}

	let pollen = new Map<string, DailyPollen>();
	try {
		const url = new URL('https://air-quality-api.open-meteo.com/v1/air-quality');
		url.search = new URLSearchParams({
			latitude: String(place.lat),
			longitude: String(place.lon),
			hourly: 'alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen',
			start_date: from,
			end_date: to,
			timezone: 'auto'
		}).toString();
		pollen = parseDailyPollen(await getJson(url));
	} catch {
		// Pollen only exists for Europe and recent years; weather alone is fine.
	}
	return { weather, pollen };
}

/**
 * Make sure every given date (not in the future) has weather for its place.
 * Today is refreshed after 3 hours, and once more after it is over. Network failures leave what is stored.
 * Does nothing while weather is turned off.
 */
export async function ensureEnvData(dates: string[]): Promise<void> {
	if (!isWeatherEnabled()) return;
	const today = localToday();
	const wanted = [...new Set(dates)].filter((d) => d <= today);
	if (!wanted.length) return;
	const places = placesFor(wanted);
	const stored = new Map(
		db.select().from(environmentalData).where(inArray(environmentalData.date, wanted)).all().map((r) => [r.date, r])
	);
	const home = homePlace();

	// Dates that need fetching, grouped by place.
	const groups = new Map<string, { place: Place; dates: string[] }>();
	for (const date of wanted) {
		const place = places.get(date);
		if (!place) continue;
		const row = stored.get(date);
		// Rows from before places were stored were fetched for home.
		const rowPlace = row && (row.lat != null && row.lon != null ? { lat: row.lat, lon: row.lon } : home);
		const fetchedAt = row ? Date.parse(`${row.fetchedAt.replace(' ', 'T')}${row.fetchedAt.includes('Z') ? '' : 'Z'}`) : 0;
		// A past day is final once fetched after it ended; before that it held a forecast for the rest of the day.
		const fresh = row && (date < today ? fetchedAt >= new Date(`${addDays(date, 1)}T00:00:00`).getTime() : Date.now() - fetchedAt < 3 * 3_600_000);
		if (row && fresh && rowPlace && samePlace(rowPlace, place)) continue;
		const key = `${place.lat.toFixed(2)},${place.lon.toFixed(2)}`;
		const group = groups.get(key) ?? { place, dates: [] };
		group.dates.push(date);
		groups.set(key, group);
	}

	for (const { place, dates: group } of groups.values()) {
		group.sort();
		// One extra day before, for the pressure change of the first day.
		let fetched;
		try {
			fetched = await fetchRange(place, addDays(group[0], -1), group.at(-1)!, today);
		} catch {
			continue;
		}
		for (const date of group) {
			const w = fetched.weather.get(date);
			if (!w) continue;
			const before = fetched.weather.get(addDays(date, -1))?.airPressureHpa ?? null;
			const p = fetched.pollen.get(date);
			const values = {
				temperatureC: w.temperatureC,
				airPressureHpa: w.airPressureHpa,
				pressureDeltaHpa: w.airPressureHpa !== null && before !== null ? w.airPressureHpa - before : null,
				weatherCondition: w.weatherCondition,
				uvIndex: w.uvIndex,
				pollenAlder: p?.alder ?? null,
				pollenBirch: p?.birch ?? null,
				pollenGrass: p?.grass ?? null,
				pollenMugwort: p?.mugwort ?? null,
				pollenOlive: p?.olive ?? null,
				pollenRagweed: p?.ragweed ?? null,
				lat: place.lat,
				lon: place.lon,
				fetchedAt: new Date().toISOString()
			};
			// Upsert: another request may have stored the day while this one was fetching.
			db.insert(environmentalData)
				.values({ id: uuidv4(), date, ...values })
				.onConflictDoUpdate({ target: environmentalData.date, set: values })
				.run();
		}
	}
}

/** Every date from `from` to `to`. */
export function datesBetween(from: string, to: string): string[] {
	const dates: string[] = [];
	for (let d = from; d <= to; d = addDays(d, 1)) dates.push(d);
	return dates;
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

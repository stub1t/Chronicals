/**
 * The phone's approximate position (Android app), and the optional daily
 * "where am I" for weather while travelling.
 */
import { registerPlugin, type PermissionState } from '@capacitor/core';
import { api } from '$lib/api.js';
import type { Entry, UserProfile } from '$lib/types/index.js';
import { toDateString } from '$lib/utils/date';

/**
 * Android's own LocationManager (android/app/.../LocationPlugin.java), so the
 * app needs no Google Play Services. Coarse location only.
 */
interface LocationPlugin {
	checkPermissions(): Promise<{ coarseLocation: PermissionState }>;
	requestPermissions(): Promise<{ coarseLocation: PermissionState }>;
	getCurrentPosition(options: { timeout: number; maximumAge: number }): Promise<{ lat: number; lon: number }>;
}
const Location = registerPlugin<LocationPlugin>('Location');

const AUTO_KEY = 'chronicals-auto-location';
/** Farther than this from home counts as away. */
const AWAY_KM = 25;

export function isAutoLocationOn(): boolean {
	try {
		return localStorage.getItem(AUTO_KEY) === 'on';
	} catch {
		return false;
	}
}

export async function setAutoLocation(on: boolean): Promise<boolean> {
	if (on && !(await requestLocationPermission())) return false;
	try {
		if (on) localStorage.setItem(AUTO_KEY, 'on');
		else localStorage.removeItem(AUTO_KEY);
	} catch {
		return false;
	}
	if (on) await updateTodaysPlace();
	return on;
}

export async function requestLocationPermission(): Promise<boolean> {
	const status = await Location.checkPermissions();
	if (status.coarseLocation === 'granted') return true;
	const asked = await Location.requestPermissions();
	return asked.coarseLocation === 'granted';
}

/** Approximate coordinates, or null when not allowed or not found in time. */
export async function currentPosition(): Promise<{ lat: number; lon: number } | null> {
	try {
		if (!(await requestLocationPermission())) return null;
		return await Location.getCurrentPosition({ timeout: 15_000, maximumAge: 3_600_000 });
	} catch {
		return null;
	}
}

export function distanceKm(a: { lat: number; lon: number }, b: { lat: number; lon: number }): number {
	const rad = (d: number) => (d * Math.PI) / 180;
	const h =
		Math.sin(rad(b.lat - a.lat) / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(rad(b.lon - a.lon) / 2) ** 2;
	return 2 * 6371 * Math.asin(Math.sqrt(h));
}

/**
 * With automatic location on: today away from home gets the current place
 * (unless the user set one by hand); back home clears an automatic place.
 */
export async function updateTodaysPlace(): Promise<void> {
	if (!isAutoLocationOn()) return;
	const date = toDateString(new Date());
	const [profileRes, entryRes] = await Promise.all([api('/api/profile'), api(`/api/entries?date=${date}`)]);
	const profile: UserProfile = await profileRes.json();
	const entry: Entry | null = await entryRes.json();
	if (!profile.weatherEnabled || entry?.locationSource === 'manual') return;
	const here = await currentPosition();
	if (!here) return;
	const home = profile.locationLat != null && profile.locationLon != null ? { lat: profile.locationLat, lon: profile.locationLon } : null;
	const away = !home || distanceKm(home, here) > AWAY_KM;
	const headers = { 'Content-Type': 'application/json' };
	if (away) {
		const location = { lat: Math.round(here.lat * 100) / 100, lon: Math.round(here.lon * 100) / 100, name: null, source: 'auto' };
		await api('/api/entries', { method: 'POST', headers, body: JSON.stringify({ date, location }) });
	} else if (entry?.locationSource === 'auto') {
		await api(`/api/entries/${entry.id}`, { method: 'PUT', headers, body: JSON.stringify({ location: null }) });
	}
}

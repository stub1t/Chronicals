<script lang="ts">
	/**
	 * The day's place, weather and pollen. The place is home unless the day has
	 * its own (travel), which can be changed here.
	 */
	import { api } from '$lib/api.js';
	import Sun from '@lucide/svelte/icons/sun';
	import CloudSun from '@lucide/svelte/icons/cloud-sun';
	import Cloud from '@lucide/svelte/icons/cloud';
	import CloudFog from '@lucide/svelte/icons/cloud-fog';
	import CloudDrizzle from '@lucide/svelte/icons/cloud-drizzle';
	import CloudRain from '@lucide/svelte/icons/cloud-rain';
	import CloudSnow from '@lucide/svelte/icons/cloud-snow';
	import CloudLightning from '@lucide/svelte/icons/cloud-lightning';
	import MapPin from '@lucide/svelte/icons/map-pin';
	import House from '@lucide/svelte/icons/house';
	import LocateFixed from '@lucide/svelte/icons/locate-fixed';
	import ArrowDown from '@lucide/svelte/icons/arrow-down';
	import ArrowUp from '@lucide/svelte/icons/arrow-up';
	import Flower2 from '@lucide/svelte/icons/flower-2';
	import Sheet from '$lib/ui/Sheet.svelte';
	import LocationSearch, { type Place } from '$lib/me/LocationSearch.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { formatNumber, t, type MessageKey } from '$lib/i18n';
	import { POLLEN, POLLEN_FIELD, pollenLevel, type PollenLevel, type PollenSpecies } from '$lib/weather/pollen.js';
	import type { DiaryDay } from './day.svelte.js';
	import type { EnvironmentalData } from '$lib/types';

	interface Props {
		day: DiaryDay;
		/** The home place from the profile, if set. */
		home: Place | null;
		/** Off: nothing is fetched and only a pointer to the setting is shown. */
		enabled: boolean;
	}

	let { day, home, enabled }: Props = $props();

	let weather = $state<EnvironmentalData | null>(null);
	let loaded = $state(false);
	let sheetOpen = $state(false);
	let locating = $state(false);

	const ownPlace = $derived(
		day.entry?.locationLat != null && day.entry.locationLon != null
			? { lat: day.entry.locationLat, lon: day.entry.locationLon, name: day.entry.locationName }
			: null
	);
	const place = $derived(ownPlace ?? home);
	const placeName = $derived(ownPlace ? (ownPlace.name ?? t('place_away')) : (home?.name ?? ''));

	// Fetch again when the day or its place changes.
	$effect(() => {
		const key = enabled && place ? `${place.lat},${place.lon}` : null;
		if (!key) return;
		loaded = false;
		api(`/api/environmental?date=${day.date}`)
			.then((res) => (res.ok ? res.json() : null))
			.then((data) => (weather = data))
			.catch(() => (weather = null))
			.finally(() => (loaded = true));
	});

	const ICONS = {
		'clear sky': Sun,
		'partly cloudy': CloudSun,
		overcast: Cloud,
		foggy: CloudFog,
		drizzle: CloudDrizzle,
		rain: CloudRain,
		'rain showers': CloudRain,
		snow: CloudSnow,
		'snow showers': CloudSnow,
		thunderstorm: CloudLightning
	} as const;

	const Icon = $derived(ICONS[(weather?.weatherCondition ?? '') as keyof typeof ICONS] ?? Cloud);
	const conditionLabel = $derived(
		weather?.weatherCondition ? t(`env_weather_${weather.weatherCondition.replaceAll(' ', '_')}` as MessageKey) : ''
	);
	const bigDrop = $derived((weather?.pressureDeltaHpa ?? 0) <= -5);
	// Every measured pollen by name, the highest first.
	const RANK = { high: 0, moderate: 1, low: 2 } as const;
	const pollen = $derived(
		weather
			? POLLEN.map((species) => ({ species, level: pollenLevel(species, weather?.[POLLEN_FIELD[species]]) }))
					.filter((p): p is { species: PollenSpecies; level: PollenLevel } => !!p.level)
					.sort((a, b) => RANK[a.level] - RANK[b.level])
			: []
	);

	async function choose(next: Place | null) {
		sheetOpen = false;
		try {
			await day.setPlace(next ? { lat: next.lat, lon: next.lon, name: next.name || null, source: 'manual' } : null);
		} catch (err) {
			toasts.show((err as Error).message, 'error');
		}
	}

	async function useCurrent() {
		locating = true;
		const { currentPosition } = await import('$lib/mobile/location.js');
		const here = await currentPosition();
		locating = false;
		if (here) await choose({ lat: Math.round(here.lat * 100) / 100, lon: Math.round(here.lon * 100) / 100, name: '' });
		else toasts.show(t('place_not_found'), 'error');
	}
</script>

{#if !enabled}
	<div class="card hint-card">
		<span class="hint">
			<CloudSun size={20} />
			<span>{t('today_weather_off')}</span>
		</span>
		<a class="change" href="/me#weather">{t('today_weather_turn_on')}</a>
	</div>
{:else if !place}
	<div class="card hint-card">
		<a class="hint" href="/me/profile">
			<MapPin size={20} />
			<span>{t('today_weather_needs_location')}</span>
		</a>
		<button type="button" class="change" onclick={() => (sheetOpen = true)}>{t('place_change')}</button>
	</div>
{:else}
	<div class="card" class:warn={bigDrop}>
		<div class="place">
			<MapPin size={15} />
			<span class="place-name">{placeName}</span>
			{#if day.entry?.locationSource === 'auto'}<span class="auto">{t('place_auto')}</span>{/if}
			<button type="button" class="change" onclick={() => (sheetOpen = true)}>{t('place_change')}</button>
		</div>
		{#if weather}
			<div class="weather">
				<span class="icon"><Icon size={26} /></span>
				<span class="main">
					<span class="temp">{weather.temperatureC !== null ? `${formatNumber(Math.round(weather.temperatureC))}°` : ''}</span>
					<span class="condition">{conditionLabel}</span>
				</span>
				{#if weather.airPressureHpa !== null}
					<span class="pressure">
						<span class="value">{formatNumber(Math.round(weather.airPressureHpa))} hPa</span>
						{#if weather.pressureDeltaHpa !== null}
							<span class="delta">
								{#if weather.pressureDeltaHpa < 0}<ArrowDown size={13} />{:else}<ArrowUp size={13} />{/if}
								{formatNumber(Math.abs(weather.pressureDeltaHpa), { maximumFractionDigits: 1 })}
							</span>
						{/if}
					</span>
				{/if}
			</div>
			{#if pollen.length}
				<div class="pollen">
					<Flower2 size={15} />
					{#each pollen as p (p.species)}
						<span class="level {p.level}">{t(`pollen_${p.species}`)} {t(`pollen_${p.level}`)}</span>
					{/each}
				</div>
			{/if}
		{:else if loaded}
			<p class="unavailable">{t('today_weather_unavailable')}</p>
		{/if}
	</div>
	{#if bigDrop}<p class="note">{t('today_pressure_drop')}</p>{/if}
{/if}

<Sheet bind:open={sheetOpen} title={t('place_title')} closeLabel={t('close')}>
	<div class="choices">
		<p class="ui-hint">{t('place_hint')}</p>
		{#if home}
			<button type="button" class="choice" class:current={!ownPlace} onclick={() => choose(null)}>
				<House size={20} />
				<span>{t('place_home', { name: home.name })}</span>
			</button>
		{/if}
		{#if __NATIVE_APP__}
			<button type="button" class="choice" disabled={locating} onclick={useCurrent}>
				<LocateFixed size={20} />
				<span>{locating ? t('place_locating') : t('place_current')}</span>
			</button>
		{/if}
		<span class="ui-label">{t('place_other')}</span>
		<LocationSearch value={null} onchange={(value) => value && choose(value)} id="day-place" />
	</div>
</Sheet>

<style>
	.card {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: var(--bg-subtle);
		color: var(--text);
	}

	.card.warn {
		background: var(--warning-soft);
	}

	.hint-card {
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.hint {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--text-muted);
		font-size: var(--text-sm);
		font-weight: 600;
		text-decoration: none;
	}

	.place {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.place-name {
		font-weight: 700;
		color: var(--text);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.auto {
		font-size: var(--text-xs);
		padding: 1px 8px;
		border-radius: var(--radius-pill);
		background: var(--bg-card);
	}

	.change {
		margin-inline-start: auto;
		min-height: 32px;
		padding: 0 12px;
		border: none;
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		color: var(--accent);
		font-size: var(--text-sm);
		font-weight: 800;
		cursor: pointer;
	}

	a.change {
		display: inline-flex;
		align-items: center;
		text-decoration: none;
	}

	.weather {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.icon {
		display: grid;
		place-items: center;
		color: var(--warning);
	}

	.main {
		flex: 1;
		display: flex;
		align-items: baseline;
		gap: var(--space-2);
	}

	.temp {
		font-size: var(--text-xl);
		font-weight: 800;
	}

	.condition {
		color: var(--text-muted);
		font-weight: 600;
	}

	.pressure {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		font-size: var(--text-sm);
		font-variant-numeric: tabular-nums;
	}

	.value {
		font-weight: 700;
	}

	.delta {
		display: flex;
		align-items: center;
		color: var(--text-muted);
	}

	.warn .delta {
		color: var(--warning);
		font-weight: 800;
	}

	.pollen {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 6px;
		color: var(--positive);
		font-size: var(--text-sm);
	}

	.level {
		padding: 2px 10px;
		border-radius: var(--radius-pill);
		font-weight: 700;
		background: var(--bg-card);
		color: var(--text-muted);
	}

	.level.moderate {
		background: var(--warning-soft);
		color: var(--warning);
	}

	.level.high {
		background: var(--danger-soft);
		color: var(--danger);
	}

	.unavailable,
	.note {
		margin: 0;
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.note {
		margin-top: var(--space-2);
	}

	.choices {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.choice {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: 52px;
		padding: 0 var(--space-4);
		border: 1.5px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-md);
		font-weight: 700;
		text-align: start;
		cursor: pointer;
	}

	.choice.current {
		border-color: var(--accent);
		background: var(--accent-soft);
	}
</style>

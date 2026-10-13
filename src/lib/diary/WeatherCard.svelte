<script lang="ts">
	/** The day's weather in one line, with a warning colour on big pressure drops. */
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
	import ArrowDown from '@lucide/svelte/icons/arrow-down';
	import ArrowUp from '@lucide/svelte/icons/arrow-up';
	import { formatNumber, t, type MessageKey } from '$lib/i18n';
	import type { EnvironmentalData } from '$lib/types';

	interface Props {
		date: string;
		hasLocation: boolean;
	}

	let { date, hasLocation }: Props = $props();

	let weather = $state<EnvironmentalData | null>(null);
	let loaded = $state(false);

	$effect(() => {
		if (!hasLocation) return;
		loaded = false;
		api(`/api/environmental?date=${date}`)
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
</script>

{#if !hasLocation}
	<a class="card hint" href="/me/profile">
		<MapPin size={20} />
		<span>{t('today_weather_needs_location')}</span>
	</a>
{:else if weather}
	<div class="card" class:warn={bigDrop}>
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
	{#if bigDrop}<p class="note">{t('today_pressure_drop')}</p>{/if}
{:else if loaded}
	<p class="note">{t('today_weather_unavailable')}</p>
{/if}

<style>
	.card {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: var(--bg-subtle);
		color: var(--text);
		text-decoration: none;
	}

	.card.warn {
		background: var(--warning-soft);
	}

	.hint {
		color: var(--text-muted);
		font-size: var(--text-sm);
		font-weight: 600;
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

	.note {
		margin: var(--space-2) 0 0;
		font-size: var(--text-sm);
		color: var(--text-muted);
	}
</style>

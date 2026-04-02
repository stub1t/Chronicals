<script lang="ts">
	import type { EnvironmentalData } from '$lib/types';
	import { t } from '$lib/stores/locale.js';

	interface Props {
		data: EnvironmentalData | null;
		loading: boolean;
	}

	let { data, loading }: Props = $props();

	function pressureArrow(delta: number | null): string {
		if (delta === null) return '';
		if (delta > 0.5) return '▲';
		if (delta < -0.5) return '▼';
		return '→';
	}

	function pressureArrowClass(delta: number | null): string {
		if (delta === null) return '';
		if (delta > 0.5) return 'arrow-up';
		if (delta < -0.5) return 'arrow-down';
		return 'arrow-stable';
	}

	function tempDisplay(c: number | null): string {
		if (c === null) return '–';
		return `${Math.round(c)}°C`;
	}

	function pressureChangeText(delta: number | null): string {
		if (delta === null) return '–';
		const prefix = delta > 0 ? '+' : '';
		const direction =
			delta > 0.5
				? t('env_pressure_rising')
				: delta < -0.5
					? t('env_pressure_falling')
					: t('env_pressure_stable');
		return `${prefix}${delta.toFixed(1)} hPa · ${direction}`;
	}

	function conditionLabel(s: string | null): string {
		if (!s) return '–';
		const mapped: Record<string, string> = {
			clear_sky: t('env_weather_clear_sky'),
			partly_cloudy: t('env_weather_partly_cloudy'),
			overcast: t('env_weather_overcast'),
			foggy: t('env_weather_foggy'),
			drizzle: t('env_weather_drizzle'),
			rain: t('env_weather_rain'),
			snow: t('env_weather_snow'),
			rain_showers: t('env_weather_rain_showers'),
			snow_showers: t('env_weather_snow_showers'),
			thunderstorm: t('env_weather_thunderstorm'),
			unknown: t('env_weather_unknown')
		};
		return mapped[s.replaceAll(' ', '_')] ?? t('env_weather_unknown');
	}
</script>

<div class="env-card">
	{#if loading}
		<div class="skeleton-row">
			<span class="skeleton skeleton-chip"></span>
			<span class="skeleton skeleton-chip"></span>
			<span class="skeleton skeleton-chip"></span>
			<span class="skeleton skeleton-wide"></span>
		</div>
		<div class="skeleton-row">
			<span class="skeleton skeleton-chip"></span>
			<span class="skeleton skeleton-chip"></span>
			<span class="skeleton skeleton-chip"></span>
			<span class="skeleton skeleton-chip"></span>
		</div>

	{:else if !data}
		<p class="no-data">
			{t('env_no_data')}
			<a href="/settings" class="settings-link">{t('env_set_location')}</a>
			{t('env_enable')}
		</p>

	{:else}
		<div class="weather-summary">
			{#if data.weatherCondition}
				<div class="condition-wrap">
					<div class="metric-label">{t('env_condition')}</div>
					<div class="condition-pill">{conditionLabel(data.weatherCondition)}</div>
				</div>
			{/if}

			<div class="stats-grid">
				<div class="metric-card">
					<div class="metric-label">{t('env_temperature')}</div>
					<div class="metric-value">{tempDisplay(data.temperatureC)}</div>
				</div>

				{#if data.airPressureHpa !== null}
					<div class="metric-card">
						<div class="metric-label">{t('env_pressure')}</div>
						<div class="metric-value">
							{Math.round(data.airPressureHpa)} hPa
							<span
								class="pressure-arrow {pressureArrowClass(data.pressureDeltaHpa)}"
								title={pressureChangeText(data.pressureDeltaHpa)}
							>
								{pressureArrow(data.pressureDeltaHpa)}
							</span>
						</div>
						<div class="metric-subvalue">{pressureChangeText(data.pressureDeltaHpa)}</div>
					</div>
				{/if}

				{#if data.uvIndex !== null}
					<div class="metric-card">
						<div class="metric-label">{t('env_uv_index')}</div>
						<div class="metric-value">{data.uvIndex.toFixed(1)}</div>
					</div>
				{/if}
			</div>
		</div>
	{/if}
</div>

<style>
	.env-card {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 12px;
		padding: 12px 14px;
		overflow: hidden;
	}

	/* Skeleton */
	.skeleton-row {
		display: flex;
		gap: 8px;
		align-items: center;
		margin-bottom: 10px;
	}

	.skeleton-row:last-child {
		margin-bottom: 0;
	}

	@keyframes shimmer {
		0% { opacity: 0.4; }
		50% { opacity: 0.8; }
		100% { opacity: 0.4; }
	}

	.skeleton {
		background: var(--bg-subtle);
		border-radius: 6px;
		animation: shimmer 1.4s ease-in-out infinite;
	}

	.skeleton-chip {
		height: 24px;
		width: 64px;
	}

	.skeleton-wide {
		height: 24px;
		width: 100px;
	}

	/* No data */
	.no-data {
		font-size: 0.85rem;
		color: var(--text-muted);
		margin: 0;
		line-height: 1.5;
	}

	.settings-link {
		color: var(--text);
		text-decoration: underline;
		text-underline-offset: 2px;
	}

	/* Data layout */
	.weather-summary {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.condition-pill {
		padding: 6px 10px;
		border-radius: 999px;
		background: var(--bg-subtle);
		border: 1px solid var(--border);
		font-size: 0.78rem;
		font-weight: 600;
		color: var(--text);
	}

	.condition-wrap {
		display: flex;
		flex-direction: column;
		gap: 6px;
		align-self: flex-start;
	}

	.stats-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
		gap: 10px;
	}

	.metric-card {
		display: flex;
		flex-direction: column;
		gap: 4px;
		padding: 10px 12px;
		border-radius: 10px;
		background: var(--bg-subtle);
		border: 1px solid var(--border);
	}

	.metric-label {
		font-size: 0.72rem;
		font-weight: 700;
		letter-spacing: 0.03em;
		text-transform: uppercase;
		color: var(--text-muted);
	}

	.metric-value {
		font-size: 1rem;
		color: var(--text);
		font-weight: 700;
		line-height: 1.2;
	}

	.metric-subvalue {
		font-size: 0.75rem;
		color: var(--text-muted);
		line-height: 1.35;
	}

	.pressure-arrow {
		font-size: 0.8rem;
		font-weight: 700;
		margin-left: 4px;
	}

	.arrow-up {
		color: #e05c5c;
	}

	.arrow-down {
		color: #5c9ee0;
	}

	.arrow-stable {
		color: var(--text-subtle);
	}


</style>

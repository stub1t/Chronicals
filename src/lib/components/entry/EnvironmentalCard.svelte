<script lang="ts">
	import type { EnvironmentalData } from '$lib/types';

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

	function capitalize(s: string | null): string {
		if (!s) return '–';
		return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
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
			No weather data.
			<a href="/settings" class="settings-link">Set location in Settings</a>
			to enable environmental tracking.
		</p>

	{:else}
		<div class="data-scroll">
			<!-- Weather row -->
			<div class="row weather-row">
				<span class="stat" title="Temperature">
					<span class="stat-label-abbr" aria-hidden="true">Temp</span>
					<span class="stat-value">{tempDisplay(data.temperatureC)}</span>
				</span>

				{#if data.airPressureHpa !== null}
					<span class="stat" title="Air pressure">
						<span class="stat-label-abbr" aria-hidden="true">hPa</span>
						<span class="stat-value">
							{Math.round(data.airPressureHpa)}
							<span
								class="pressure-arrow {pressureArrowClass(data.pressureDeltaHpa)}"
								title={data.pressureDeltaHpa !== null
									? `${data.pressureDeltaHpa > 0 ? '+' : ''}${data.pressureDeltaHpa?.toFixed(1)} hPa`
									: ''}
							>
								{pressureArrow(data.pressureDeltaHpa)}
							</span>
						</span>
					</span>
				{/if}

				{#if data.uvIndex !== null}
					<span class="stat" title="UV index">
						<span class="stat-label-abbr" aria-hidden="true">UV</span>
						<span class="stat-value">{data.uvIndex}</span>
					</span>
				{/if}

				{#if data.weatherCondition}
					<span class="stat" title="Weather condition">
						<span class="stat-value">{capitalize(data.weatherCondition)}</span>
					</span>
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
	.data-scroll {
		display: flex;
		flex-direction: column;
		gap: 8px;
		overflow-x: auto;
	}

	.row {
		display: flex;
		flex-wrap: nowrap;
		gap: 10px;
		align-items: center;
		min-width: 0;
	}

	/* Stats */
	.stat {
		display: flex;
		align-items: center;
		gap: 4px;
		white-space: nowrap;
		flex-shrink: 0;
	}

	.stat-icon {
		font-size: 1rem;
		line-height: 1;
	}

	.stat-value {
		font-size: 0.85rem;
		color: var(--text);
		font-weight: 500;
	}

	.pressure-arrow {
		font-size: 0.75rem;
		font-weight: 700;
		margin-left: 1px;
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

<script lang="ts">
	/** How am I doing: days with the symptom, intensity, the trend against the period before, and the timeline. */
	import TrendingDown from '@lucide/svelte/icons/trending-down';
	import TrendingUp from '@lucide/svelte/icons/trending-up';
	import Minus from '@lucide/svelte/icons/minus';
	import Pill from '@lucide/svelte/icons/pill';
	import Card from '$lib/ui/Card.svelte';
	import Heatmap from './charts/Heatmap.svelte';
	import MonthBars from './charts/MonthBars.svelte';
	import { dateLocale, formatNumber, t } from '$lib/i18n';
	import type { Bucket, Overview } from './engine.js';

	interface Props {
		typeName: string;
		color: string;
		overview: Overview;
		/** Day grid for up to half a year, month bars beyond. */
		days: Array<{ date: string; level: number | null }> | null;
		months: Bucket[];
	}

	let { typeName, color, overview, days, months }: Props = $props();

	const one = (n: number) => formatNumber(n, { maximumFractionDigits: 1 });
	const date = (d: string) => new Date(`${d}T12:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short' });
</script>

<Card>
	<div class="head">
		<p class="big" style="--type: {color}">{formatNumber(overview.symptomDays)}</p>
		<p class="unit">
			<strong>{t('insights_days_with', { count: overview.symptomDays, type: typeName })}</strong>
			<span>{t('insights_of_recorded', { count: overview.trackedDays })}</span>
		</p>
	</div>

	<ul class="facts">
		{#if overview.avgIntensity !== null}
			<li>{t('insights_intensity_avg', { value: one(overview.avgIntensity) })}</li>
		{/if}
		{#if overview.per30 !== null}
			<li>{t('insights_per30_short', { value: one(overview.per30) })}</li>
		{/if}
		{#if overview.reliefDays > 0}
			<li class="relief"><Pill size={14} />{t('insights_relief', { count: overview.reliefDays, total: overview.symptomDays })}</li>
		{/if}
	</ul>

	{#if overview.trend}
		<div class="trend {overview.trend}">
			<span class="trend-title">
				{#if overview.trend === 'better'}<TrendingDown size={18} />{:else if overview.trend === 'worse'}<TrendingUp size={18} />{:else}<Minus size={18} />{/if}
				{t(`insights_trend_${overview.trend}`)}
			</span>
			<span class="trend-detail">
				{t('insights_trend_detail', {
					from: date(overview.previous.from),
					to: date(overview.previous.to),
					before: one(overview.previous.per30 ?? 0),
					now: one(overview.per30 ?? 0)
				})}
			</span>
		</div>
	{/if}

	{#if days}
		<h3 class="chart-title">{t('insights_every_day')}</h3>
		<Heatmap {days} {color} {typeName} label={t('insights_timeline_label', { type: typeName, days: overview.symptomDays, total: overview.trackedDays })} />
	{:else}
		<h3 class="chart-title">{t('insights_by_month')}</h3>
		<MonthBars {months} {color} label={t('insights_timeline_label', { type: typeName, days: overview.symptomDays, total: overview.trackedDays })} />
	{/if}
</Card>

<style>
	.head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.big {
		margin: 0;
		font-size: 3rem;
		font-weight: 800;
		line-height: 1;
		color: var(--type);
		font-variant-numeric: tabular-nums;
	}

	.unit {
		margin: 0;
		display: flex;
		flex-direction: column;
		color: var(--text-muted);
	}

	.unit strong {
		color: var(--text);
		font-size: var(--text-lg);
	}

	.facts {
		list-style: none;
		margin: var(--space-3) 0 0;
		padding: 0;
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.facts li {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 4px 12px;
		border-radius: var(--radius-pill);
		background: var(--bg-subtle);
		font-size: var(--text-sm);
		font-weight: 700;
	}

	.trend {
		display: flex;
		flex-direction: column;
		gap: 2px;
		margin-top: var(--space-3);
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-md);
	}

	.trend-title {
		display: flex;
		align-items: center;
		gap: 6px;
		font-weight: 800;
	}

	.trend-detail {
		font-size: var(--text-sm);
		opacity: 0.9;
	}

	.trend.better {
		background: var(--positive-soft);
		color: var(--positive);
	}

	.trend.worse {
		background: var(--warning-soft);
		color: var(--warning);
	}

	.trend.same {
		background: var(--bg-subtle);
		color: var(--text-muted);
	}

	.chart-title {
		margin: var(--space-5) 0 var(--space-2);
		font-size: var(--text-sm);
		font-weight: 800;
		color: var(--text-muted);
	}
</style>

<script lang="ts">
	/** How am I doing: the rate, the trend against the period before, weekly bars, every day, well-being. */
	import TrendingDown from '@lucide/svelte/icons/trending-down';
	import TrendingUp from '@lucide/svelte/icons/trending-up';
	import Minus from '@lucide/svelte/icons/minus';
	import Card from '$lib/ui/Card.svelte';
	import WeekBars from './charts/WeekBars.svelte';
	import Heatmap from './charts/Heatmap.svelte';
	import Sparkline from './charts/Sparkline.svelte';
	import { formatNumber, t } from '$lib/i18n';
	import type { Overview, Week } from './engine.js';

	interface Props {
		typeName: string;
		color: string;
		overview: Overview;
		weeks: Week[];
		days: Array<{ date: string; level: number | null }>;
	}

	let { typeName, color, overview, weeks, days }: Props = $props();

	const one = (n: number) => formatNumber(n, { maximumFractionDigits: 1 });
	const wellbeing = $derived(weeks.map((w) => w.avgWellbeing));
	const hasWellbeing = $derived(wellbeing.some((v) => v !== null));
</script>

<Card>
	<div class="head">
		<p class="big" style="--type: {color}">{overview.per30 === null ? '–' : one(overview.per30)}</p>
		<p class="unit">{t('insights_per30', { type: typeName })}</p>
	</div>

	{#if overview.trend}
		<p class="trend {overview.trend}">
			{#if overview.trend === 'better'}<TrendingDown size={18} />{:else if overview.trend === 'worse'}<TrendingUp size={18} />{:else}<Minus size={18} />{/if}
			{t(`insights_trend_${overview.trend}`)}
			<span class="before">{t('insights_trend_before', { value: one(overview.previous.per30 ?? 0) })}</span>
		</p>
	{/if}

	<dl class="stats">
		<div>
			<dt>{t('insights_tracked_days')}</dt>
			<dd>{formatNumber(overview.trackedDays)}</dd>
		</div>
		<div>
			<dt>{t('insights_avg_intensity')}</dt>
			<dd>{overview.avgIntensity === null ? '–' : one(overview.avgIntensity)}</dd>
		</div>
		<div>
			<dt>{t('insights_hours')}</dt>
			<dd>{formatNumber(Math.round(overview.hours))}</dd>
		</div>
	</dl>

	<h3 class="chart-title">{t('insights_weeks', { type: typeName })}</h3>
	<WeekBars
		{weeks}
		{color}
		label={t('insights_weeks_summary', { type: typeName, weeks: weeks.length, days: overview.symptomDays })}
	/>

	<h3 class="chart-title">{t('insights_every_day')}</h3>
	<Heatmap {days} {color} label={t('insights_weeks_summary', { type: typeName, weeks: weeks.length, days: overview.symptomDays })} />

	{#if hasWellbeing}
		<h3 class="chart-title">{t('insights_wellbeing')}</h3>
		<Sparkline values={wellbeing} min={1} max={5} color="var(--positive)" label={t('insights_wellbeing')} />
		<div class="scale" aria-hidden="true"><span>{t('wellbeing_1')}</span><span>{t('wellbeing_5')}</span></div>
	{/if}
</Card>

<style>
	.head {
		display: flex;
		align-items: baseline;
		gap: var(--space-3);
		flex-wrap: wrap;
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
		flex: 1;
		min-width: 160px;
		font-weight: 700;
		color: var(--text-muted);
	}

	.trend {
		display: inline-flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 6px;
		margin: var(--space-3) 0 0;
		padding: 6px 12px;
		border-radius: var(--radius-pill);
		font-size: var(--text-sm);
		font-weight: 800;
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

	.before {
		font-weight: 600;
		opacity: 0.85;
	}

	.stats {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: var(--space-2);
		margin: var(--space-4) 0 0;
	}

	.stats div {
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-md);
		background: var(--bg-subtle);
	}

	dt {
		font-size: var(--text-xs);
		font-weight: 700;
		color: var(--text-muted);
	}

	dd {
		margin: 0;
		font-size: var(--text-lg);
		font-weight: 800;
		font-variant-numeric: tabular-nums;
	}

	.chart-title {
		margin: var(--space-5) 0 var(--space-2);
		font-size: var(--text-sm);
		font-weight: 800;
		color: var(--text-muted);
	}

	.scale {
		display: flex;
		justify-content: space-between;
		font-size: var(--text-xs);
		color: var(--text-muted);
	}
</style>

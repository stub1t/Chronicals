<script lang="ts">
	/** Every analysed type at once: how often, how strong, the trend and a small strip on one time axis. Tap for details. */
	import TrendingDown from '@lucide/svelte/icons/trending-down';
	import TrendingUp from '@lucide/svelte/icons/trending-up';
	import Minus from '@lucide/svelte/icons/minus';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import Card from '$lib/ui/Card.svelte';
	import Sparkline from './charts/Sparkline.svelte';
	import { formatNumber, localizeBuiltInText, t } from '$lib/i18n';
	import type { Bucket, Overview } from './engine.js';
	import type { EntryType } from '$lib/types';

	interface Row {
		type: EntryType;
		overview: Overview;
		/** Weeks or months, the same for every row. */
		strip: Bucket[];
	}

	interface Props {
		rows: Row[];
		trackedDays: number;
		/** Weekly or monthly well-being of all days. */
		wellbeing: Array<number | null>;
		onpick: (typeId: string) => void;
	}

	let { rows, trackedDays, wellbeing, onpick }: Props = $props();

	const one = (n: number) => formatNumber(n, { maximumFractionDigits: 1 });
	const top = $derived(Math.max(0.01, ...rows.flatMap((r) => r.strip.map((b) => b.rate ?? 0))));
</script>

{#if wellbeing.some((v) => v !== null)}
	<Card>
		<h3 class="chart-title">{t('insights_wellbeing')}</h3>
		<Sparkline values={wellbeing} min={1} max={5} color="var(--positive)" label={t('insights_wellbeing')} />
		<div class="scale" aria-hidden="true"><span>{t('wellbeing_1')}</span><span>{t('wellbeing_5')}</span></div>
	</Card>
{/if}

<Card padding="none">
	<ul class="rows">
		{#each rows as row (row.type.id)}
			<li>
				<button type="button" class="row" onclick={() => onpick(row.type.id)} style="--type: {row.type.color ?? 'var(--accent)'}">
					<EntryTypeIcon entryType={row.type} size={40} badge />
					<span class="text">
						<span class="name">{localizeBuiltInText(row.type.name)}</span>
						<span class="meta">
							{t('insights_all_days', { count: row.overview.symptomDays, total: trackedDays })}
							{#if row.overview.avgIntensity !== null}· {one(row.overview.avgIntensity)}/5{/if}
						</span>
						<span class="strip" aria-hidden="true">
							{#each row.strip as bucket (bucket.start)}
								<span class="cell" class:empty={bucket.rate === null} style="--h: {((bucket.rate ?? 0) / top) * 100}%"></span>
							{/each}
						</span>
					</span>
					{#if row.overview.trend}
						<span class="trend {row.overview.trend}" title={t(`insights_trend_${row.overview.trend}`)}>
							{#if row.overview.trend === 'better'}<TrendingDown size={18} />{:else if row.overview.trend === 'worse'}<TrendingUp size={18} />{:else}<Minus size={18} />{/if}
							<span class="sr-only">{t(`insights_trend_${row.overview.trend}`)}</span>
						</span>
					{/if}
					<ChevronRight size={18} class="flip-rtl chevron" />
				</button>
			</li>
		{/each}
	</ul>
</Card>
<p class="hint">{t('insights_all_hint')}</p>

<style>
	.chart-title {
		margin: 0 0 var(--space-2);
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

	.rows {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.rows li + li {
		border-top: 1px solid var(--border);
	}

	.row {
		width: 100%;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		border: none;
		background: none;
		color: var(--text);
		text-align: start;
		cursor: pointer;
	}

	.row:active {
		background: var(--bg-subtle);
	}

	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.name {
		font-weight: 800;
	}

	.meta {
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.strip {
		display: flex;
		align-items: flex-end;
		gap: 2px;
		height: 22px;
		margin-top: 4px;
	}

	.cell {
		flex: 1;
		height: max(2px, var(--h));
		border-radius: 2px;
		background: var(--type);
		opacity: 0.85;
	}

	.cell.empty {
		height: 2px;
		background: var(--border);
	}

	.trend {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		border-radius: 50%;
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

	.row :global(.chevron) {
		color: var(--text-subtle);
	}

	.hint {
		margin: 0;
		font-size: var(--text-sm);
		color: var(--text-muted);
	}
</style>

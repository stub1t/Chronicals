<script lang="ts">
	/** Share of recorded days with the symptom, one bar per month. Months with few recorded days are faint. */
	import { dateLocale, formatNumber } from '$lib/i18n';
	import type { Bucket } from '../engine.js';

	interface Props {
		months: Bucket[];
		color: string;
		label: string;
	}

	let { months, color, label }: Props = $props();

	const name = (start: string) => new Date(`${start}T12:00:00`).toLocaleDateString(dateLocale(), { month: 'short' });
	const labels = $derived(months.map((m) => name(m.start)));
	// Long histories: only every few months get a name, so they don't crowd.
	const every = $derived(Math.max(1, Math.ceil(months.length / 6)));
	// The year, on its own line, under the first bar and every January.
	const years = $derived(months.map((m, i) => (i === 0 || m.start.slice(5, 7) === '01' ? m.start.slice(0, 4) : '')));
</script>

<figure class="chart" style="--bar: {color}">
	<div class="bars" role="img" aria-label={label}>
		{#each months as month, i (month.start)}
			<span class="slot" title="{labels[i]}: {month.symptomDays}/{month.trackedDays}">
				{#if month.rate !== null}
					<span class="bar" class:faint={month.trackedDays < 5} style="height: {Math.max(month.rate * 100, 2)}%"></span>
				{/if}
			</span>
		{/each}
	</div>
	<div class="axis" aria-hidden="true">
		{#each months as month, i (month.start)}
			<span class="tick">{i % every === 0 ? labels[i] : ''}</span>
		{/each}
	</div>
	<div class="axis years" aria-hidden="true">
		{#each months as month, i (month.start)}
			<span class="tick">{years[i]}</span>
		{/each}
	</div>
	<span class="scale" aria-hidden="true">{formatNumber(1, { style: 'percent' })}</span>
</figure>

<style>
	.chart {
		margin: 0;
		position: relative;
	}

	.bars {
		display: flex;
		align-items: flex-end;
		gap: 3px;
		height: 110px;
		border-bottom: 1px solid var(--border);
	}

	.slot {
		flex: 1;
		height: 100%;
		display: flex;
		align-items: flex-end;
	}

	.bar {
		width: 100%;
		border-radius: 4px 4px 1px 1px;
		background: var(--bar);
	}

	.bar.faint {
		opacity: 0.35;
	}

	.axis {
		display: flex;
		gap: 3px;
		margin-top: 4px;
	}

	.years {
		margin-top: 0;
	}

	.years .tick {
		color: var(--text-subtle);
	}

	.tick {
		flex: 1;
		min-width: 0;
		overflow: visible;
		white-space: nowrap;
		font-size: var(--text-xs);
		color: var(--text-muted);
	}

	.scale {
		position: absolute;
		inset-inline-end: 0;
		top: -18px;
		font-size: var(--text-xs);
		color: var(--text-subtle);
	}
</style>

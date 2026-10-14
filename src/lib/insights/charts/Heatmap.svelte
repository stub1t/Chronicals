<script lang="ts">
	/**
	 * One square per day, a column per week (Monday on top), months named above:
	 * empty when not recorded, soft green without the symptom, the type's colour
	 * by intensity otherwise. Tap a day to see it.
	 */
	import { dateLocale, t } from '$lib/i18n';
	import { weekdayIndex } from '../dates.js';

	interface Props {
		days: Array<{ date: string; level: number | null }>;
		color: string;
		typeName: string;
		label: string;
	}

	let { days, color, typeName, label }: Props = $props();

	const lead = $derived(days.length ? weekdayIndex(days[0].date) : 0);
	const cells = $derived([...Array.from({ length: lead }, () => null), ...days]);
	const columns = $derived(Math.ceil(cells.length / 7));
	// A month name above the column where the month's first day falls.
	const monthLabels = $derived.by(() => {
		const labels: Array<{ column: number; text: string }> = [];
		cells.forEach((cell, i) => {
			if (cell && (cell.date.endsWith('-01') || i === lead)) {
				labels.push({ column: Math.floor(i / 7), text: new Date(`${cell.date}T12:00:00`).toLocaleDateString(dateLocale(), { month: 'short' }) });
			}
		});
		// Drop a label that would crowd the next one.
		return labels.filter((l, i) => !labels[i + 1] || labels[i + 1].column - l.column >= 3);
	});

	let picked = $state<{ date: string; level: number | null } | null>(null);
	const describe = (cell: { date: string; level: number | null }) => {
		const date = new Date(`${cell.date}T12:00:00`).toLocaleDateString(dateLocale(), { weekday: 'short', day: 'numeric', month: 'short' });
		const state = cell.level === null ? t('insights_day_none') : cell.level === 0 ? t('insights_day_free', { type: typeName }) : t('insights_day_level', { type: typeName, level: cell.level });
		return `${date}: ${state}`;
	};

	let scroller: HTMLDivElement | undefined = $state();
	$effect(() => {
		void days;
		if (scroller) scroller.scrollLeft = document.documentElement.dir === 'rtl' ? -scroller.scrollWidth : scroller.scrollWidth;
	});
</script>

<figure class="heatmap" style="--cell: {color}">
	<div class="scroller" bind:this={scroller}>
		<div class="months" style="grid-template-columns: repeat({columns}, 12px)" aria-hidden="true">
			{#each monthLabels as month (month.column)}
				<span style="grid-column: {month.column + 1} / span 3">{month.text}</span>
			{/each}
		</div>
		<div class="grid" role="img" aria-label={label}>
			{#each cells as cell, i (cell?.date ?? `lead-${i}`)}
				{#if cell}
					<button
						type="button"
						class="day"
						class:untracked={cell.level === null}
						class:free={cell.level === 0}
						class:picked={picked?.date === cell.date}
						style={cell.level ? `--strength: ${20 + cell.level * 16}%` : ''}
						aria-label={describe(cell)}
						onclick={() => (picked = picked?.date === cell.date ? null : cell)}
					></button>
				{:else}
					<span></span>
				{/if}
			{/each}
		</div>
	</div>
	<figcaption class="caption">
		{#if picked}
			<span class="picked-text">{describe(picked)}</span>
		{:else}
			<span class="legend" aria-hidden="true">
				<span class="day untracked"></span>{t('insights_heatmap_untracked')}
				<span class="day free"></span>{t('insights_heatmap_free')}
				<span class="day" style="--strength: 36%"></span><span class="day" style="--strength: 100%"></span>{t('insights_heatmap_strong')}
			</span>
		{/if}
	</figcaption>
</figure>

<style>
	.heatmap {
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.scroller {
		overflow-x: auto;
		scrollbar-width: none;
	}

	.months {
		display: grid;
		column-gap: 3px;
		height: 16px;
		font-size: var(--text-xs);
		color: var(--text-muted);
		white-space: nowrap;
	}

	.grid {
		display: grid;
		grid-template-rows: repeat(7, 12px);
		grid-auto-flow: column;
		grid-auto-columns: 12px;
		gap: 3px;
		width: max-content;
	}

	.day {
		display: inline-block;
		width: 12px;
		height: 12px;
		padding: 0;
		border: none;
		border-radius: 3px;
		background: color-mix(in srgb, var(--cell) var(--strength, 0%), var(--bg-card));
		cursor: pointer;
	}

	.day.untracked {
		background: transparent;
		box-shadow: inset 0 0 0 1px var(--border);
	}

	.day.free {
		background: color-mix(in srgb, var(--positive) 30%, var(--bg-card));
	}

	.day.picked {
		outline: 2px solid var(--text);
		outline-offset: 1px;
	}

	.caption {
		min-height: 20px;
		font-size: var(--text-xs);
		color: var(--text-muted);
	}

	.picked-text {
		font-weight: 700;
		color: var(--text);
	}

	.legend {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 6px;
	}

	.legend .day {
		cursor: default;
	}

	.legend .day:not(:first-child) {
		margin-inline-start: 6px;
	}

	.legend .day + .day {
		margin-inline-start: 0;
	}
</style>

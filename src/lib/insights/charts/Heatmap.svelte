<script lang="ts">
	/**
	 * One square per day, a column per week (Monday on top): empty when not
	 * tracked, soft green when tracked without the symptom, the type's colour
	 * by intensity otherwise. Scrolls to the newest week.
	 */
	import { dateLocale, t } from '$lib/i18n';
	import { weekdayIndex } from '../dates.js';

	interface Props {
		days: Array<{ date: string; level: number | null }>;
		color: string;
		label: string;
	}

	let { days, color, label }: Props = $props();

	const lead = $derived(days.length ? weekdayIndex(days[0].date) : 0);
	const cells = $derived([...Array.from({ length: lead }, () => null), ...days]);
	const format = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short' });

	let scroller: HTMLDivElement | undefined = $state();
	$effect(() => {
		void days;
		if (scroller) scroller.scrollLeft = document.documentElement.dir === 'rtl' ? -scroller.scrollWidth : scroller.scrollWidth;
	});
</script>

<figure class="heatmap" style="--cell: {color}">
	<div class="scroller" bind:this={scroller}>
		<div class="grid" role="img" aria-label={label}>
			{#each cells as cell, i (cell?.date ?? `lead-${i}`)}
				{#if cell}
					<span
						class="day"
						class:untracked={cell.level === null}
						class:free={cell.level === 0}
						style={cell.level ? `--strength: ${20 + cell.level * 16}%` : ''}
						title="{format(cell.date)}"
					></span>
				{:else}
					<span></span>
				{/if}
			{/each}
		</div>
	</div>
	<figcaption class="legend" aria-hidden="true">
		<span class="day untracked"></span>{t('insights_heatmap_untracked')}
		<span class="day free"></span>{t('insights_heatmap_free')}
		<span class="day" style="--strength: 36%"></span><span class="day" style="--strength: 100%"></span>{t('insights_heatmap_strong')}
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
		border-radius: 3px;
		background: color-mix(in srgb, var(--cell) var(--strength, 0%), var(--bg-card));
	}

	.day.untracked {
		background: transparent;
		box-shadow: inset 0 0 0 1px var(--border);
	}

	.day.free {
		background: color-mix(in srgb, var(--positive) 30%, var(--bg-card));
	}

	.legend {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 6px;
		font-size: var(--text-xs);
		color: var(--text-muted);
	}

	.legend .day:not(:first-child) {
		margin-inline-start: 6px;
	}

	.legend .day + .day {
		margin-inline-start: 0;
	}
</style>

<script lang="ts">
	/** Symptom days per week as columns (0 to 7). Weeks with few tracked days are drawn faintly. */
	import { dateLocale } from '$lib/i18n';
	import type { Week } from '../engine.js';

	interface Props {
		weeks: Week[];
		color: string;
		/** Read out instead of the bars. */
		label: string;
	}

	let { weeks, color, label }: Props = $props();

	const monthOf = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), { month: 'short' });
	// A month label under the first week that starts in that month.
	const labels = $derived(weeks.map((w, i) => (i === 0 || w.start.slice(5, 7) !== weeks[i - 1].start.slice(5, 7) ? monthOf(w.start) : '')));
</script>

<figure class="chart" style="--bar: {color}">
	<div class="bars" role="img" aria-label={label}>
		{#each weeks as week (week.start)}
			<span class="slot" title="{week.start}: {week.symptomDays}/{week.trackedDays}">
				{#if week.trackedDays > 0}
					<span
						class="bar"
						class:faint={week.trackedDays < 4}
						class:zero={week.symptomDays === 0}
						style="height: {(week.symptomDays / 7) * 100}%"
					></span>
				{/if}
			</span>
		{/each}
	</div>
	<div class="axis" aria-hidden="true">
		{#each labels as text, i (i)}
			<span class="tick">{text}</span>
		{/each}
	</div>
</figure>

<style>
	.chart {
		margin: 0;
	}

	.bars {
		display: flex;
		align-items: flex-end;
		gap: 2px;
		height: 96px;
		padding-bottom: 2px;
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
		min-height: 3px;
		border-radius: 4px 4px 1px 1px;
		background: var(--bar);
	}

	.bar.zero {
		background: color-mix(in srgb, var(--positive) 45%, transparent);
	}

	.bar.faint {
		opacity: 0.35;
	}

	.axis {
		display: flex;
		gap: 2px;
		margin-top: 4px;
	}

	.tick {
		flex: 1;
		min-width: 0;
		overflow: visible;
		white-space: nowrap;
		font-size: var(--text-xs);
		color: var(--text-muted);
	}
</style>

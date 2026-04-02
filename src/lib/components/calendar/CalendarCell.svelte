<script lang="ts">
	import type { CalendarDayData } from '$lib/types';
	import DayDot from './DayDot.svelte';

	interface Props {
		date: string;
		dayNumber: number;
		dayData: CalendarDayData | null;
		isToday: boolean;
		isSelected: boolean;
		onclick?: () => void;
	}

	let { date, dayNumber, dayData, isToday, isSelected, onclick }: Props = $props();

	let visibleDots = $derived(dayData ? dayData.dots.slice(0, 3) : []);
	let extraDots = $derived(dayData ? Math.max(0, dayData.dots.length - 3) : 0);
</script>

<button
	class="cell"
	class:today={isToday}
	class:selected={isSelected}
	class:has-data={!!dayData}
	aria-label={date}
	aria-pressed={isSelected}
	onclick={onclick}
>
	<span class="day-number" class:today-num={isToday}>{dayNumber}</span>

	<div class="indicators">
		{#if dayData?.hasHypo}
			<span class="badge badge-hypo" title="Hypoglycemia">!</span>
		{/if}
		{#if dayData?.hasMedChange}
			<span class="badge badge-med" title="Medication change">Rx</span>
		{/if}
	</div>

	{#if visibleDots.length > 0}
		<div class="dots-row">
			{#each visibleDots as dot (dot.entryTypeId + dot.color)}
				<DayDot color={dot.color} intensity={dot.intensity} entryTypeName={dot.entryTypeName} />
			{/each}
			{#if extraDots > 0}
				<span class="extra-dots">+{extraDots}</span>
			{/if}
		</div>
	{/if}
</button>

<style>
	.cell {
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: flex-start;
		gap: 2px;
		padding: 4px 2px;
		min-height: 52px;
		background: transparent;
		border: 1.5px solid transparent;
		border-radius: 8px;
		cursor: pointer;
		transition: background 0.15s, border-color 0.15s;
		width: 100%;
		font-family: inherit;
	}

	.cell:hover {
		background: var(--bg-subtle);
	}

	.cell.selected {
		border-color: var(--border);
		background: var(--bg-card);
		box-shadow: 0 0 0 2px color-mix(in srgb, var(--text) 20%, transparent);
	}

	.day-number {
		font-size: 0.85rem;
		font-weight: 400;
		color: var(--text);
		line-height: 1;
		width: 24px;
		height: 24px;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		flex-shrink: 0;
	}

	.today-num {
		background: var(--text);
		color: var(--bg);
		font-weight: 700;
	}

	.dots-row {
		display: flex;
		flex-direction: row;
		align-items: center;
		gap: 2px;
		flex-wrap: nowrap;
	}

	.extra-dots {
		font-size: 0.6rem;
		color: var(--text-muted);
		line-height: 1;
	}

	.indicators {
		position: absolute;
		top: 2px;
		right: 3px;
		display: flex;
		flex-direction: row;
		gap: 1px;
	}

	.badge {
		font-size: 0.55rem;
		font-weight: 700;
		line-height: 1;
		padding: 1px 3px;
		border-radius: 3px;
		letter-spacing: 0;
	}

	.badge-hypo {
		background: rgba(245, 158, 11, 0.15);
		color: #b45309;
	}

	.badge-med {
		background: rgba(107, 114, 128, 0.15);
		color: #4b5563;
	}

	@media (prefers-color-scheme: dark) {
		.badge-hypo { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }
		.badge-med { background: rgba(156, 163, 175, 0.15); color: #9ca3af; }
	}
</style>

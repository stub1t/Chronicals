<script lang="ts">
	import type { CalendarDayData } from '$lib/types';
	import { getDaysInMonth, getFirstDayOfMonth, toDateString } from '$lib/utils/date';
	import CalendarCell from './CalendarCell.svelte';

	interface Props {
		yearMonth: string;
		dayDataMap: Map<string, CalendarDayData>;
		selectedDate: string | null;
		ondayselect?: (date: string) => void;
		onprevmonth?: () => void;
		onnextmonth?: () => void;
	}

	let { yearMonth, dayDataMap, selectedDate, ondayselect, onprevmonth, onnextmonth }: Props = $props();

	// Locale-aware day names Mon–Sun (Jan 1 2024 was a Monday)
	const WEEKDAYS = Array.from({ length: 7 }, (_, i) =>
		new Date(2024, 0, 1 + i).toLocaleDateString('default', { weekday: 'short' })
	);
	const todayStr = toDateString(new Date());

	let daysInMonth = $derived(getDaysInMonth(yearMonth));
	let firstDayOffset = $derived(getFirstDayOfMonth(yearMonth));

	// Build grid cells: nulls for leading empty days, then day numbers
	let cells = $derived<Array<number | null>>([
		...Array(firstDayOffset).fill(null),
		...Array.from({ length: daysInMonth }, (_, i) => i + 1)
	]);

	function dateForDay(day: number): string {
		const [year, month] = yearMonth.split('-');
		return `${year}-${month}-${String(day).padStart(2, '0')}`;
	}

	// Touch swipe handling
	let touchStartX = $state(0);
	let touchStartY = $state(0);

	function onTouchStart(e: TouchEvent) {
		touchStartX = e.touches[0].clientX;
		touchStartY = e.touches[0].clientY;
	}

	function onTouchEnd(e: TouchEvent) {
		const dx = e.changedTouches[0].clientX - touchStartX;
		const dy = e.changedTouches[0].clientY - touchStartY;
		if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 40) {
			if (dx < 0) {
				onnextmonth?.();
			} else {
				onprevmonth?.();
			}
		}
	}
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="calendar-month"
	ontouchstart={onTouchStart}
	ontouchend={onTouchEnd}
>
	<!-- Weekday header -->
	<div class="weekday-header" role="row">
		{#each WEEKDAYS as day}
			<span class="weekday-label" role="columnheader">{day}</span>
		{/each}
	</div>

	<!-- Day grid -->
	<div class="day-grid" role="grid">
		{#each cells as cell, i (i)}
			{#if cell === null}
				<div class="empty-cell" role="gridcell" aria-hidden="true"></div>
			{:else}
				{@const date = dateForDay(cell)}
				{@const dayData = dayDataMap.get(date) ?? null}
				<div role="gridcell">
					<CalendarCell
						{date}
						dayNumber={cell}
						{dayData}
						isToday={date === todayStr}
						isSelected={date === selectedDate}
						onclick={() => ondayselect?.(date)}
					/>
				</div>
			{/if}
		{/each}
	</div>
</div>

<style>
	.calendar-month {
		width: 100%;
		user-select: none;
	}

	.weekday-header {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		margin-bottom: 4px;
	}

	.weekday-label {
		text-align: center;
		font-size: 0.72rem;
		font-weight: 600;
		color: var(--text-subtle);
		text-transform: uppercase;
		letter-spacing: 0.04em;
		padding: 4px 0;
	}

	.day-grid {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: 2px;
	}

	.empty-cell {
		min-height: 52px;
	}
</style>

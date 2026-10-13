<script lang="ts">
	/**
	 * A month as a grid of soft day cells. A day is tinted by its strongest
	 * symptom (stronger = more colour), shows a dot per entry type, and falls
	 * back to the well-being colour on symptom-free days.
	 */
	import { getDaysInMonth, getFirstDayOfMonth, getWeekdayLabels } from '$lib/utils/date';
	import { dateLocale } from '$lib/i18n';
	import type { Entry } from '$lib/types';

	interface Props {
		yearMonth: string;
		entries: Entry[];
		selected: string | null;
		today: string;
		onselect: (date: string) => void;
	}

	let { yearMonth, entries, selected, today, onselect }: Props = $props();

	const FACE_COLORS = ['#ec8a9a', '#f2ac72', '#e9c66b', '#94d29c', '#62c393'];
	const weekdays = getWeekdayLabels();

	const cells = $derived.by(() => {
		const byDate = new Map(entries.map((e) => [e.date, e]));
		const blanks = Array.from({ length: getFirstDayOfMonth(yearMonth) }, () => null);
		const days = Array.from({ length: getDaysInMonth(yearMonth) }, (_, i) => {
			const date = `${yearMonth}-${String(i + 1).padStart(2, '0')}`;
			const entry = byDate.get(date);
			const logs = entry?.logs ?? [];
			const symptoms = logs.filter((l) => l.entryType?.category === 'symptom');
			const worst = [...symptoms].sort((a, b) => (b.intensityCache ?? 3) - (a.intensityCache ?? 3))[0];
			const colors = [...new Set(logs.map((l) => l.entryType?.color).filter((c): c is string => !!c))].slice(0, 3);
			return {
				date,
				day: i + 1,
				tint: worst?.entryType?.color ?? (entry?.wellbeing ? FACE_COLORS[entry.wellbeing - 1] : null),
				strength: worst ? 0.12 + (worst.intensityCache ?? 3) * 0.07 : 0.16,
				colors,
				label: new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' })
			};
		});
		return [...blanks, ...days];
	});
</script>

<div class="grid">
	<div class="row weekdays" aria-hidden="true">
		{#each weekdays as weekday (weekday)}
			<span class="weekday">{weekday}</span>
		{/each}
	</div>
	<div class="days">
		{#each cells as cell, i (cell?.date ?? `blank-${i}`)}
			{#if cell}
				<button
					type="button"
					class="cell"
					class:today={cell.date === today}
					class:selected={cell.date === selected}
					class:tinted={!!cell.tint}
					aria-pressed={cell.date === selected}
					aria-current={cell.date === today ? 'date' : undefined}
					aria-label={cell.label}
					style="--tint: {cell.tint ?? 'transparent'}; --strength: {cell.strength}"
					onclick={() => onselect(cell.date)}
				>
					<span class="number">{cell.day}</span>
					<span class="dots" aria-hidden="true">
						{#each cell.colors as color (color)}
							<span class="dot" style="background: {color}"></span>
						{/each}
					</span>
				</button>
			{:else}
				<span class="cell blank" aria-hidden="true"></span>
			{/if}
		{/each}
	</div>
</div>

<style>
	.weekdays,
	.days {
		display: grid;
		grid-template-columns: repeat(7, minmax(0, 1fr));
		gap: 6px;
	}

	.weekday {
		text-align: center;
		font-size: var(--text-xs);
		font-weight: 800;
		color: var(--text-subtle);
		padding-bottom: var(--space-1);
	}

	.cell {
		aspect-ratio: 1 / 1.08;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 4px;
		border: 2px solid transparent;
		border-radius: var(--radius-sm);
		background: var(--bg-card);
		color: var(--text);
		cursor: pointer;
		transition: transform 0.3s var(--ease-spring);
	}

	.cell:active {
		transform: scale(0.9);
	}

	.cell.tinted {
		background: color-mix(in srgb, var(--tint) calc(var(--strength) * 100%), var(--bg-card));
	}

	.cell.blank {
		background: transparent;
		cursor: default;
	}

	.number {
		font-size: var(--text-sm);
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}

	.today .number {
		display: grid;
		place-items: center;
		width: 26px;
		height: 26px;
		border-radius: 50%;
		background: var(--accent);
		color: var(--on-accent);
	}

	.selected {
		border-color: var(--accent);
		box-shadow: 0 3px 12px color-mix(in srgb, var(--accent) 25%, transparent);
	}

	.dots {
		display: flex;
		gap: 3px;
		min-height: 6px;
	}

	.dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
	}
</style>

<script lang="ts">
	import { WELLBEING_COLORS } from '$lib/ui/wellbeing.js';
	/** The last seven days at a glance: the day's worst symptom colour, or how you felt. */
	import { dateLocale } from '$lib/i18n';
	import { toDateString } from '$lib/utils/date';
	import type { Entry } from '$lib/types';

	interface Props {
		entries: Entry[];
		today: string;
		label: string;
	}

	let { entries, today, label }: Props = $props();


	const days = $derived.by(() => {
		const byDate = new Map(entries.map((e) => [e.date, e]));
		return Array.from({ length: 7 }, (_, i) => {
			const d = new Date(`${today}T12:00:00`);
			d.setDate(d.getDate() - (6 - i));
			const date = toDateString(d);
			const entry = byDate.get(date);
			const worst = (entry?.logs ?? [])
				.filter((l) => l.entryType?.category === 'symptom')
				.sort((a, b) => (b.intensityCache ?? 0) - (a.intensityCache ?? 0))[0];
			return {
				date,
				weekday: d.toLocaleDateString(dateLocale(), { weekday: 'narrow' }),
				day: d.getDate(),
				color: worst?.entryType?.color ?? null,
				face: entry?.wellbeing ? WELLBEING_COLORS[entry.wellbeing - 1] : null,
				logged: !!entry
			};
		});
	});
</script>

<ol class="strip" aria-label={label}>
	{#each days as d (d.date)}
		<li>
			<a href="/calendar/day/{d.date}" class="day" class:today={d.date === today}>
				<span class="weekday">{d.weekday}</span>
				<span
					class="mark"
					class:empty={!d.logged}
					style="--mark: {d.color ?? d.face ?? 'var(--border)'}"
				>{d.day}</span>
			</a>
		</li>
	{/each}
</ol>

<style>
	.strip {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(7, minmax(0, 1fr));
		gap: var(--space-1);
	}

	.day {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 6px;
		padding: var(--space-2) 0;
		border-radius: var(--radius-md);
		color: var(--text-muted);
		text-decoration: none;
		font-size: var(--text-xs);
		font-weight: 700;
	}

	.today {
		background: var(--accent-soft);
		color: var(--text);
	}

	.mark {
		display: grid;
		place-items: center;
		width: 34px;
		height: 34px;
		border-radius: 50%;
		background: color-mix(in srgb, var(--mark) 35%, var(--bg-card));
		border: 2px solid var(--mark);
		color: var(--text);
		font-size: var(--text-sm);
		font-weight: 800;
		font-variant-numeric: tabular-nums;
	}

	.mark.empty {
		background: transparent;
		border-style: dashed;
		color: var(--text-subtle);
	}
</style>

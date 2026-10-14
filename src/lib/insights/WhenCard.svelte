<script lang="ts">
	/** When does it happen: weekdays, time of day, and around the period when there is one. */
	import Card from '$lib/ui/Card.svelte';
	import RateBars from './charts/RateBars.svelte';
	import { dateLocale, t } from '$lib/i18n';
	import { addDays } from './dates.js';
	import type { CycleResult, DayPart, Share } from './engine.js';

	interface Props {
		color: string;
		weekdays: Share[];
		dayParts: Record<DayPart, number>;
		cycle: CycleResult | null;
	}

	let { color, weekdays, dayParts, cycle }: Props = $props();

	// 2026-10-12 is a Monday.
	const weekdayName = (i: number) => new Date(`${addDays('2026-10-12', i)}T12:00:00`).toLocaleDateString(dateLocale(), { weekday: 'short' });
	const top = $derived(Math.max(...weekdays.map((w) => w.rate)));
	const detail = (s: Share) => t('insights_days_of', { count: s.symptomDays, total: s.trackedDays });
	const partsTotal = $derived(Object.values(dayParts).reduce((a, b) => a + b, 0));
</script>

<Card>
	<h3 class="sub first">{t('insights_weekdays')}</h3>
	<RateBars
		{color}
		rows={weekdays.map((w, i) => ({ label: weekdayName(i), value: w.rate, detail: detail(w), highlight: w.rate === top && top > 0, faint: w.trackedDays < 3 }))}
	/>

	{#if partsTotal > 0}
		<h3 class="sub">{t('insights_day_parts')}</h3>
		<RateBars
			{color}
			count
			rows={(['night', 'morning', 'afternoon', 'evening'] as const).map((part) => ({
				label: t(`insights_part_${part}`),
				value: dayParts[part],
				highlight: dayParts[part] === Math.max(...Object.values(dayParts))
			}))}
		/>
	{/if}

	{#if cycle}
		<h3 class="sub">{t('insights_cycle')}</h3>
		<RateBars
			{color}
			rows={cycle.phases.map((p) => ({
				label: t(`insights_phase_${p.phase}`),
				value: p.rate,
				detail: detail(p),
				highlight: p.label === 'likely' || p.label === 'possible',
				faint: p.trackedDays < 5
			}))}
		/>
		<p class="note">{t('insights_cycle_note', { count: cycle.cycles })}</p>
	{/if}
</Card>

<style>
	.sub {
		margin: var(--space-5) 0 var(--space-3);
		font-size: var(--text-sm);
		font-weight: 800;
		color: var(--text-muted);
	}

	.sub.first {
		margin-top: 0;
	}

	.note {
		margin: var(--space-2) 0 0;
		font-size: var(--text-xs);
		color: var(--text-muted);
	}
</style>

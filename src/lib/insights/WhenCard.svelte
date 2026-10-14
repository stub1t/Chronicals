<script lang="ts">
	/** When does it happen: a weekday or time of day only when one stands out, and the cycle when there is one. */
	import CalendarDays from '@lucide/svelte/icons/calendar-days';
	import Clock from '@lucide/svelte/icons/clock';
	import Card from '$lib/ui/Card.svelte';
	import RateBars from './charts/RateBars.svelte';
	import { dateLocale, formatNumber, t } from '$lib/i18n';
	import { addDays } from './dates.js';
	import type { CycleResult, DayPart, Share } from './engine.js';

	interface Props {
		typeName: string;
		color: string;
		weekday: { weekday: number; rate: number; otherRate: number } | null;
		time: { part: DayPart; count: number; total: number } | null;
		cycle: CycleResult | null;
	}

	let { typeName, color, weekday, time, cycle }: Props = $props();

	// 2026-10-12 is a Monday.
	const weekdayName = (i: number) => new Date(`${addDays('2026-10-12', i)}T12:00:00`).toLocaleDateString(dateLocale(), { weekday: 'long' });
	const percent = (v: number) => formatNumber(v, { style: 'percent', maximumFractionDigits: 0 });
	const detail = (s: Share) => t('insights_days_of', { count: s.symptomDays, total: s.trackedDays });
</script>

<Card>
	{#if weekday || time}
		<ul class="sentences">
			{#if weekday}
				<li>
					<CalendarDays size={18} />
					<span>{t('insights_weekday_sentence', { type: typeName, weekday: weekdayName(weekday.weekday), rate: percent(weekday.rate), other: percent(weekday.otherRate) })}</span>
				</li>
			{/if}
			{#if time}
				<li>
					<Clock size={18} />
					<span>{t('insights_time_sentence', { part: t(`insights_at_${time.part}`), count: time.count, total: time.total })}</span>
				</li>
			{/if}
		</ul>
	{:else}
		<p class="muted">{t('insights_when_nothing')}</p>
	{/if}

	{#if cycle}
		<h3 class="sub">{t('insights_cycle')}</h3>
		<RateBars
			{color}
			rows={cycle.phases.map((p) => ({
				label: t(`insights_phase_${p.phase}`),
				value: p.rate,
				detail: detail(p),
				highlight: p.label === 'likely' || p.label === 'watch',
				faint: p.trackedDays < 5
			}))}
		/>
		<p class="note">{t('insights_cycle_note', { count: cycle.cycles })}</p>
	{/if}
</Card>

<style>
	.sentences {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.sentences li {
		display: flex;
		gap: var(--space-2);
		align-items: flex-start;
		line-height: 1.45;
	}

	.sentences :global(svg) {
		flex-shrink: 0;
		margin-top: 2px;
		color: var(--text-muted);
	}

	.muted {
		margin: 0;
		color: var(--text-muted);
	}

	.sub {
		margin: var(--space-5) 0 var(--space-3);
		font-size: var(--text-sm);
		font-weight: 800;
		color: var(--text-muted);
	}

	.note {
		margin: var(--space-2) 0 0;
		font-size: var(--text-xs);
		color: var(--text-muted);
	}
</style>

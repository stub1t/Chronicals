<script lang="ts">
	import { isTracked } from '$lib/diary/day.svelte.js';
	import { WELLBEING_COLORS } from '$lib/ui/wellbeing.js';
	import { goto } from '$app/navigation';
	import CalendarHeader from '$lib/calendar/CalendarHeader.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import { dateLocale, formatTime, localizeBuiltInText, t } from '$lib/i18n';
	import { toDateString } from '$lib/utils/date';
	import type { Entry } from '$lib/types';

	interface Props {
		data: { today: string; days: string[]; entries: Entry[] };
	}

	let { data }: Props = $props();

	const byDate = $derived(new Map(data.entries.map((e) => [e.date, e])));

	function shift(days: number) {
		const d = new Date(`${data.days[0]}T12:00:00`);
		d.setDate(d.getDate() + days);
		goto(`/calendar/week?week=${toDateString(d)}`, { replaceState: true, noScroll: true, keepFocus: true });
	}

	const fmt = (date: string, options: Intl.DateTimeFormatOptions) =>
		new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), options);
	const time = (iso: string) => formatTime(iso);
	const title = $derived(
		`${fmt(data.days[0], { day: 'numeric', month: 'short' })} – ${fmt(data.days[6], { day: 'numeric', month: 'short', year: 'numeric' })}`
	);
</script>

<div class="page">
	<CalendarHeader
		{title}
		view="week"
		onprev={() => shift(-7)}
		onnext={() => shift(7)}
		ontoday={() => goto('/calendar/week', { replaceState: true })}
		showToday={!data.days.includes(data.today)}
		prevLabel={t('prev_week')}
		nextLabel={t('next_week')}
	/>

	<ol class="days">
		{#each data.days as date (date)}
			{@const entry = byDate.get(date)}
			{@const logs = [...(entry?.logs ?? [])].sort((a, b) => a.startTime.localeCompare(b.startTime))}
			<li>
				<a class="day" class:today={date === data.today} class:future={date > data.today} class:empty={!isTracked(entry)} href="/calendar/day/{date}">
					<span class="date">
						<span class="weekday">{fmt(date, { weekday: 'short' })}</span>
						<span class="number">{fmt(date, { day: 'numeric' })}</span>
						{#if entry?.wellbeing}
							<span class="face" style="background: {WELLBEING_COLORS[entry.wellbeing - 1]}"></span>
						{/if}
					</span>
					<span class="content">
						{#if logs.length}
							{#each logs as log (log.id)}
								<span class="log" style="--type: {log.entryType?.color ?? 'var(--accent)'}">
									<EntryTypeIcon entryType={log.entryType} size={22} />
									<span class="name">{localizeBuiltInText(log.entryType?.name ?? '')}</span>
									<span class="time">{time(log.startTime)}</span>
									{#if log.intensityCache}<span class="level">{log.intensityCache}/5</span>{/if}
								</span>
							{/each}
						{:else if isTracked(entry)}
							<span class="muted">{t('calendar_day_no_symptoms')}</span>
						{/if}
						{#if entry?.triggers?.length}
							<span class="triggers">{entry.triggers.map((tr) => localizeBuiltInText(tr.triggerName)).join(' · ')}</span>
						{/if}
					</span>
				</a>
			</li>
		{/each}
	</ol>
</div>

<style>
	.page {
		max-width: 640px;
		margin: 0 auto;
		padding: var(--space-5) var(--space-4) var(--space-8);
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.days {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.day {
		display: flex;
		gap: var(--space-4);
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: var(--bg-card);
		box-shadow: var(--shadow-sm);
		color: var(--text);
		text-decoration: none;
		border: 2px solid transparent;
	}

	.day.today {
		border-color: var(--accent);
	}

	.day.empty {
		background: transparent;
		box-shadow: none;
		padding-block: var(--space-2);
	}

	.day.empty.today {
		background: var(--bg-card);
	}

	.day.future {
		opacity: 0.55;
	}

	.date {
		width: 44px;
		flex-shrink: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
	}

	.weekday {
		font-size: var(--text-xs);
		font-weight: 800;
		color: var(--text-muted);
		text-transform: uppercase;
	}

	.number {
		font-size: var(--text-xl);
		font-weight: 800;
		font-variant-numeric: tabular-nums;
	}

	.face {
		width: 12px;
		height: 12px;
		border-radius: 50%;
	}

	.content {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 6px;
		justify-content: center;
	}

	.log {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: 4px 10px 4px 6px;
		border-radius: var(--radius-pill);
		background: color-mix(in srgb, var(--type) 12%, var(--bg-card));
		align-self: flex-start;
		max-width: 100%;
	}

	.name {
		font-weight: 700;
		font-size: var(--text-sm);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.time {
		font-size: var(--text-xs);
		color: var(--text-muted);
		font-variant-numeric: tabular-nums;
	}

	.level {
		font-size: var(--text-xs);
		font-weight: 800;
		color: var(--type);
	}

	.muted,
	.triggers {
		font-size: var(--text-sm);
		color: var(--text-subtle);
	}
</style>

<script lang="ts">
	import { isTracked } from '$lib/diary/day.svelte.js';
	import { WELLBEING_COLORS } from '$lib/ui/wellbeing.js';
	/** A day at a glance under the month: how it felt, entries, triggers, note, and a way in. */
	import Card from '$lib/ui/Card.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import ArrowRight from '@lucide/svelte/icons/arrow-right';
	import { dateLocale, formatTime, localizeBuiltInText, t } from '$lib/i18n';
	import type { Entry } from '$lib/types';

	interface Props {
		date: string;
		entry: Entry | null;
	}

	let { date, entry }: Props = $props();

	const title = $derived(
		new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' })
	);
	const time = (iso: string) => formatTime(iso);
	const logs = $derived([...(entry?.logs ?? [])].sort((a, b) => a.startTime.localeCompare(b.startTime)));
</script>

<Card>
	<div class="head">
		<h2 class="title">{title}</h2>
		{#if entry?.wellbeing}
			<span class="face" style="background: {WELLBEING_COLORS[entry.wellbeing - 1]}" title={t(`wellbeing_${entry.wellbeing as 1 | 2 | 3 | 4 | 5}`)}></span>
		{/if}
	</div>

	{#if logs.length}
		<ul class="logs">
			{#each logs as log (log.id)}
				<li style="--type: {log.entryType?.color ?? 'var(--accent)'}">
					<EntryTypeIcon entryType={log.entryType} size={30} badge />
					<span class="name">{localizeBuiltInText(log.entryType?.name ?? '')}</span>
					<span class="time">{time(log.startTime)}{log.endTime ? ` – ${time(log.endTime)}` : ''}</span>
					{#if log.intensityCache}<span class="level">{log.intensityCache}/5</span>{/if}
				</li>
			{/each}
		</ul>
	{:else}
		<p class="empty">{isTracked(entry) ? t('calendar_day_no_symptoms') : t('calendar_day_empty')}</p>
	{/if}

	{#if entry?.triggers?.length}
		<p class="triggers">{entry.triggers.map((tr) => localizeBuiltInText(tr.triggerName)).join(' · ')}</p>
	{/if}
	{#if entry?.note}
		<p class="note">{entry.note}</p>
	{/if}

	<a class="open" href="/calendar/day/{date}">
		{isTracked(entry) ? t('calendar_open_day') : t('calendar_add_to_day')}
		<ArrowRight size={18} class="flip-rtl" />
	</a>
</Card>

<style>
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
		margin-bottom: var(--space-3);
	}

	.title {
		margin: 0;
		font-size: var(--text-lg);
		font-weight: 800;
	}

	.face {
		width: 18px;
		height: 18px;
		border-radius: 50%;
		flex-shrink: 0;
	}

	.logs {
		list-style: none;
		margin: 0 0 var(--space-3);
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.logs li {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.name {
		flex: 1;
		font-weight: 700;
	}

	.time,
	.level {
		font-size: var(--text-sm);
		color: var(--text-muted);
		font-variant-numeric: tabular-nums;
	}

	.level {
		font-weight: 800;
		color: var(--type);
	}

	.empty,
	.triggers,
	.note {
		margin: 0 0 var(--space-3);
		color: var(--text-muted);
		font-size: var(--text-sm);
	}

	.note {
		font-style: italic;
	}

	.open {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		min-height: 44px;
		border-radius: var(--radius-pill);
		background: var(--accent-soft);
		color: var(--accent);
		font-weight: 800;
		text-decoration: none;
	}
</style>

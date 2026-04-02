<script lang="ts">
	import { intensityColor, formatTime, formatDuration } from '$lib/utils/date.js';
	import { localizeBuiltInText } from '$lib/stores/locale.js';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import type { EntryTypeLog } from '$lib/types/index.js';

	let {
		log,
		hourHeight = 64,
		dayStart = 0,
		onclick
	}: {
		log: EntryTypeLog;
		hourHeight?: number;
		dayStart?: number; // offset hour (e.g. 0 = midnight, 6 = 6am)
		onclick?: (log: EntryTypeLog) => void;
	} = $props();

	const color = $derived(log.entryType?.color ?? '#94a3b8');
	const intensity = $derived(log.intensityCache ?? 3);
	const isEvent = $derived(log.entryType?.category === 'event');

	const topPx = $derived(() => {
		const start = new Date(log.startTime);
		const hours = start.getHours() + start.getMinutes() / 60 - dayStart;
		return Math.max(0, hours * hourHeight);
	});

	const heightPx = $derived(() => {
		if (!log.endTime) return Math.max(30, hourHeight * 0.5);
		const durationMs = new Date(log.endTime).getTime() - new Date(log.startTime).getTime();
		const durationHours = durationMs / 3600000;
		return Math.max(28, durationHours * hourHeight);
	});

	const bgColor = $derived(
		isEvent ? color : intensityColor(color, intensity)
	);
</script>

<!-- svelte-ignore a11y_click_events_have_key_events a11y_no_static_element_interactions -->
<div
	class="event-block"
	class:event-type={isEvent}
	style="top: {topPx()}px; height: {heightPx()}px; border-left: 3px solid {color}; background: {bgColor}20;"
	onclick={() => onclick?.(log)}
	title="{log.entryType?.name ? localizeBuiltInText(log.entryType.name) : 'Entry'} — {formatTime(log.startTime)}"
>
	<EntryTypeIcon entryType={log.entryType ?? null} size={14} class="event-icon" />
	{#if heightPx() > 36}
		<div class="event-body">
			<span class="event-name">{log.entryType?.name ? localizeBuiltInText(log.entryType.name) : 'Entry'}</span>
			<span class="event-time">{formatTime(log.startTime)}{log.endTime ? ` – ${formatDuration(log.startTime, log.endTime)}` : ''}</span>
		</div>
	{/if}
</div>

<style>
	.event-block {
		position: absolute;
		left: 2px;
		right: 2px;
		border-radius: 4px;
		padding: 3px 5px;
		cursor: pointer;
		display: flex;
		align-items: flex-start;
		gap: 4px;
		overflow: hidden;
		transition: filter 0.1s;
		z-index: 1;
	}

	.event-block:hover {
		filter: brightness(1.1);
	}

	.event-type {
		border-style: dashed;
	}

	.event-icon {
		flex-shrink: 0;
	}

	.event-body {
		display: flex;
		flex-direction: column;
		gap: 1px;
		min-width: 0;
	}

	.event-name {
		font-size: 0.7rem;
		font-weight: 600;
		color: var(--text);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.event-time {
		font-size: 0.6rem;
		color: var(--text-muted);
	}
</style>

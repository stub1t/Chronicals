<script lang="ts">
	import type { Entry, EntryType, EnvironmentalData, EntryTypeLog } from '$lib/types';
	import { formatTime, formatDuration, formatFullDate } from '$lib/utils/date';
	import { intensityColor } from '$lib/utils/date';
	import { localizeBuiltInText, t } from '$lib/stores/locale.js';
	import EnvironmentalCard from '$lib/components/entry/EnvironmentalCard.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';

	interface Props {
		date: string;
		entry: Entry | null;
		entryTypes: EntryType[];
		envData: EnvironmentalData | null;
		envLoading?: boolean;
		onadd: (hour: number) => void;
		onedit?: (logId: string) => void;
	}

	let { date, entry, entryTypes, envData, envLoading = false, onadd, onedit }: Props = $props();

	const HOURS = Array.from({ length: 24 }, (_, i) => i);
	const HOUR_HEIGHT = 64; // px per hour

	function getEntryType(id: string): EntryType | undefined {
		return entryTypes.find((et) => et.id === id);
	}

	function logBlockStyle(log: EntryTypeLog): string {
		const start = new Date(log.startTime);
		const startHour = start.getHours() + start.getMinutes() / 60;
		const top = startHour * HOUR_HEIGHT;

		let height = HOUR_HEIGHT;
		if (log.endTime) {
			const end = new Date(log.endTime);
			const endHour = end.getHours() + end.getMinutes() / 60;
			height = Math.max((endHour - startHour) * HOUR_HEIGHT, 36);
		} else {
			height = Math.max(HOUR_HEIGHT * 0.75, 36);
		}

		const et = getEntryType(log.entryTypeId);
		const color = et?.color ?? '#888888';
		const intensity = log.intensityCache ?? 3;
		const bg = intensityColor(color, intensity);

		return `top: ${top}px; height: ${height}px; background: ${bg}; border-left: 3px solid ${color};`;
	}

	let regularLogs = $derived.by<EntryTypeLog[]>(() => {
		if (!entry?.logs) return [];
		return entry.logs;
	});

	function handleEditClick(e: MouseEvent | KeyboardEvent, logId: string) {
		e.stopPropagation();
		onedit?.(logId);
	}
</script>

<div class="day-view">
	<!-- Header -->
	<div class="day-header">
		<h2 class="day-title">{formatFullDate(date)}</h2>
	</div>

	<!-- Environmental card (always expanded in day view) -->
	<div class="env-section">
		<EnvironmentalCard data={envData} loading={envLoading} />
	</div>

	<!-- Timeline -->
	<div class="timeline-container">
		{#if !entry?.logs || entry.logs.length === 0}
			<div class="empty-overlay">
				<span class="empty-icon" aria-hidden="true">📋</span>
			<p class="empty-text">{t('day_no_entries')}</p>
			<button class="empty-add-btn" onclick={() => onadd(9)}>
				{t('day_add_entry')}
			</button>
			</div>
		{/if}

		<div class="timeline" style={`--hour-h: ${HOUR_HEIGHT}px`}>
			<!-- Hour rows -->
			{#each HOURS as hour (hour)}
				<!-- svelte-ignore a11y_click_events_have_key_events -->
				<div
					class="hour-row"
					role="button"
					tabindex="0"
					aria-label={t('day_add_entry_at', { time: `${String(hour).padStart(2, '0')}:00` })}
					onclick={() => onadd(hour)}
					onkeydown={(e) => { if (e.key === 'Enter' || e.key === ' ') onadd(hour); }}
				>
					<div class="hour-label" aria-hidden="true">
						{#if hour > 0}
							{String(hour).padStart(2, '0')}:00
						{/if}
					</div>
					<div class="hour-area"></div>
				</div>
			{/each}

			<!-- Event blocks column -->
			<div class="events-column">
				{#each regularLogs as log (log.id)}
					{@const et = getEntryType(log.entryTypeId)}
					<!-- svelte-ignore a11y_click_events_have_key_events -->
					<div
						class="event-block"
						style={logBlockStyle(log)}
						role="button"
						tabindex="0"
						aria-label={t('day_event_at', { name: et ? localizeBuiltInText(et.name) : t('drawer_unknown'), time: formatTime(log.startTime) })}
						onclick={(e) => handleEditClick(e, log.id)}
						onkeydown={(e) => { if (e.key === 'Enter') handleEditClick(e, log.id); }}
					>
						<div class="event-header">
							<EntryTypeIcon entryType={et ?? null} size={16} />
							<span class="event-name">{et ? localizeBuiltInText(et.name) : t('drawer_unknown')}</span>
						</div>
						<div class="event-meta">
							<span class="event-time">{formatTime(log.startTime)}</span>
							{#if log.endTime}
								<span class="event-duration">· {formatDuration(log.startTime, log.endTime)}</span>
							{/if}
							{#if log.intensityCache !== null}
								<span class="event-intensity">· {log.intensityCache}/5</span>
							{/if}
						</div>
					</div>
				{/each}
			</div>
		</div>
	</div>
</div>

<style>
	.day-view {
		display: flex;
		flex-direction: column;
		height: 100%;
		background: var(--bg);
		overflow: hidden;
	}

	.day-header {
		padding: 12px 16px 8px;
		flex-shrink: 0;
		border-bottom: 1px solid var(--border);
	}

	.day-title {
		font-size: 1rem;
		font-weight: 700;
		color: var(--text);
		margin: 0;
	}

	.env-section {
		padding: 12px 16px;
		flex-shrink: 0;
		border-bottom: 1px solid var(--border);
	}

	/* Timeline */
	.timeline-container {
		flex: 1;
		overflow-y: auto;
		overscroll-behavior: contain;
		position: relative;
	}

	.empty-overlay {
		position: absolute;
		top: 50%;
		left: 50%;
		transform: translate(-50%, -50%);
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
		z-index: 10;
		text-align: center;
		pointer-events: none;
	}

	.empty-icon {
		font-size: 2.5rem;
		opacity: 0.5;
	}

	.empty-text {
		font-size: 0.88rem;
		color: var(--text-muted);
		margin: 0;
	}

	.empty-add-btn {
		padding: 8px 18px;
		background: var(--text);
		color: var(--bg);
		border: none;
		border-radius: 18px;
		font-family: inherit;
		font-size: 0.82rem;
		font-weight: 600;
		cursor: pointer;
		pointer-events: auto;
		transition: opacity 0.15s;
	}

	.empty-add-btn:hover {
		opacity: 0.85;
	}

	.timeline {
		position: relative;
		padding-left: 56px;
	}

	.hour-row {
		display: flex;
		height: var(--hour-h);
		border-bottom: 1px solid var(--bg-subtle);
		cursor: pointer;
		box-sizing: border-box;
		position: relative;
	}

	.hour-row:hover .hour-area {
		background: var(--bg-subtle);
	}

	.hour-label {
		position: absolute;
		left: -56px;
		width: 50px;
		text-align: right;
		font-size: 0.62rem;
		color: var(--text-subtle);
		top: 0;
		transform: translateY(-0.5em);
		pointer-events: none;
		white-space: nowrap;
		padding-right: 6px;
	}

	.hour-area {
		flex: 1;
		transition: background 0.12s;
	}

	/* Events column (absolute, overlaid on timeline) */
	.events-column {
		position: absolute;
		top: 0;
		left: 56px;
		right: 0;
		pointer-events: none;
	}

	.event-block {
		position: absolute;
		left: 4px;
		right: 8px;
		border-radius: 7px;
		padding: 5px 8px;
		overflow: hidden;
		display: flex;
		flex-direction: column;
		cursor: pointer;
		z-index: 1;
		pointer-events: auto;
		transition: filter 0.15s;
		box-sizing: border-box;
	}

	.event-block:hover {
		filter: brightness(1.1);
		z-index: 2;
	}

	.event-block:focus {
		outline: 2px solid var(--text);
		outline-offset: 1px;
	}

	.event-header {
		display: flex;
		align-items: center;
		gap: 5px;
	}

	.event-name {
		font-size: 0.78rem;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.event-meta {
		display: flex;
		gap: 4px;
		align-items: center;
		flex-wrap: nowrap;
		margin-top: 2px;
	}

	.event-time,
	.event-duration,
	.event-intensity {
		font-size: 0.68rem;
		opacity: 0.85;
		white-space: nowrap;
	}
</style>

<script lang="ts">
	import type { Entry, EntryType, Trigger, EntryTypeLog } from '$lib/types';
	import { formatTime } from '$lib/utils/date';
	import { intensityColor } from '$lib/utils/date';

	interface Props {
		weekStart: string; // 'YYYY-MM-DD' Monday
		entries: Entry[];
		entryTypes: EntryType[];
		allTriggers: Trigger[];
		ondayclick: (date: string, hour: number) => void;
		onprevweek?: () => void;
		onnextweek?: () => void;
	}

	let { weekStart, entries, entryTypes, allTriggers, ondayclick, onprevweek, onnextweek }: Props = $props();

	const HOURS = Array.from({ length: 24 }, (_, i) => i);
	const DAY_LABELS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];
	const HOUR_HEIGHT = 56; // px per hour row

	// Touch swipe
	let touchStartX = $state(0);
	let touchStartY = $state(0);

	// Build array of 7 dates for the week
	let weekDates = $derived.by<string[]>(() => {
		const result: string[] = [];
		const base = new Date(weekStart + 'T00:00:00');
		for (let i = 0; i < 7; i++) {
			const d = new Date(base);
			d.setDate(base.getDate() + i);
			result.push(d.toISOString().split('T')[0]);
		}
		return result;
	});

	const todayStr = new Date().toISOString().split('T')[0];

	// Build a map: date -> Entry
	let entryByDate = $derived.by<Map<string, Entry>>(() => {
		const map = new Map<string, Entry>();
		for (const e of entries) {
			map.set(e.date, e);
		}
		return map;
	});

	// Get entry type by id
	function getEntryType(id: string): EntryType | undefined {
		return entryTypes.find((et) => et.id === id);
	}

	// Day number label from date string
	function dayNum(dateStr: string): number {
		return parseInt(dateStr.split('-')[2], 10);
	}

	// Compute pixel position/height for a log block
	function logBlockStyle(log: EntryTypeLog, et: EntryType | undefined): string {
		const start = new Date(log.startTime);
		const startHour = start.getHours() + start.getMinutes() / 60;
		const top = startHour * HOUR_HEIGHT;

		let height = HOUR_HEIGHT; // default 1h if no end
		if (log.endTime) {
			const end = new Date(log.endTime);
			const endHour = end.getHours() + end.getMinutes() / 60;
			height = Math.max((endHour - startHour) * HOUR_HEIGHT, 30);
		} else {
			height = Math.max(HOUR_HEIGHT * 0.75, 30);
		}

		const color = et?.color ?? '#888888';
		const intensity = log.intensityCache ?? 3;
		const bg = intensityColor(color, intensity);

		return `top: ${top}px; height: ${height}px; background: ${bg}; border-left: 3px solid ${color};`;
	}

	// Special events (hypo / med change) from log category
	function isHypo(log: EntryTypeLog): boolean {
		const et = getEntryType(log.entryTypeId);
		return et?.name?.toLowerCase().includes('hypo') ?? false;
	}

	function isMedChange(log: EntryTypeLog): boolean {
		const et = getEntryType(log.entryTypeId);
		return et?.name?.toLowerCase().includes('med') ?? false;
	}

	function getSpecialLogs(date: string): { hypo: boolean; medChange: boolean } {
		const entry = entryByDate.get(date);
		if (!entry?.logs) return { hypo: false, medChange: false };
		return {
			hypo: entry.logs.some(isHypo),
			medChange: entry.logs.some(isMedChange)
		};
	}

	function getRegularLogs(date: string): EntryTypeLog[] {
		const entry = entryByDate.get(date);
		if (!entry?.logs) return [];
		return entry.logs.filter((l) => !isHypo(l) && !isMedChange(l));
	}

	function onTouchStart(e: TouchEvent) {
		touchStartX = e.touches[0].clientX;
		touchStartY = e.touches[0].clientY;
	}

	function onTouchEnd(e: TouchEvent) {
		const dx = e.changedTouches[0].clientX - touchStartX;
		const dy = e.changedTouches[0].clientY - touchStartY;
		if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 50) {
			if (dx < 0) {
				dispatchWeekChange('next');
			} else {
				dispatchWeekChange('prev');
			}
		}
	}

	function dispatchWeekChange(dir: 'prev' | 'next') {
		if (dir === 'prev') onprevweek?.();
		else onnextweek?.();
	}

	function handleCellClick(date: string, hour: number) {
		ondayclick(date, hour);
	}
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="week-view"
	ontouchstart={onTouchStart}
	ontouchend={onTouchEnd}
>
	<!-- Fixed header -->
	<div class="week-header">
		<!-- Empty corner for time labels -->
		<div class="time-corner" aria-hidden="true"></div>
		<!-- Day columns header -->
		{#each weekDates as date, i (date)}
			{@const special = getSpecialLogs(date)}
			<div class="day-header" class:today={date === todayStr}>
				<span class="day-label">{DAY_LABELS[i]}</span>
				<span class="day-num" class:today-num={date === todayStr}>{dayNum(date)}</span>
				<!-- Special event badges -->
				<div class="special-badges" aria-hidden="true">
					{#if special.hypo}
						<span class="special-badge special-badge-hypo" title="Hypoglycemia">!</span>
					{/if}
					{#if special.medChange}
						<span class="special-badge special-badge-med" title="Medication change">Rx</span>
					{/if}
				</div>
			</div>
		{/each}
	</div>

	<!-- Scrollable time grid -->
	<div class="grid-scroll">
		<div class="time-grid" style={`--hour-h: ${HOUR_HEIGHT}px`}>
			<!-- Time labels column -->
			<div class="time-labels" aria-hidden="true">
				{#each HOURS as hour (hour)}
					<div class="time-label">
						{#if hour > 0}
							<span>{String(hour).padStart(2, '0')}:00</span>
						{/if}
					</div>
				{/each}
			</div>

			<!-- Day columns -->
			{#each weekDates as date, colIdx (date)}
				<div
					class="day-column"
					class:today={date === todayStr}
					role="grid"
					aria-label={date}
				>
					<!-- Hour rows (click targets) -->
					{#each HOURS as hour (hour)}
						<!-- svelte-ignore a11y_click_events_have_key_events -->
						<div
							class="hour-cell"
							role="gridcell"
							tabindex="0"
							aria-label={`${date} ${String(hour).padStart(2, '0')}:00`}
							onclick={() => handleCellClick(date, hour)}
						></div>
					{/each}

					<!-- Event blocks (absolutely positioned) -->
					{#each getRegularLogs(date) as log (log.id)}
						{@const et = getEntryType(log.entryTypeId)}
						<div
							class="event-block"
							style={logBlockStyle(log, et)}
							title={`${et?.name ?? ''} – ${formatTime(log.startTime)}`}
							role="button"
							tabindex="0"
						>
							<span class="event-icon" aria-hidden="true">{et?.icon ?? '●'}</span>
							<span class="event-name">{et?.name ?? ''}</span>
							<span class="event-time">{formatTime(log.startTime)}</span>
						</div>
					{/each}
				</div>
			{/each}
		</div>
	</div>
</div>

<style>
	.week-view {
		display: flex;
		flex-direction: column;
		height: 100%;
		background: var(--bg);
		overflow: hidden;
		user-select: none;
	}

	/* Header */
	.week-header {
		display: grid;
		grid-template-columns: 44px repeat(7, 1fr);
		border-bottom: 1px solid var(--border);
		background: var(--bg);
		flex-shrink: 0;
		position: sticky;
		top: 0;
		z-index: 10;
	}

	.time-corner {
		border-right: 1px solid var(--border);
	}

	.day-header {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 6px 2px 4px;
		border-right: 1px solid var(--border);
		gap: 1px;
	}

	.day-header:last-child {
		border-right: none;
	}

	.day-label {
		font-size: 0.68rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		color: var(--text-subtle);
	}

	.day-header.today .day-label {
		color: var(--text);
	}

	.day-num {
		font-size: 0.88rem;
		font-weight: 500;
		color: var(--text);
		width: 26px;
		height: 26px;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
	}

	.today-num {
		background: var(--text);
		color: var(--bg);
		font-weight: 700;
	}

	.special-badges {
		display: flex;
		gap: 1px;
		min-height: 14px;
	}

	.special-badge {
		font-size: 0.55rem;
		font-weight: 700;
		line-height: 1;
		padding: 1px 3px;
		border-radius: 3px;
	}

	.special-badge-hypo {
		background: rgba(245, 158, 11, 0.15);
		color: #b45309;
	}

	.special-badge-med {
		background: rgba(107, 114, 128, 0.15);
		color: #4b5563;
	}

	@media (prefers-color-scheme: dark) {
		.special-badge-hypo { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }
		.special-badge-med { background: rgba(156, 163, 175, 0.15); color: #9ca3af; }
	}

	/* Grid scroll container */
	.grid-scroll {
		flex: 1;
		overflow-y: auto;
		overscroll-behavior: contain;
	}

	/* Time grid */
	.time-grid {
		display: grid;
		grid-template-columns: 44px repeat(7, 1fr);
		position: relative;
	}

	/* Time labels */
	.time-labels {
		border-right: 1px solid var(--border);
		position: sticky;
		left: 0;
		background: var(--bg);
		z-index: 2;
	}

	.time-label {
		height: var(--hour-h);
		display: flex;
		align-items: flex-start;
		justify-content: flex-end;
		padding: 0 6px;
		box-sizing: border-box;
		border-bottom: 1px solid var(--bg-subtle);
	}

	.time-label span {
		font-size: 0.6rem;
		color: var(--text-subtle);
		transform: translateY(-0.5em);
		display: block;
		white-space: nowrap;
	}

	/* Day columns */
	.day-column {
		position: relative;
		border-right: 1px solid var(--border);
	}

	.day-column:last-child {
		border-right: none;
	}

	.day-column.today {
		background: color-mix(in srgb, var(--text) 3%, transparent);
	}

	/* Hour cells */
	.hour-cell {
		height: var(--hour-h);
		border-bottom: 1px solid var(--bg-subtle);
		box-sizing: border-box;
		cursor: pointer;
		transition: background 0.12s;
	}

	.hour-cell:hover {
		background: var(--bg-subtle);
	}

	/* Event blocks */
	.event-block {
		position: absolute;
		left: 2px;
		right: 2px;
		border-radius: 5px;
		padding: 3px 5px;
		overflow: hidden;
		display: flex;
		flex-direction: column;
		cursor: pointer;
		z-index: 1;
		transition: filter 0.15s;
		box-sizing: border-box;
	}

	.event-block:hover {
		filter: brightness(1.08);
		z-index: 2;
	}

	.event-block:focus {
		outline: 2px solid var(--text);
		outline-offset: 1px;
	}

	.event-icon {
		font-size: 0.75rem;
		line-height: 1;
		flex-shrink: 0;
	}

	.event-name {
		font-size: 0.65rem;
		font-weight: 600;
		color: inherit;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		line-height: 1.2;
	}

	.event-time {
		font-size: 0.6rem;
		opacity: 0.8;
		white-space: nowrap;
	}
</style>

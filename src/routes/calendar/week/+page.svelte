<script lang="ts">
	import { browser } from '$app/environment';
	import type { Entry, EntryType, Trigger } from '$lib/types';
	import { formatDayMonth, toDateString, toYearMonth } from '$lib/utils/date';
	import { dateLocale, t } from '$lib/stores/locale.js';
	import CalendarWeek from '$lib/components/calendar/CalendarWeek.svelte';
	import EntryDrawer from '$lib/components/entry/EntryDrawer.svelte';

	interface Props {
		data: {
			entryTypes: EntryType[];
			allTriggers: Trigger[];
		};
	}

	let { data }: Props = $props();

	// Compute the Monday of a given date
	function getMondayOf(dateStr: string): string {
		const d = new Date(dateStr + 'T00:00:00');
		const day = d.getDay(); // 0 = Sun
		const offset = day === 0 ? -6 : 1 - day; // shift to Monday
		d.setDate(d.getDate() + offset);
		return d.toISOString().split('T')[0];
	}

	function formatWeekLabel(weekStart: string): string {
		const start = new Date(weekStart + 'T00:00:00');
		const end = new Date(weekStart + 'T00:00:00');
		end.setDate(start.getDate() + 6);
		const startLabel = formatDayMonth(weekStart);
		const endLabel = end.toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' });
		return `${startLabel} – ${endLabel}`;
	}

	// Default weekStart from URL param or current week
	let initialWeekStart = (() => {
		if (browser) {
			const param = new URL(window.location.href).searchParams.get('week');
			if (param && /^\d{4}-\d{2}-\d{2}$/.test(param)) return getMondayOf(param);
		}
		return getMondayOf(toDateString(new Date()));
	})();

	let weekStart = $state(initialWeekStart);
	let entries = $state<Entry[]>([]);
	let loadingEntries = $state(false);
	let selectedDate = $state<string | null>(null);
	let selectedHour = $state(9);

	$effect(() => {
		if (!browser) return;
		fetchWeekEntries(weekStart);
	});

	async function fetchWeekEntries(ws: string) {
		loadingEntries = true;
		try {
			const start = new Date(ws + 'T00:00:00');
			const end = new Date(ws + 'T00:00:00');
			end.setDate(start.getDate() + 6);

			const startMonth = toYearMonth(start);
			const endMonth = toYearMonth(end);

			const fetches: Promise<Entry[]>[] = [];

			const res1 = fetch(`/api/entries?month=${startMonth}`).then((r) => (r.ok ? r.json() : []));
			fetches.push(res1);

			if (startMonth !== endMonth) {
				const res2 = fetch(`/api/entries?month=${endMonth}`).then((r) => (r.ok ? r.json() : []));
				fetches.push(res2);
			}

			const results = await Promise.all(fetches);
			const all: Entry[] = results.flat();

			// Filter to entries within the week range
			const startStr = ws;
			const endStr = end.toISOString().split('T')[0];
			entries = all.filter((e) => e.date >= startStr && e.date <= endStr);
		} catch (err) {
			console.error('Failed to fetch week entries', err);
		} finally {
			loadingEntries = false;
		}
	}

	function prevWeek() {
		const d = new Date(weekStart + 'T00:00:00');
		d.setDate(d.getDate() - 7);
		weekStart = d.toISOString().split('T')[0];
	}

	function nextWeek() {
		const d = new Date(weekStart + 'T00:00:00');
		d.setDate(d.getDate() + 7);
		weekStart = d.toISOString().split('T')[0];
	}

	function goToCurrentWeek() {
		weekStart = getMondayOf(toDateString(new Date()));
	}

	function isCurrentWeek(): boolean {
		return weekStart === getMondayOf(toDateString(new Date()));
	}

	function handleDayClick(date: string, hour: number) {
		selectedDate = date;
		selectedHour = hour;
	}

	function handleDrawerClose() {
		selectedDate = null;
		fetchWeekEntries(weekStart);
	}
</script>

<div class="page">
	<!-- Header -->
	<header class="week-page-header">
			<div class="nav-row">
				<button class="nav-btn" onclick={prevWeek} aria-label={t('prev_week')}>
				<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
					<path d="M10 3L5 8l5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
				</svg>
			</button>

			<div class="nav-center">
				<span class="week-label">{formatWeekLabel(weekStart)}</span>
				{#if !isCurrentWeek()}
						<button class="today-btn" onclick={goToCurrentWeek}>{t('this_week')}</button>
				{/if}
			</div>

				<button class="nav-btn" onclick={nextWeek} aria-label={t('next_week')}>
				<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
					<path d="M6 3l5 5-5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
				</svg>
			</button>
		</div>

		<div class="view-tabs">
				<a href="/calendar" class="view-tab">{t('month_view')}</a>
				<span class="view-tab active">{t('week_view')}</span>
		</div>
	</header>

	<!-- Loading bar -->
	{#if loadingEntries}
		<div class="loading-bar" aria-hidden="true"></div>
	{/if}

	<!-- Week grid -->
	<main class="week-main">
		<CalendarWeek
			{weekStart}
			{entries}
			entryTypes={data.entryTypes}
			allTriggers={data.allTriggers}
			ondayclick={handleDayClick}
			onprevweek={prevWeek}
			onnextweek={nextWeek}
		/>
	</main>
</div>

<!-- Entry drawer -->
{#if selectedDate}
	<EntryDrawer
		date={selectedDate}
		entryTypes={data.entryTypes}
		allTriggers={data.allTriggers}
		onclose={handleDrawerClose}
	/>
{/if}

<style>
	.page {
		display: flex;
		flex-direction: column;
		height: 100dvh;
		background: var(--bg);
		overflow: hidden;
	}

	.week-page-header {
		flex-shrink: 0;
		background: var(--bg);
		border-bottom: 1px solid var(--border);
		padding: 8px 12px 6px;
	}

	.nav-row {
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.nav-center {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
	}

	.week-label {
		font-size: 0.9rem;
		font-weight: 600;
		color: var(--text);
		white-space: nowrap;
	}

	.nav-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 34px;
		height: 34px;
		border-radius: 8px;
		background: transparent;
		border: none;
		color: var(--text);
		cursor: pointer;
		transition: background 0.15s;
		flex-shrink: 0;
	}

	.nav-btn:hover {
		background: var(--bg-subtle);
	}

	.today-btn {
		font-size: 0.7rem;
		font-weight: 500;
		color: var(--text-muted);
		background: transparent;
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 1px 8px;
		cursor: pointer;
		font-family: inherit;
		line-height: 1.6;
		transition: background 0.15s, color 0.15s;
	}

	.today-btn:hover {
		background: var(--bg-subtle);
		color: var(--text);
	}

	.view-tabs {
		display: flex;
		gap: 4px;
		align-self: flex-start;
		margin-top: 4px;
	}

	.view-tab {
		font-size: 0.78rem;
		font-weight: 500;
		padding: 4px 12px;
		border-radius: 12px;
		border: 1px solid var(--border);
		background: transparent;
		color: var(--text-muted);
		text-decoration: none;
		cursor: pointer;
		transition: background 0.12s, color 0.12s;
	}

	.view-tab.active {
		background: var(--text);
		color: var(--bg);
		border-color: var(--text);
	}

	.view-tab:not(.active):hover {
		background: var(--bg-subtle);
		color: var(--text);
	}

	/* Loading bar */
	.loading-bar {
		height: 2px;
		background: linear-gradient(90deg, transparent, var(--text-muted), transparent);
		background-size: 200% 100%;
		animation: slide 1.2s linear infinite;
		flex-shrink: 0;
	}

	@keyframes slide {
		0% { background-position: 200% center; }
		100% { background-position: -200% center; }
	}

	/* Main grid area */
	.week-main {
		flex: 1;
		overflow: hidden;
		min-height: 0;
	}
</style>

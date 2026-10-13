<script lang="ts">
	import { api } from '$lib/api.js';
	import { browser } from '$app/environment';
	import type { CalendarDayData, EntryType, Trigger } from '$lib/types';
	import { toYearMonth, toDateString, prevMonth, nextMonth } from '$lib/utils/date';
	import { t } from '$lib/stores/locale.js';
	import MonthNav from '$lib/components/calendar/MonthNav.svelte';
	import CalendarMonth from '$lib/components/calendar/CalendarMonth.svelte';
	import EntryDrawer from '$lib/components/entry/EntryDrawer.svelte';

	interface Props {
		data: {
			entryTypes: EntryType[];
			allTriggers: Trigger[];
		};
	}

	let { data }: Props = $props();

	const today = new Date();
	let yearMonth = $state(toYearMonth(today));
	let selectedDate = $state<string | null>(null);
	let dotData = $state<CalendarDayData[]>([]);
	let loading = $state(false);

	let dayDataMap = $derived.by<Map<string, CalendarDayData>>(() => {
		const map = new Map<string, CalendarDayData>();
		for (const d of dotData) {
			map.set(d.date, d);
		}
		return map;
	});

	$effect(() => {
		if (!browser) return;
		fetchDotData(yearMonth);
	});

	async function fetchDotData(ym: string) {
		loading = true;
		try {
			const res = await api(`/api/entries?month=${ym}&dots=1`);
			if (res.ok) {
				dotData = await res.json();
			}
		} catch (e) {
			console.error('Failed to fetch dot data', e);
		} finally {
			loading = false;
		}
	}

	function handlePrevMonth() {
		yearMonth = prevMonth(yearMonth);
	}

	function handleNextMonth() {
		yearMonth = nextMonth(yearMonth);
	}

	function handleToday() {
		yearMonth = toYearMonth(new Date());
	}

	function handleDaySelect(date: string) {
		selectedDate = date;
	}

	function handleDrawerClose() {
		selectedDate = null;
		// Refresh dots in case new entries were added
		fetchDotData(yearMonth);
	}
</script>

<div class="page">
	<header class="page-header">
		<div class="view-tabs">
				<span class="view-tab active" aria-current="page">{t('month_view')}</span>
				<a href="/calendar/week" class="view-tab">{t('week_view')}</a>
		</div>
		<MonthNav
			{yearMonth}
			onprev={handlePrevMonth}
			onnext={handleNextMonth}
			ontoday={handleToday}
		/>
	</header>

	<main class="page-body">
		{#if loading}
				<div class="loading-indicator" aria-live="polite" aria-busy="true">
					<span class="sr-only">{t('loading_calendar')}</span>
				</div>
		{/if}

		<CalendarMonth
			{yearMonth}
			dayDataMap={dayDataMap}
			{selectedDate}
			ondayselect={handleDaySelect}
			onprevmonth={handlePrevMonth}
			onnextmonth={handleNextMonth}
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
		min-height: 100dvh;
		background: var(--bg);
		padding: 0 0 env(safe-area-inset-bottom);
	}

	.page-header {
		padding: 8px 16px 8px;
		border-bottom: 1px solid var(--border);
		background: var(--bg);
		position: sticky;
		top: 0;
		z-index: 10;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}

	.view-tabs {
		display: flex;
		gap: 4px;
		align-self: flex-start;
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
		font-family: inherit;
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

	.page-body {
		flex: 1;
		padding: 12px 12px 16px;
		position: relative;
	}

	.loading-indicator {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		height: 2px;
		background: linear-gradient(90deg, transparent, var(--text-muted), transparent);
		background-size: 200% 100%;
		animation: slide 1.2s linear infinite;
		z-index: 5;
	}

	@keyframes slide {
		0% { background-position: 200% center; }
		100% { background-position: -200% center; }
	}

	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		padding: 0;
		overflow: hidden;
		clip: rect(0, 0, 0, 0);
		white-space: nowrap;
		border: 0;
	}

</style>

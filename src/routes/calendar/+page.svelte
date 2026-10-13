<script lang="ts">
	import { goto } from '$app/navigation';
	import CalendarHeader from '$lib/calendar/CalendarHeader.svelte';
	import MonthGrid from '$lib/calendar/MonthGrid.svelte';
	import DaySummary from '$lib/calendar/DaySummary.svelte';
	import { formatMonthLabel, nextMonth, prevMonth, toYearMonth } from '$lib/utils/date';
	import { t } from '$lib/i18n';
	import type { Entry } from '$lib/types';

	interface Props {
		data: { today: string; yearMonth: string; selected: string | null; entries: Entry[] };
	}

	let { data }: Props = $props();

	function go(params: { month: string; day?: string | null }) {
		const query = new URLSearchParams({ month: params.month });
		if (params.day) query.set('day', params.day);
		goto(`/calendar?${query}`, { replaceState: true, noScroll: true, keepFocus: true });
	}

	let touchX: number | null = null;
	function onTouchEnd(e: TouchEvent) {
		if (touchX === null) return;
		const dx = e.changedTouches[0].clientX - touchX;
		touchX = null;
		if (Math.abs(dx) < 60) return;
		const rtl = document.documentElement.dir === 'rtl';
		go({ month: (dx < 0) !== rtl ? nextMonth(data.yearMonth) : prevMonth(data.yearMonth) });
	}

	const selectedEntry = $derived(data.entries.find((e) => e.date === data.selected) ?? null);
</script>

<div class="page">
	<CalendarHeader
		title={formatMonthLabel(data.yearMonth)}
		view="month"
		onprev={() => go({ month: prevMonth(data.yearMonth) })}
		onnext={() => go({ month: nextMonth(data.yearMonth) })}
		ontoday={() => go({ month: toYearMonth(new Date()), day: data.today })}
		showToday={!data.today.startsWith(data.yearMonth)}
		prevLabel={t('prev_month')}
		nextLabel={t('next_month')}
	/>

	<!-- Swiping is a shortcut; the header buttons do the same. -->
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div class="month" ontouchstart={(e) => (touchX = e.touches[0].clientX)} ontouchend={onTouchEnd}>
		<MonthGrid
			yearMonth={data.yearMonth}
			entries={data.entries}
			selected={data.selected}
			today={data.today}
			onselect={(day) => go({ month: data.yearMonth, day })}
		/>
	</div>

	{#if data.selected}
		<DaySummary date={data.selected} entry={selectedEntry} />
	{/if}
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

	.month {
		touch-action: pan-y;
	}
</style>

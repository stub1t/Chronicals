<script lang="ts">
	import { goto } from '$app/navigation';
	import CalendarDay from '$lib/components/calendar/CalendarDay.svelte';
	import EntryDrawer from '$lib/components/entry/EntryDrawer.svelte';
	import type { EntryType, Trigger } from '$lib/types/index.js';

	import type { Entry } from '$lib/types/index.js';

	let { data } = $props();

	let showDrawer = $state(false);
	let drawerHour = $state(9);
	// svelte-ignore state_referenced_locally
	let liveEntry = $state<Entry | null>(data.entry as Entry | null);

	async function refreshEntry() {
		const res = await fetch(`/api/entries?month=${data.date.slice(0, 7)}`);
		if (res.ok) {
			const entries: Entry[] = await res.json();
			liveEntry = entries.find((e) => e.date === data.date) ?? null;
		}
	}

	function prevDay() {
		const d = new Date(data.date);
		d.setDate(d.getDate() - 1);
		goto(`/calendar/day/${d.toISOString().split('T')[0]}`);
	}

	function nextDay() {
		const d = new Date(data.date);
		d.setDate(d.getDate() + 1);
		goto(`/calendar/day/${d.toISOString().split('T')[0]}`);
	}

	function formatDayHeader(date: string) {
		return new Date(date + 'T12:00:00').toLocaleDateString('default', {
			weekday: 'long',
			month: 'long',
			day: 'numeric'
		});
	}
</script>

<div class="page">
	<header class="page-header">
		<a href="/calendar" class="back-btn">‹</a>
		<div class="day-nav">
			<button onclick={prevDay} class="nav-btn">‹</button>
			<h1 class="day-title">{formatDayHeader(data.date)}</h1>
			<button onclick={nextDay} class="nav-btn">›</button>
		</div>
		<a href="/calendar/week" class="view-switch">Week</a>
	</header>

	<CalendarDay
		date={data.date}
		entry={liveEntry}
		entryTypes={data.entryTypes as EntryType[]}
		envData={data.envData}
		onadd={(hour) => { drawerHour = hour; showDrawer = true; }}
	/>

	{#if showDrawer}
		<EntryDrawer
			date={data.date}
			entryTypes={data.entryTypes as EntryType[]}
			allTriggers={data.allTriggers as Trigger[]}
			onclose={async () => { showDrawer = false; await refreshEntry(); }}
		/>
	{/if}
</div>

<style>
	.page {
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	.page-header {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.75rem 1rem;
		border-bottom: 1px solid var(--border);
		background: var(--bg-card);
	}

	.back-btn,
	.view-switch {
		color: var(--text-muted);
		text-decoration: none;
		font-size: 0.875rem;
		padding: 0.25rem 0.5rem;
	}

	.day-nav {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.75rem;
	}

	.day-title {
		margin: 0;
		font-size: 0.95rem;
		font-weight: 600;
	}

	.nav-btn {
		background: none;
		border: none;
		font-size: 1.25rem;
		color: var(--text-muted);
		cursor: pointer;
		padding: 0.25rem;
	}
</style>

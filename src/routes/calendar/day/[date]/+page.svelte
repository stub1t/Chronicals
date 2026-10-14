<script lang="ts">
	import ArrowLeft from '@lucide/svelte/icons/arrow-left';
	import ChevronLeft from '@lucide/svelte/icons/chevron-left';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import DayView from '$lib/diary/DayView.svelte';
	import { DiaryDay } from '$lib/diary/day.svelte.js';
	import { dateLocale, t } from '$lib/i18n';
	import { toDateString } from '$lib/utils/date';
	import type { Entry, EntryType, Medication, Trigger, UserProfile } from '$lib/types';

	interface Props {
		data: {
			date: string;
			today: string;
			entryTypes: EntryType[];
			medications: Medication[];
			triggers: Trigger[];
			profile: UserProfile;
			entry: Entry | null;
			typeRanking: string[];
			triggerRanking: string[];
		};
	}

	let { data }: Props = $props();

	// A new DiaryDay per date; the page component is reused when moving between days.
	const day = $derived(new DiaryDay(data.date, data.entry));
	// svelte-ignore state_referenced_locally
	let triggers = $state(data.triggers);

	function shifted(days: number): string {
		const d = new Date(`${data.date}T12:00:00`);
		d.setDate(d.getDate() + days);
		return toDateString(d);
	}

	const noon = $derived(new Date(`${data.date}T12:00:00`));
	const weekday = $derived(noon.toLocaleDateString(dateLocale(), { weekday: 'long' }));
	const title = $derived(noon.toLocaleDateString(dateLocale(), { day: 'numeric', month: 'long', year: 'numeric' }));
</script>

<div class="page">
	<header class="header">
		<a class="back" href="/calendar?month={data.date.slice(0, 7)}&day={data.date}">
			<ArrowLeft size={18} class="flip-rtl" />
			{t('nav_calendar')}
		</a>
		<div class="period">
			<a class="icon-button" href="/calendar/day/{shifted(-1)}" aria-label={t('prev_day')} data-sveltekit-replacestate>
				<ChevronLeft size={22} class="flip-rtl" />
			</a>
			<h1 class="title">
				<span class="weekday">{weekday}</span>
				{title}
			</h1>
			<a class="icon-button" href="/calendar/day/{shifted(1)}" aria-label={t('next_day')} data-sveltekit-replacestate>
				<ChevronRight size={22} class="flip-rtl" />
			</a>
		</div>
	</header>

	{#key data.date}
		<DayView
			{day}
			entryTypes={data.entryTypes}
			medications={data.medications}
			bind:triggers
			typeRanking={data.typeRanking}
			triggerRanking={data.triggerRanking}
			home={data.profile.locationLat != null && data.profile.locationLon != null
			? { lat: data.profile.locationLat, lon: data.profile.locationLon, name: data.profile.locationName ?? '' }
			: null}
			isToday={data.date === data.today}
		/>
	{/key}
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

	.header {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.back {
		align-self: flex-start;
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: 44px;
		color: var(--text-muted);
		font-weight: 700;
		text-decoration: none;
	}

	.period {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.title {
		flex: 1;
		margin: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		font-size: var(--text-xl);
		font-weight: 800;
		text-align: center;
	}

	.weekday {
		font-size: var(--text-sm);
		font-weight: 700;
		color: var(--text-muted);
	}

	.icon-button {
		width: 44px;
		height: 44px;
		flex-shrink: 0;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: var(--bg-card);
		box-shadow: var(--shadow-sm);
		color: var(--text);
	}
</style>

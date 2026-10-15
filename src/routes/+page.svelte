<script lang="ts">
	import DayView from '$lib/diary/DayView.svelte';
	import { DiaryDay } from '$lib/diary/day.svelte.js';
	import { dateLocale, t } from '$lib/i18n';
	import type { Entry, EntryType, Medication, Trigger, UserProfile } from '$lib/types';

	interface Props {
		data: {
			today: string;
			entryTypes: EntryType[];
			medications: Medication[];
			triggers: Trigger[];
			profile: UserProfile;
			recent: Entry[];
			entry: Entry | null;
			typeRanking: string[];
			triggerRanking: string[];
		};
	}

	let { data }: Props = $props();

	// svelte-ignore state_referenced_locally
	const day = new DiaryDay(data.today, data.entry);
	// svelte-ignore state_referenced_locally
	let triggers = $state(data.triggers);

	const greeting = $derived.by(() => {
		const hour = new Date().getHours();
		const name = data.profile.displayName?.trim();
		const part = hour < 5 ? 'night' : hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening';
		return name ? t(`today_greeting_${part}_name`, { name }) : t(`today_greeting_${part}`);
	});

	const dateLine = new Date().toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' });
</script>

<div class="page">
	<header class="header">
		<p class="date">{dateLine}</p>
		<h1 class="greeting">{greeting}</h1>
	</header>

	<DayView
		{day}
		entryTypes={data.entryTypes}
		medications={data.medications}
		bind:triggers
		typeRanking={data.typeRanking}
		triggerRanking={data.triggerRanking}
		weatherEnabled={data.profile.weatherEnabled}
		home={data.profile.locationLat != null && data.profile.locationLon != null
			? { lat: data.profile.locationLat, lon: data.profile.locationLon, name: data.profile.locationName ?? '' }
			: null}
		isToday
	/>
</div>

<style>
	.page {
		max-width: 640px;
		margin: 0 auto;
		padding: var(--space-6) var(--space-4) var(--space-8);
		display: flex;
		flex-direction: column;
		gap: var(--space-6);
	}

	.header {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.date {
		margin: 0;
		color: var(--text-muted);
		font-weight: 700;
		font-size: var(--text-sm);
	}

	.greeting {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 800;
		line-height: 1.2;
	}
</style>

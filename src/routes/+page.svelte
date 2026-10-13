<script lang="ts">
	import Card from '$lib/ui/Card.svelte';
	import FaceRating from '$lib/ui/FaceRating.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import LogSheet from '$lib/diary/LogSheet.svelte';
	import LogList from '$lib/diary/LogList.svelte';
	import TriggerChips from '$lib/diary/TriggerChips.svelte';
	import WeatherCard from '$lib/diary/WeatherCard.svelte';
	import WeekStrip from '$lib/diary/WeekStrip.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Sprout from '@lucide/svelte/icons/sprout';
	import { DiaryDay } from '$lib/diary/day.svelte.js';
	import { dateLocale, localizeBuiltInText, t } from '$lib/i18n';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { hapticSuccess, hapticTap } from '$lib/haptics.js';
	import type { Entry, EntryType, EntryTypeLog, Trigger, UserProfile } from '$lib/types';

	interface Props {
		data: {
			today: string;
			entryTypes: EntryType[];
			triggers: Trigger[];
			profile: UserProfile;
			recent: Entry[];
			typeRanking: string[];
			triggerRanking: string[];
		};
	}

	let { data }: Props = $props();

	// svelte-ignore state_referenced_locally
	const day = new DiaryDay(data.today, data.recent.find((e) => e.date === data.today) ?? null);
	// svelte-ignore state_referenced_locally
	let triggers = $state(data.triggers);

	let sheetOpen = $state(false);
	let sheetType = $state<EntryType | null>(null);
	let sheetLog = $state<EntryTypeLog | null>(null);

	// Read once: syncing from later saves could overwrite what is being typed.
	let note = $state(day.entry?.note ?? '');
	let noteTimer: ReturnType<typeof setTimeout> | undefined;
	let noteSaved = $state(false);

	const greeting = $derived.by(() => {
		const hour = new Date().getHours();
		const name = data.profile.displayName?.trim();
		const part = hour < 5 ? 'night' : hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening';
		return name ? t(`today_greeting_${part}_name`, { name }) : t(`today_greeting_${part}`);
	});

	const dateLine = new Date().toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' });

	// Most used entry types first; unused ones keep the configured order.
	const quickTypes = $derived(
		[...data.entryTypes].sort((a, b) => {
			const rank = (id: string) => {
				const i = data.typeRanking.indexOf(id);
				return i === -1 ? Infinity : i;
			};
			return rank(a.id) - rank(b.id) || a.sortOrder - b.sortOrder;
		})
	);

	const recentWithToday = $derived([
		...data.recent.filter((e) => e.date !== data.today),
		...(day.entry ? [day.entry] : [])
	]);

	function openNew(type: EntryType) {
		hapticTap();
		sheetType = type;
		sheetLog = null;
		sheetOpen = true;
	}

	function openLog(log: EntryTypeLog) {
		sheetType = data.entryTypes.find((type) => type.id === log.entryTypeId) ?? null;
		sheetLog = log;
		sheetOpen = true;
	}

	async function endLog(log: EntryTypeLog) {
		await day.endLog(log.id);
		hapticSuccess();
		toasts.show(t('log_ended', { name: localizeBuiltInText(log.entryType?.name ?? '') }), 'success');
	}

	async function rate(value: number) {
		await day.setWellbeing(value);
		const symptomFree = !(day.entry?.logs ?? []).some((l) => l.entryType?.category === 'symptom');
		if (value >= 4 && symptomFree) {
			hapticSuccess();
			toasts.show(t('today_good_day'), 'success');
		}
	}

	function onNoteInput(value: string) {
		note = value;
		noteSaved = false;
		clearTimeout(noteTimer);
		noteTimer = setTimeout(async () => {
			await day.saveNote(note);
			noteSaved = true;
		}, 700);
	}
</script>

<div class="page">
	<header class="header">
		<p class="date">{dateLine}</p>
		<h1 class="greeting">{greeting}</h1>
	</header>

	<Card>
		<h2 class="card-title">{t('today_how_are_you')}</h2>
		<FaceRating
			value={day.entry?.wellbeing ?? null}
			labels={[t('wellbeing_1'), t('wellbeing_2'), t('wellbeing_3'), t('wellbeing_4'), t('wellbeing_5')]}
			groupLabel={t('today_how_are_you')}
			onchange={rate}
		/>
	</Card>

	<section>
		<h2 class="section-title">{t('today_log_something')}</h2>
		<div class="tiles">
			{#each quickTypes as type (type.id)}
				<button type="button" class="tile" style="--type: {type.color ?? 'var(--accent)'}" onclick={() => openNew(type)}>
					<EntryTypeIcon entryType={type} size={40} badge />
					<span class="tile-name">{localizeBuiltInText(type.name)}</span>
				</button>
			{/each}
		</div>
	</section>

	<section>
		<h2 class="section-title">{t('today_logged')}</h2>
		{#if day.entry?.logs?.length}
			<LogList logs={day.entry.logs} onopen={openLog} onend={endLog} />
		{:else}
			<Card padding="sm">
				<EmptyState icon={Sprout} compact color="var(--positive)" title={t('today_nothing_logged')} text={t('today_nothing_logged_hint')} />
			</Card>
		{/if}
	</section>

	<section>
		<h2 class="section-title">{t('today_triggers')}</h2>
		<TriggerChips {day} bind:triggers ranking={data.triggerRanking} />
	</section>

	<section>
		<h2 class="section-title">{t('today_weather')}</h2>
		<WeatherCard date={data.today} hasLocation={data.profile.locationLat != null && data.profile.locationLon != null} />
	</section>

	<section>
		<h2 class="section-title">
			<label for="today-note">{t('today_note')}</label>
			{#if noteSaved}<span class="saved">{t('today_note_saved')}</span>{/if}
		</h2>
		<textarea
			id="today-note"
			class="note"
			rows="3"
			placeholder={t('today_note_placeholder')}
			value={note}
			oninput={(e) => onNoteInput(e.currentTarget.value)}
		></textarea>
	</section>

	<section>
		<h2 class="section-title">{t('today_this_week')}</h2>
		<Card padding="sm">
			<WeekStrip entries={recentWithToday} today={data.today} label={t('today_this_week')} />
		</Card>
	</section>
</div>

<LogSheet bind:open={sheetOpen} {day} entryType={sheetType} log={sheetLog} />

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

	.card-title {
		margin: 0 0 var(--space-4);
		font-size: var(--text-lg);
		font-weight: 800;
	}

	.section-title {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		margin: 0 0 var(--space-3);
		font-size: var(--text-md);
		font-weight: 800;
	}

	.saved {
		font-size: var(--text-xs);
		font-weight: 700;
		color: var(--positive);
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(78px, 1fr));
		gap: var(--space-2);
	}

	.tile {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 6px;
		padding: var(--space-3) var(--space-1) var(--space-2);
		border-radius: var(--radius-md);
		border: 1px solid color-mix(in srgb, var(--type) 22%, transparent);
		background: color-mix(in srgb, var(--type) 10%, var(--bg-card));
		color: var(--text);
		cursor: pointer;
		transition: transform 0.3s var(--ease-spring);
	}

	.tile:active {
		transform: scale(0.93);
	}

	.tile-name {
		font-size: var(--text-xs);
		font-weight: 700;
		text-align: center;
		line-height: 1.2;
		/* Long single words (Stomachache, Medikationsänderung) break with a hyphen. */
		hyphens: auto;
		overflow-wrap: break-word;
	}

	.note {
		width: 100%;
		padding: var(--space-3) var(--space-4);
		border: 1.5px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-md);
		resize: vertical;
	}
</style>

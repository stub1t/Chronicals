<script lang="ts">
	/**
	 * Everything about one day: how you felt, quick logging, the day's entries,
	 * triggers, weather and note. Used by Today and by the calendar's day page.
	 */
	import Card from '$lib/ui/Card.svelte';
	import FaceRating from '$lib/ui/FaceRating.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import Sprout from '@lucide/svelte/icons/sprout';
	import LogSheet from './LogSheet.svelte';
	import LogList from './LogList.svelte';
	import TriggerChips from './TriggerChips.svelte';
	import WeatherCard from './WeatherCard.svelte';
	import { DiaryDay } from './day.svelte.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { hapticSuccess, hapticTap } from '$lib/haptics.js';
	import type { EntryType, EntryTypeLog, Trigger } from '$lib/types';

	interface Props {
		day: DiaryDay;
		entryTypes: EntryType[];
		triggers: Trigger[];
		/** Entry type and trigger ids, most used first. */
		typeRanking: string[];
		triggerRanking: string[];
		hasLocation: boolean;
		isToday: boolean;
	}

	let { day, entryTypes, triggers = $bindable(), typeRanking, triggerRanking, hasLocation, isToday }: Props = $props();

	let sheetOpen = $state(false);
	let sheetType = $state<EntryType | null>(null);
	let sheetLog = $state<EntryTypeLog | null>(null);

	// Read once: syncing from later saves could overwrite what is being typed.
	// svelte-ignore state_referenced_locally
	let note = $state(day.entry?.note ?? '');
	let noteTimer: ReturnType<typeof setTimeout> | undefined;
	let noteSaved = $state(false);

	// Most used entry types first; unused ones keep the configured order. Hidden types get no tile.
	const quickTypes = $derived(
		entryTypes.filter((type) => !type.hidden).sort((a, b) => {
			const rank = (id: string) => {
				const i = typeRanking.indexOf(id);
				return i === -1 ? Infinity : i;
			};
			return rank(a.id) - rank(b.id) || a.sortOrder - b.sortOrder;
		})
	);

	function openNew(type: EntryType) {
		hapticTap();
		sheetType = type;
		sheetLog = null;
		sheetOpen = true;
	}

	function openLog(log: EntryTypeLog) {
		sheetType = entryTypes.find((type) => type.id === log.entryTypeId) ?? null;
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

<div class="day-view">
	<Card>
		<h2 class="card-title">{isToday ? t('today_how_are_you') : t('day_how_were_you')}</h2>
		<FaceRating
			value={day.entry?.wellbeing ?? null}
			labels={[t('wellbeing_1'), t('wellbeing_2'), t('wellbeing_3'), t('wellbeing_4'), t('wellbeing_5')]}
			groupLabel={isToday ? t('today_how_are_you') : t('day_how_were_you')}
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
		<h2 class="section-title">{isToday ? t('today_logged') : t('day_entries')}</h2>
		{#if day.entry?.logs?.length}
			<LogList logs={day.entry.logs} onopen={openLog} onend={endLog} />
		{:else}
			<Card padding="sm">
				<EmptyState icon={Sprout} compact color="var(--positive)" title={t('today_nothing_logged')} text={t('today_nothing_logged_hint')} />
			</Card>
		{/if}
	</section>

	<section>
		<h2 class="section-title">{isToday ? t('today_triggers') : t('day_triggers')}</h2>
		<TriggerChips {day} bind:triggers ranking={triggerRanking} />
	</section>

	<section>
		<h2 class="section-title">{t('today_weather')}</h2>
		<WeatherCard date={day.date} {hasLocation} />
	</section>

	<section>
		<h2 class="section-title">
			<label for="day-note-{day.date}">{t('today_note')}</label>
			{#if noteSaved}<span class="saved">{t('today_note_saved')}</span>{/if}
		</h2>
		<textarea
			id="day-note-{day.date}"
			class="note"
			rows="3"
			placeholder={t('today_note_placeholder')}
			value={note}
			oninput={(e) => onNoteInput(e.currentTarget.value)}
		></textarea>
	</section>
</div>

<LogSheet bind:open={sheetOpen} {day} entryType={sheetType} log={sheetLog} />

<style>
	.day-view {
		display: flex;
		flex-direction: column;
		gap: var(--space-6);
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

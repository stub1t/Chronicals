<script lang="ts">
	import { untrack } from 'svelte';
	import { page } from '$app/state';
	/**
	 * Everything about one day: quick logging, the day's entries, triggers,
	 * weather and note. Used by Today and by the calendar's day page. Meals
	 * have their own tab, so they get no tile here.
	 */
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import Sprout from '@lucide/svelte/icons/sprout';
	import LogSheet from './LogSheet.svelte';
	import EntryTypeSheet from '$lib/me/EntryTypeSheet.svelte';
	import Plus from '@lucide/svelte/icons/plus';
	import LogList from './LogList.svelte';
	import TriggerChips from './TriggerChips.svelte';
	import WeatherCard from './WeatherCard.svelte';
	import { DiaryDay } from './day.svelte.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { hapticSuccess, hapticTap } from '$lib/haptics.js';
	import type { EntryType, EntryTypeLog, Medication, Trigger } from '$lib/types';

	interface Props {
		day: DiaryDay;
		entryTypes: EntryType[];
		triggers: Trigger[];
		/** Entry type and trigger ids, most used first. */
		typeRanking: string[];
		triggerRanking: string[];
		/** The home place from the profile, for the weather. */
		home: { lat: number; lon: number; name: string } | null;
		/** Weather and pollen turned on under Me. */
		weatherEnabled: boolean;
		isToday: boolean;
		/** Offered under "Taken anything for it?" in the log sheet. */
		medications?: Medication[];
	}

	let { day, entryTypes, triggers = $bindable(), typeRanking, triggerRanking, home, weatherEnabled, isToday, medications = [] }: Props = $props();

	// Own copy: new types ("+ New") and own options change it while the page stays.
	// svelte-ignore state_referenced_locally
	let types = $state<EntryType[]>([...entryTypes]);
	let newTypeOpen = $state(false);

	function replaceType(type: EntryType) {
		types = types.some((item) => item.id === type.id) ? types.map((item) => (item.id === type.id ? type : item)) : [...types, type];
		if (sheetType?.id === type.id) sheetType = type;
	}

	let sheetOpen = $state(false);
	let sheetType = $state<EntryType | null>(null);
	let sheetLog = $state<EntryTypeLog | null>(null);

	// Read once: syncing from later saves could overwrite what is being typed.
	// svelte-ignore state_referenced_locally
	let note = $state(day.entry?.note ?? '');
	let noteTimer: ReturnType<typeof setTimeout> | undefined;
	let noteSaved = $state(false);

	// Most used entry types first; unused ones keep the configured order. Hidden types and meals get no tile.
	const quickTypes = $derived(
		types.filter((type) => !type.hidden && type.category !== 'meal').sort((a, b) => {
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

	// A tapped "still going?" notification opens that log (?log=<id>), also when
	// the page is already open and only the address changes.
	$effect(() => {
		const id = page.url.searchParams.get('log');
		if (!id) return;
		untrack(() => {
			const log = day.entry?.logs?.find((item) => item.id === id);
			if (log) openLog(log);
		});
	});

	function openLog(log: EntryTypeLog) {
		sheetType = types.find((type) => type.id === log.entryTypeId) ?? null;
		sheetLog = log;
		sheetOpen = true;
	}

	async function endLog(log: EntryTypeLog) {
		await day.endLog(log.id);
		hapticSuccess();
		toasts.show(t('log_ended', { name: localizeBuiltInText(log.entryType?.name ?? '') }), 'success');
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
	<section>
		<h2 class="section-title">{t('today_log_something')}</h2>
		<p class="section-hint">{t('today_log_hint')}</p>
		<div class="tiles">
			{#each quickTypes as type (type.id)}
				<button type="button" class="tile" style="--type: {type.color ?? 'var(--accent)'}" onclick={() => openNew(type)}>
					<EntryTypeIcon entryType={type} size={40} badge />
					<span class="tile-name">{localizeBuiltInText(type.name)}</span>
				</button>
			{/each}
			<button type="button" class="tile new" style="--type: var(--text-muted)" onclick={() => (newTypeOpen = true)}>
				<span class="new-icon" aria-hidden="true"><Plus size={22} /></span>
				<span class="tile-name">{t('add_new')}</span>
			</button>
		</div>
	</section>

	<section>
		<h2 class="section-title">{isToday ? t('today_logged') : t('day_entries')}</h2>
		{#if day.entry?.logs?.length}
			<LogList logs={day.entry.logs} onopen={openLog} onend={endLog} />
		{:else}
			<Card padding="sm">
				<EmptyState icon={Sprout} compact color="var(--positive)" title={t('today_nothing_logged')} />
			</Card>
		{/if}
	</section>

	<section>
		<h2 class="section-title">{isToday ? t('today_triggers') : t('day_triggers')}</h2>
		<p class="section-hint">{isToday ? t('today_triggers_hint') : t('day_triggers_hint')}</p>
		<TriggerChips {day} bind:triggers ranking={triggerRanking} />
	</section>

	<section>
		<h2 class="section-title">{t('today_weather')}</h2>
		<WeatherCard {day} {home} enabled={weatherEnabled} />
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

<LogSheet bind:open={sheetOpen} {day} entryType={sheetType} log={sheetLog} {medications} ontypechange={replaceType} />
<EntryTypeSheet
	bind:open={newTypeOpen}
	editing={null}
	colorIndex={types.length}
	onsaved={(type) => {
		replaceType(type);
		openNew(type);
	}}
/>

<style>
	.day-view {
		display: flex;
		flex-direction: column;
		gap: var(--space-6);
	}

	.section-title {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		margin: 0 0 var(--space-3);
		font-size: var(--text-md);
		font-weight: 800;
	}

	.section-hint {
		margin: calc(var(--space-2) * -1) 0 var(--space-3);
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.saved {
		font-size: var(--text-xs);
		font-weight: 700;
		color: var(--positive);
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(78px, 1fr));
		/* Every row as tall as the tallest tile, so all tiles have one size. */
		grid-auto-rows: 1fr;
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

	.tile.new {
		border-style: dashed;
		background: transparent;
		color: var(--text-muted);
	}

	.new-icon {
		width: 40px;
		height: 40px;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: var(--bg-subtle);
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

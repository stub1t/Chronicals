<script lang="ts">
	/**
	 * The meals of a day by kind: breakfast, lunch, dinner and snacks. The tab
	 * always opens on today; the arrows go back to fill in a day that was missed.
	 */
	import ChevronLeft from '@lucide/svelte/icons/chevron-left';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import Plus from '@lucide/svelte/icons/plus';
	import LogSheet from '$lib/diary/LogSheet.svelte';
	import { DiaryDay } from '$lib/diary/day.svelte.js';
	import { dateLocale, formatTime, localizeBuiltInText, t } from '$lib/i18n';
	import { hapticTap } from '$lib/haptics.js';
	import { toDateString } from '$lib/utils/date';
	import type { Entry, EntryType, EntryTypeLog } from '$lib/types';

	let { data }: { data: { today: string; mealType: EntryType | null; entry: Entry | null } } = $props();

	// svelte-ignore state_referenced_locally
	let day = $state(new DiaryDay(data.today, data.entry));
	// Own copy: foods added with "+ New" change it.
	// svelte-ignore state_referenced_locally
	let mealType = $state(data.mealType);

	const SECTIONS = [
		{ meal: 'breakfast', label: 'meals_breakfast' },
		{ meal: 'lunch', label: 'meals_lunch' },
		{ meal: 'dinner', label: 'meals_dinner' },
		{ meal: 'snack', label: 'meals_snacks' }
	] as const;

	// Snacks also hold drinks (a kind the form no longer offers) and meals without a kind.
	function sectionOf(log: EntryTypeLog): string {
		const kind = log.metricValues?.meal;
		return kind === 'breakfast' || kind === 'lunch' || kind === 'dinner' ? kind : 'snack';
	}

	const meals = $derived(
		(day.entry?.logs ?? []).filter((log) => log.entryTypeId === mealType?.id).sort((a, b) => a.startTime.localeCompare(b.startTime))
	);

	const isToday = $derived(day.date === data.today);
	const dateLine = $derived.by(() => {
		const long = new Date(`${day.date}T12:00:00`).toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' });
		return isToday ? `${t('nav_today')}, ${long}` : long;
	});

	function go(days: number) {
		const date = new Date(`${day.date}T12:00:00`);
		date.setDate(date.getDate() + days);
		day = new DiaryDay(toDateString(date));
		day.load();
	}

	const foodsOf = (log: EntryTypeLog) =>
		[...(Array.isArray(log.metricValues?.foods) ? (log.metricValues.foods as string[]) : []), (log.metricValues?.what as string) ?? '']
			.filter(Boolean)
			.map((food) => localizeBuiltInText(food))
			.join(', ');

	let sheetOpen = $state(false);
	let sheetLog = $state<EntryTypeLog | null>(null);
	let preset = $state<Record<string, unknown>>({});

	function add(meal: string) {
		hapticTap();
		sheetLog = null;
		preset = { meal };
		sheetOpen = true;
	}

	function open(log: EntryTypeLog) {
		sheetLog = log;
		preset = {};
		sheetOpen = true;
	}
</script>

<div class="page" style="--type: {mealType?.color ?? 'var(--accent)'}">
	<header class="header">
		<button type="button" class="icon-button" aria-label={t('prev_day')} onclick={() => go(-1)}><ChevronLeft size={22} class="flip-rtl" /></button>
		<div class="heading">
			<h1 class="title">{t('meals_title')}</h1>
			<p class="date">{dateLine}</p>
		</div>
		<button type="button" class="icon-button" aria-label={t('next_day')} disabled={isToday} onclick={() => go(1)}><ChevronRight size={22} class="flip-rtl" /></button>
	</header>

	{#if mealType}
		{#each SECTIONS as section (section.meal)}
			{@const logs = meals.filter((log) => sectionOf(log) === section.meal)}
			<section class="section">
				<h2 class="section-title">{t(section.label)}</h2>
				{#if logs.length}
					<ul class="meals">
						{#each logs as log (log.id)}
							<li>
								<button type="button" class="meal" onclick={() => open(log)}>
									<span class="time">{formatTime(log.startTime)}</span>
									<span class="foods">{foodsOf(log) || localizeBuiltInText(mealType.name)}</span>
									<ChevronRight size={18} class="flip-rtl chevron" />
								</button>
							</li>
						{/each}
					</ul>
				{/if}
				<button type="button" class="add" onclick={() => add(section.meal)}><Plus size={16} />{t('add_new')}</button>
			</section>
		{/each}
	{/if}
</div>

<LogSheet bind:open={sheetOpen} {day} entryType={mealType} log={sheetLog} {preset} ontypechange={(type) => (mealType = type)} />

<style>
	.page {
		max-width: 640px;
		margin: 0 auto;
		padding: var(--space-6) var(--space-4) var(--space-8);
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.header {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.heading {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		text-align: center;
	}

	.title {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 800;
		line-height: 1.2;
	}

	.date {
		margin: 0;
		color: var(--text-muted);
		font-weight: 700;
		font-size: var(--text-sm);
	}

	.icon-button {
		width: 44px;
		height: 44px;
		display: grid;
		place-items: center;
		border: none;
		border-radius: 50%;
		background: var(--bg-subtle);
		color: var(--text);
		cursor: pointer;
	}

	.icon-button:disabled {
		opacity: 0.35;
		cursor: default;
	}

	.section {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: var(--space-2);
	}

	.section-title {
		margin: 0 0 var(--space-1);
		font-size: var(--text-md);
		font-weight: 800;
	}

	.meals {
		list-style: none;
		margin: 0;
		padding: 0;
		width: 100%;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.meal {
		width: 100%;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3);
		border-radius: var(--radius-md);
		border: 1px solid color-mix(in srgb, var(--type) 20%, transparent);
		background: color-mix(in srgb, var(--type) 9%, var(--bg-card));
		color: var(--text);
		text-align: start;
		cursor: pointer;
	}

	.time {
		font-size: var(--text-sm);
		font-weight: 700;
		color: var(--text-muted);
		font-variant-numeric: tabular-nums;
	}

	.foods {
		flex: 1;
		min-width: 0;
		font-weight: 700;
	}

	.foods::first-letter {
		text-transform: uppercase;
	}

	.meal :global(.chevron) {
		color: var(--text-subtle);
	}

	.add {
		min-height: 40px;
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 0 14px;
		border-radius: var(--radius-pill);
		border: 1.5px dashed var(--border);
		background: transparent;
		color: var(--text-muted);
		font-size: var(--text-sm);
		font-weight: 700;
		cursor: pointer;
	}
</style>

<script lang="ts">
	import { goto } from '$app/navigation';
	import ChartNoAxesColumn from '@lucide/svelte/icons/chart-no-axes-column';
	import FileText from '@lucide/svelte/icons/file-text';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import Chip from '$lib/ui/Chip.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import SummaryCard from '$lib/insights/SummaryCard.svelte';
	import LinksCard from '$lib/insights/LinksCard.svelte';
	import WhenCard from '$lib/insights/WhenCard.svelte';
	import MedsCard from '$lib/insights/MedsCard.svelte';
	import AllTypes from '$lib/insights/AllTypes.svelte';
	import {
		analysableTypes,
		byCyclePhase,
		byTimeOfDay,
		byWeekday,
		dailyLevels,
		indexDiary,
		links,
		medicationEffects,
		medicationEvents,
		months,
		overview,
		timePattern,
		trackedDays,
		weekdayPattern,
		weeks
	} from '$lib/insights/engine.js';
	import { daysBetween } from '$lib/insights/dates.js';
	import { dateLocale, localizeBuiltInText, t } from '$lib/i18n';
	import type { Entry, EntryType, EnvironmentalData, Medication, MedicationChange, Trigger } from '$lib/types';
	import type { Period } from './+page.js';

	interface Props {
		data: {
			period: Period;
			from: string;
			to: string;
			type: string | null;
			entries: Entry[];
			weather: EnvironmentalData[];
			entryTypes: EntryType[];
			triggers: Trigger[];
			medications: Medication[];
			changes: MedicationChange[];
		};
	}

	let { data }: Props = $props();

	const diary = $derived(indexDiary(data.entries));
	const tracked = $derived(trackedDays(diary, data.from, data.to).length);
	const length = $derived(daysBetween(data.from, data.to) + 1);
	const types = $derived(analysableTypes(diary, data.entryTypes, data.from, data.to));
	// "All" first when there is more than one type.
	const showAll = $derived(types.length > 1 && (data.type === 'all' || !types.some((item) => item.type.id === data.type)));
	const selected = $derived(showAll ? null : (types.find((item) => item.type.id === data.type)?.type ?? types[0]?.type ?? null));
	const color = $derived(selected?.color ?? 'var(--accent)');
	const typeName = $derived(selected ? localizeBuiltInText(selected.name) : '');
	const names = $derived(new Map(data.triggers.map((tr) => [tr.id, tr.name])));
	// Up to half a year as single days; longer as months.
	const daily = $derived(length <= 190);
	const date = (d: string) => new Date(`${d}T12:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' });

	const result = $derived.by(() => {
		if (!selected) return null;
		const id = selected.id;
		const parts = byTimeOfDay(diary, id, data.from, data.to);
		return {
			overview: overview(diary, id, data.from, data.to),
			days: daily ? dailyLevels(diary, id, data.from, data.to) : null,
			months: months(diary, id, data.from, data.to),
			links: links(diary, id, data.from, data.to, data.weather),
			weekday: weekdayPattern(byWeekday(diary, id, data.from, data.to)),
			time: timePattern(parts),
			cycle: byCyclePhase(diary, id, 'builtin-period', data.from, data.to),
			meds: medicationEffects(diary, id, medicationEvents(data.medications, data.changes), data.from, data.to, data.to)
		};
	});

	const all = $derived.by(() => {
		if (!showAll) return null;
		const strip = (id: string) => (daily ? weeks(diary, id, data.from, data.to) : months(diary, id, data.from, data.to));
		const first = types[0]?.type.id ?? '';
		return {
			rows: types.map(({ type }) => ({ type, overview: overview(diary, type.id, data.from, data.to), strip: strip(type.id) })),
			wellbeing: strip(first).map((b) => b.avgWellbeing)
		};
	});

	function go(params: { period?: Period; type?: string }) {
		const query = new URLSearchParams({ period: params.period ?? data.period });
		const type = params.type ?? (showAll ? 'all' : selected?.id);
		if (type) query.set('type', type);
		goto(`/insights?${query}`, { replaceState: true, noScroll: true, keepFocus: true });
	}
</script>

<div class="page">
	<header class="header">
		<h1 class="title">{t('nav_insights')}</h1>
		<Segmented
			label={t('insights_period')}
			value={data.period}
			onchange={(period) => go({ period })}
			options={[
				{ value: '3m', label: t('insights_period_3m') },
				{ value: '6m', label: t('insights_period_6m') },
				{ value: '12m', label: t('insights_period_12m') },
				{ value: 'all', label: t('insights_period_all') }
			]}
		/>
		<p class="range">{t('insights_range', { from: date(data.from), to: date(data.to), tracked, days: length })}</p>
	</header>

	{#if types.length === 0}
		<EmptyState icon={ChartNoAxesColumn} title={t('insights_empty_title')} text={t('insights_empty_text')} />
	{:else}
		{#if types.length > 1}
			<div class="types" role="group" aria-label={t('insights_symptom')}>
				<Chip selected={showAll} onclick={() => go({ type: 'all' })}>{t('insights_all')}</Chip>
				{#each types as item (item.type.id)}
					<Chip selected={item.type.id === selected?.id} color={item.type.color} onclick={() => go({ type: item.type.id })}>
						<EntryTypeIcon entryType={item.type} size={18} />
						{localizeBuiltInText(item.type.name)}
					</Chip>
				{/each}
			</div>
		{/if}

		{#if tracked < 28}
			<p class="hint">{t('insights_few_days', { count: tracked })}</p>
		{/if}

		{#if all}
			<AllTypes rows={all.rows} trackedDays={tracked} wellbeing={all.wellbeing} onpick={(id) => go({ type: id })} />
		{:else if selected && result}
			<section class="section">
				<h2 class="section-title">{t('insights_doing')}</h2>
				<SummaryCard {typeName} {color} overview={result.overview} days={result.days} months={result.months} />
			</section>

			<section class="section">
				<h2 class="section-title">{t('insights_links')}</h2>
				<LinksCard {typeName} {color} links={result.links} {names} />
			</section>

			<section class="section">
				<h2 class="section-title">{t('insights_when')}</h2>
				<WhenCard {typeName} {color} weekday={result.weekday} time={result.time} cycle={result.cycle} />
			</section>

			{#if result.meds.length}
				<section class="section">
					<h2 class="section-title">{t('insights_meds')}</h2>
					<MedsCard {typeName} {color} effects={result.meds} />
				</section>
			{/if}
		{/if}
	{/if}

	<a class="report" href="/insights/report">
		<span class="bubble" aria-hidden="true"><FileText size={22} /></span>
		<span class="text">
			<span class="name">{t('report_title')}</span>
			<span class="detail">{t('insights_report_text')}</span>
		</span>
		<ChevronRight size={20} class="flip-rtl" />
	</a>
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

	.title {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 800;
	}

	.range {
		margin: 0;
		font-size: var(--text-sm);
		font-weight: 700;
		color: var(--text-muted);
	}

	.types {
		display: flex;
		gap: var(--space-2);
		overflow-x: auto;
		padding-bottom: 4px;
		scrollbar-width: none;
	}

	.types :global(.chip) {
		flex-shrink: 0;
		white-space: nowrap;
	}

	.hint {
		margin: 0;
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: var(--accent-soft);
		font-size: var(--text-sm);
		font-weight: 600;
		line-height: 1.45;
	}

	.section {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.section-title {
		margin: 0 var(--space-1);
		font-size: var(--text-lg);
		font-weight: 800;
	}

	.report {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-4);
		border-radius: var(--radius-lg);
		background: var(--bg-card);
		box-shadow: var(--shadow-sm);
		color: var(--text);
		text-decoration: none;
	}

	.bubble {
		width: 44px;
		height: 44px;
		flex-shrink: 0;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: var(--accent-soft);
		color: var(--accent);
	}

	.text {
		flex: 1;
		display: flex;
		flex-direction: column;
	}

	.name {
		font-weight: 800;
	}

	.detail {
		font-size: var(--text-sm);
		color: var(--text-muted);
	}
</style>

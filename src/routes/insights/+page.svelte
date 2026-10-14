<script lang="ts">
	import { goto } from '$app/navigation';
	import ChartNoAxesColumn from '@lucide/svelte/icons/chart-no-axes-column';
	import FileText from '@lucide/svelte/icons/file-text';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import Chip from '$lib/ui/Chip.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import DoingCard from '$lib/insights/DoingCard.svelte';
	import LinksCard from '$lib/insights/LinksCard.svelte';
	import WhenCard from '$lib/insights/WhenCard.svelte';
	import MedsCard from '$lib/insights/MedsCard.svelte';
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
		overview,
		trackedDays,
		weeks
	} from '$lib/insights/engine.js';
	import { addDays } from '$lib/insights/dates.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
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
	const types = $derived(analysableTypes(diary, data.entryTypes, data.from, data.to));
	const selected = $derived(types.find((item) => item.type.id === data.type)?.type ?? types[0]?.type ?? null);
	const color = $derived(selected?.color ?? 'var(--accent)');
	const typeName = $derived(selected ? localizeBuiltInText(selected.name) : '');
	const names = $derived(new Map(data.triggers.map((tr) => [tr.id, tr.name])));

	// The heatmap shows at most the last 53 weeks.
	const heatFrom = $derived([data.from, addDays(data.to, -370)].sort().at(-1)!);

	const result = $derived.by(() => {
		if (!selected) return null;
		const id = selected.id;
		return {
			overview: overview(diary, id, data.from, data.to),
			weeks: weeks(diary, id, data.from, data.to),
			days: dailyLevels(diary, id, heatFrom, data.to),
			links: links(diary, id, data.from, data.to, data.weather),
			weekdays: byWeekday(diary, id, data.from, data.to),
			dayParts: byTimeOfDay(diary, id, data.from, data.to),
			cycle: byCyclePhase(diary, id, 'builtin-period', data.from, data.to),
			meds: medicationEffects(diary, id, medicationEvents(data.medications, data.changes), data.from, data.to, data.to)
		};
	});

	function go(params: { period?: Period; type?: string }) {
		const query = new URLSearchParams({ period: params.period ?? data.period });
		const type = params.type ?? selected?.id;
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
	</header>

	{#if !selected || !result}
		<EmptyState icon={ChartNoAxesColumn} title={t('insights_empty_title')} text={t('insights_empty_text')} />
	{:else}
		{#if types.length > 1}
			<div class="types" role="group" aria-label={t('insights_symptom')}>
				{#each types as item (item.type.id)}
					<Chip selected={item.type.id === selected.id} color={item.type.color} onclick={() => go({ type: item.type.id })}>
						<EntryTypeIcon entryType={item.type} size={18} />
						{localizeBuiltInText(item.type.name)}
					</Chip>
				{/each}
			</div>
		{/if}

		{#if tracked < 28}
			<p class="hint">{t('insights_few_days', { count: tracked })}</p>
		{/if}

		<section class="section">
			<h2 class="section-title">{t('insights_doing')}</h2>
			<DoingCard {typeName} {color} overview={result.overview} weeks={result.weeks} days={result.days} />
		</section>

		<section class="section">
			<h2 class="section-title">{t('insights_links')}</h2>
			<LinksCard {typeName} {color} links={result.links} {names} />
		</section>

		<section class="section">
			<h2 class="section-title">{t('insights_when')}</h2>
			<WhenCard {color} weekdays={result.weekdays} dayParts={result.dayParts} cycle={result.cycle} />
		</section>

		{#if result.meds.length}
			<section class="section">
				<h2 class="section-title">{t('insights_meds')}</h2>
				<MedsCard {typeName} {color} effects={result.meds} />
			</section>
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

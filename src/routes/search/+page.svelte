<script lang="ts">
	import ArrowLeft from '@lucide/svelte/icons/arrow-left';
	import SearchIcon from '@lucide/svelte/icons/search';
	import SearchX from '@lucide/svelte/icons/search-x';
	import { api } from '$lib/api.js';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import Chip from '$lib/ui/Chip.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import { matchEntry } from '$lib/search.js';
	import { dateLocale, localizeBuiltInText, t } from '$lib/i18n';
	import type { Entry, EntryType } from '$lib/types/index.js';
	import { toDateString } from '$lib/utils/date';

	let { data }: { data: { entryTypes: EntryType[] } } = $props();

	type Period = '3m' | '12m' | 'all';

	let query = $state('');
	let entryTypeId = $state('');
	let period = $state<Period>('12m');
	let entries = $state<Entry[]>([]);
	let loading = $state(true);

	function periodStart(p: Period): string {
		if (p === 'all') return '1900-01-01';
		const d = new Date();
		d.setMonth(d.getMonth() - (p === '3m' ? 3 : 12));
		return toDateString(d);
	}

	// Load all entries of the period once; filtering happens on the device so
	// translated names can be matched.
	$effect(() => {
		const from = periodStart(period);
		loading = true;
		api(`/api/entries?from=${from}&to=${toDateString(new Date())}`)
			.then((res) => (res.ok ? res.json() : []))
			.then((list: Entry[]) => (entries = list))
			.finally(() => (loading = false));
	});

	let active = $derived(query.trim() !== '' || entryTypeId !== '');
	let results = $derived(
		active
			? entries
					.filter((e) => matchEntry(e, query, entryTypeId || null, localizeBuiltInText))
					.sort((a, b) => b.date.localeCompare(a.date))
			: []
	);

	function formatDay(date: string): string {
		return new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), {
			weekday: 'short',
			day: 'numeric',
			month: 'short',
			year: 'numeric'
		});
	}

	function snippet(text: string | null): string {
		if (!text) return '';
		return text.length > 120 ? `${text.slice(0, 117)}...` : text;
	}
</script>

<div class="page">
	<header class="header">
		<a href="/calendar" class="icon-button" aria-label={t('calendar_back')}><ArrowLeft size={22} class="flip-rtl" /></a>
		<h1 class="title">{t('search_title')}</h1>
	</header>

	<label class="field">
		<SearchIcon size={20} />
		<!-- svelte-ignore a11y_autofocus -->
		<input type="search" bind:value={query} placeholder={t('search_placeholder')} aria-label={t('search_title')} autofocus />
	</label>

	<div class="types" role="group" aria-label={t('search_all_types')}>
		<Chip selected={entryTypeId === ''} onclick={() => (entryTypeId = '')}>{t('search_all_types')}</Chip>
		{#each data.entryTypes as type (type.id)}
			<Chip selected={entryTypeId === type.id} color={type.color} onclick={() => (entryTypeId = entryTypeId === type.id ? '' : type.id)}>
				<EntryTypeIcon entryType={type} size={18} />
				{localizeBuiltInText(type.name)}
			</Chip>
		{/each}
	</div>

	<Segmented
		label={t('search_period')}
		bind:value={period}
		options={[
			{ value: '3m', label: t('search_period_3m') },
			{ value: '12m', label: t('search_period_12m') },
			{ value: 'all', label: t('search_period_all') }
		]}
	/>

	{#if !active}
		<EmptyState icon={SearchIcon} compact title={t('search_title')} text={t('search_hint')} />
	{:else if !loading}
		{#if results.length === 0}
			<EmptyState icon={SearchX} compact color="var(--warning)" title={t('search_no_results')} />
		{:else}
			<p class="count">{t('search_results', { count: results.length })}</p>
			<ul class="results">
				{#each results as entry (entry.id)}
					<li>
						<a class="result" href="/calendar/day/{entry.date}">
							<span class="date">{formatDay(entry.date)}</span>
							{#if entry.logs?.length}
								<span class="logs">
									{#each entry.logs as log (log.id)}
										<span class="log" style="--type: {log.entryType?.color ?? 'var(--accent)'}">
											<EntryTypeIcon entryType={log.entryType} size={18} />
											{localizeBuiltInText(log.entryType?.name)}
										</span>
									{/each}
								</span>
							{/if}
							{#if entry.triggers?.length}
								<span class="muted">{entry.triggers.map((tr) => localizeBuiltInText(tr.triggerName)).join(' · ')}</span>
							{/if}
							{#if entry.note}
								<span class="muted note">{snippet(entry.note)}</span>
							{/if}
						</a>
					</li>
				{/each}
			</ul>
		{/if}
	{/if}
</div>

<style>
	.page {
		max-width: 640px;
		margin: 0 auto;
		padding: var(--space-5) var(--space-4) var(--space-8);
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.header {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.title {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 800;
	}

	.icon-button {
		width: 44px;
		height: 44px;
		display: grid;
		place-items: center;
		border-radius: 50%;
		color: var(--text);
	}

	.icon-button:hover {
		background: var(--bg-subtle);
	}

	.field {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: 0 var(--space-4);
		min-height: 52px;
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		box-shadow: var(--shadow-sm);
		color: var(--text-muted);
		border: 2px solid transparent;
	}

	.field:focus-within {
		border-color: var(--accent);
	}

	.field input {
		flex: 1;
		min-width: 0;
		border: none;
		background: none;
		outline: none;
		color: var(--text);
		font-size: var(--text-md);
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

	.count {
		margin: 0;
		font-size: var(--text-sm);
		font-weight: 700;
		color: var(--text-muted);
	}

	.results {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.result {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: var(--bg-card);
		box-shadow: var(--shadow-sm);
		color: var(--text);
		text-decoration: none;
	}

	.date {
		font-weight: 800;
	}

	.logs {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.log {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 3px 10px 3px 4px;
		border-radius: var(--radius-pill);
		background: color-mix(in srgb, var(--type) 12%, var(--bg-card));
		font-size: var(--text-sm);
		font-weight: 700;
	}

	.muted {
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.note {
		white-space: pre-line;
	}
</style>

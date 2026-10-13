<script lang="ts">
	import { api } from '$lib/api.js';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import { matchEntry } from '$lib/search.js';
	import { dateLocale, localizeBuiltInText, t } from '$lib/stores/locale.js';
	import type { Entry, EntryType } from '$lib/types/index.js';
	import { toDateString } from '$lib/utils/date';

	let { data }: { data: { entryTypes: EntryType[] } } = $props();

	type Period = '3m' | '12m' | 'all';
	const PERIODS: Array<{ value: Period; label: () => string }> = [
		{ value: '3m', label: () => t('search_period_3m') },
		{ value: '12m', label: () => t('search_period_12m') },
		{ value: 'all', label: () => t('search_period_all') }
	];

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
	<header class="page-header">
		<a href="/calendar" class="back-link">← {t('search_back')}</a>
		<h1 class="page-title">{t('search_title')}</h1>
	</header>

	<div class="controls">
		<input
			class="input"
			type="search"
			bind:value={query}
			placeholder={t('search_placeholder')}
			aria-label={t('search_title')}
		/>
		<div class="filter-row">
			<select class="input select" bind:value={entryTypeId} aria-label={t('search_all_types')}>
				<option value="">{t('search_all_types')}</option>
				{#each data.entryTypes as type (type.id)}
					<option value={type.id}>{localizeBuiltInText(type.name)}</option>
				{/each}
			</select>
			<div class="periods" role="group">
				{#each PERIODS as option (option.value)}
					<button
						class="period"
						class:active={period === option.value}
						aria-pressed={period === option.value}
						onclick={() => (period = option.value)}>{option.label()}</button
					>
				{/each}
			</div>
		</div>
	</div>

	{#if active && !loading}
		<p class="count">
			{results.length === 0 ? t('search_no_results') : t('search_results', { count: results.length })}
		</p>
		<ul class="results">
			{#each results as entry (entry.id)}
				<li>
					<a class="result" href="/calendar/day/{entry.date}">
						<span class="result-date">{formatDay(entry.date)}</span>
						{#if entry.logs?.length}
							<span class="chips">
								{#each entry.logs as log (log.id)}
									<span class="chip" style="--accent: {log.entryType?.color ?? 'var(--text-muted)'}">
										<EntryTypeIcon entryType={log.entryType} size={14} />
										{localizeBuiltInText(log.entryType?.name)}
									</span>
								{/each}
							</span>
						{/if}
						{#if entry.triggers?.length}
							<span class="triggers">{entry.triggers.map((tr) => localizeBuiltInText(tr.triggerName)).join(', ')}</span>
						{/if}
						{#if entry.note}
							<span class="note">{snippet(entry.note)}</span>
						{/if}
					</a>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.page {
		max-width: 560px;
		margin: 0 auto;
		padding: 24px 16px 80px;
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	.page-header {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.back-link {
		font-size: 0.82rem;
		color: var(--text-muted);
		text-decoration: none;
		align-self: flex-start;
	}

	.page-title {
		font-size: 1.6rem;
		font-weight: 800;
		color: var(--text);
		margin: 0;
	}

	.controls {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.filter-row {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}

	.input {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 10px 14px;
		font-family: inherit;
		font-size: 0.9rem;
		color: var(--text);
		width: 100%;
		box-sizing: border-box;
	}

	.select {
		flex: 1;
		min-width: 160px;
		width: auto;
	}

	.periods {
		display: flex;
		gap: 4px;
	}

	.period {
		padding: 8px 12px;
		border: 1px solid var(--border);
		border-radius: 18px;
		background: var(--bg-card);
		font-family: inherit;
		font-size: 0.8rem;
		color: var(--text-muted);
		cursor: pointer;
	}

	.period.active {
		background: var(--text);
		border-color: var(--text);
		color: var(--bg);
		font-weight: 700;
	}

	.count {
		font-size: 0.82rem;
		color: var(--text-muted);
		margin: 0;
	}

	.results {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.result {
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: 12px 14px;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 12px;
		text-decoration: none;
		color: var(--text);
	}

	.result-date {
		font-size: 0.85rem;
		font-weight: 700;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.chip {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		font-size: 0.78rem;
		font-weight: 600;
		padding: 2px 8px;
		border-radius: 10px;
		border: 1px solid var(--accent);
		color: var(--text);
	}

	.triggers,
	.note {
		font-size: 0.8rem;
		color: var(--text-muted);
	}
</style>

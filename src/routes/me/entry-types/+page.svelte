<script lang="ts">
	import Plus from '@lucide/svelte/icons/plus';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import { api } from '$lib/api.js';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import SubPage from '$lib/me/SubPage.svelte';
	import EntryTypeSheet from '$lib/me/EntryTypeSheet.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Switch from '$lib/ui/Switch.svelte';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import type { EntryType } from '$lib/types/index.js';

	let { data }: { data: { entryTypes: EntryType[] } } = $props();

	// svelte-ignore state_referenced_locally
	let entryTypes = $state<EntryType[]>([...data.entryTypes]);

	let sheetOpen = $state(false);
	let editing = $state<EntryType | null>(null);

	function openNew() {
		editing = null;
		sheetOpen = true;
	}

	function openEdit(type: EntryType) {
		editing = type;
		sheetOpen = true;
	}

	function onsaved(saved: EntryType, created: boolean) {
		entryTypes = created ? [...entryTypes, saved] : entryTypes.map((type) => (type.id === saved.id ? saved : type));
	}

	const hasOwnOptions = (type: EntryType) => Object.values(type.extraOptions ?? {}).some((options) => options.length > 0);

	async function setShown(type: EntryType, shown: boolean) {
		const res = await api(`/api/entry-types/${type.id}`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ hidden: !shown })
		});
		if (res.ok) {
			const saved: EntryType = await res.json();
			entryTypes = entryTypes.map((item) => (item.id === saved.id ? saved : item));
		}
	}

	function categoryLabel(kind: string): string {
		if (kind === 'symptom') return t('entry_category_symptom');
		if (kind === 'event') return t('entry_category_event');
		if (kind === 'note') return t('entry_category_note');
		if (kind === 'meal') return t('entry_category_meal');
		return kind;
	}
</script>

<SubPage title={t('entry_types_title')}>
	{#snippet action()}
		<Button size="sm" onclick={openNew}><Plus size={18} />{t('add_new')}</Button>
	{/snippet}

	<p class="ui-hint">{t('entry_types_hint')}</p>

	<ul class="list">
		{#each entryTypes as type (type.id)}
			<li class="row" class:hidden={type.hidden && type.category !== 'meal'}>
				{#if type.isBuiltIn && !hasOwnOptions(type)}
					<span class="main">
						<EntryTypeIcon entryType={type} size={42} badge />
						<span class="text">
							<span class="name">{localizeBuiltInText(type.name)}</span>
							<span class="meta">{categoryLabel(type.category)} · {t('entry_types_builtin')}</span>
						</span>
					</span>
				{:else}
					<button type="button" class="main" onclick={() => openEdit(type)} aria-label={t('triggers_edit', { name: localizeBuiltInText(type.name) })}>
						<EntryTypeIcon entryType={type} size={42} badge />
						<span class="text">
							<span class="name">{localizeBuiltInText(type.name)}</span>
							<span class="meta">{categoryLabel(type.category)}{type.isBuiltIn ? ` · ${t('entry_types_builtin')}` : ''}</span>
						</span>
						<ChevronRight size={18} class="flip-rtl chevron" />
					</button>
				{/if}
				<!-- Meals have their own tab instead of a tile on Today. -->
				{#if type.category !== 'meal'}
					<Switch
						checked={!type.hidden}
						color={type.color}
						label={t('entry_types_show', { name: localizeBuiltInText(type.name) })}
						onchange={(on) => setShown(type, on)}
					/>
				{/if}
			</li>
		{/each}
	</ul>
</SubPage>

<EntryTypeSheet
	bind:open={sheetOpen}
	{editing}
	colorIndex={entryTypes.length}
	{onsaved}
	ondeleted={(id) => (entryTypes = entryTypes.filter((type) => type.id !== id))}
/>


<style>
	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		background: var(--bg-card);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		overflow: hidden;
	}

	.row {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding-inline-end: var(--space-4);
	}

	.row + .row {
		border-top: 1px solid var(--border);
	}

	.main {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: 68px;
		padding: var(--space-2) var(--space-4);
		border: none;
		background: none;
		color: var(--text);
		text-align: start;
		font-size: var(--text-md);
	}

	button.main {
		cursor: pointer;
	}

	button.main:active {
		background: var(--bg-subtle);
	}

	.row.hidden .main {
		opacity: 0.5;
	}

	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.name {
		font-weight: 700;
	}

	.meta {
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.main :global(.chevron) {
		color: var(--text-subtle);
	}










</style>

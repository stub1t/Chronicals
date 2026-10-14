<script lang="ts">
	import Plus from '@lucide/svelte/icons/plus';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import { api } from '$lib/api.js';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import SubPage from '$lib/me/SubPage.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import Sheet from '$lib/ui/Sheet.svelte';
	import Switch from '$lib/ui/Switch.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { ENTRY_ICONS } from '$lib/ui/entry-icons.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import type { EntryType } from '$lib/types/index.js';

	let { data }: { data: { entryTypes: EntryType[] } } = $props();

	// svelte-ignore state_referenced_locally
	let entryTypes = $state<EntryType[]>([...data.entryTypes]);

	const COLORS = ['#e0679a', '#d6455d', '#d9822b', '#e3a13b', '#2f9e6e', '#3aa6b9', '#5b8def', '#7462e8', '#a855f7', '#94a3b8'];
	const ICON_KEYS = Object.keys(ENTRY_ICONS);

	let sheetOpen = $state(false);
	let editing = $state<EntryType | null>(null);
	let name = $state('');
	let icon = $state('lucide:activity');
	let color = $state(COLORS[0]);
	let category = $state<'symptom' | 'event'>('symptom');
	let saving = $state(false);

	function openNew() {
		editing = null;
		name = '';
		icon = 'lucide:activity';
		color = COLORS[entryTypes.length % COLORS.length];
		category = 'symptom';
		sheetOpen = true;
	}

	function openEdit(type: EntryType) {
		editing = type;
		name = type.name;
		icon = type.icon ?? 'lucide:activity';
		color = type.color ?? COLORS[0];
		sheetOpen = true;
	}

	function schemaFor(kind: 'symptom' | 'event') {
		const notes = { key: 'notes', label: 'Notes', type: 'textarea', required: false, config: { placeholder: 'Additional notes...' } };
		const intensity = { key: 'intensity', label: 'Intensity', type: 'slider', required: true, config: { min: 1, max: 5, step: 1 } };
		return { fields: kind === 'symptom' ? [intensity, notes] : [notes] };
	}

	async function save() {
		if (!name.trim()) return;
		saving = true;
		const body = editing
			? { name: name.trim(), icon, color }
			: { name: name.trim(), icon, color, category, metricSchema: schemaFor(category) };
		const res = await api(editing ? `/api/entry-types/${editing.id}` : '/api/entry-types', {
			method: editing ? 'PUT' : 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(body)
		});
		saving = false;
		if (!res.ok) {
			const err = await res.json().catch(() => ({}));
			toasts.show(err.message ?? t('network_error'), 'error');
			return;
		}
		const saved: EntryType = await res.json();
		entryTypes = editing ? entryTypes.map((type) => (type.id === saved.id ? saved : type)) : [...entryTypes, saved];
		toasts.show(t('log_saved', { name: saved.name }), 'success');
		sheetOpen = false;
	}

	async function remove() {
		if (!editing || !confirm(t('entry_types_delete_confirm'))) return;
		const res = await api(`/api/entry-types/${editing.id}`, { method: 'DELETE' });
		if (res.status === 409) {
			toasts.show(t('entry_types_in_use'), 'error');
			return;
		}
		entryTypes = entryTypes.filter((type) => type.id !== editing?.id);
		sheetOpen = false;
	}

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
		return kind;
	}
</script>

<SubPage title={t('entry_types_title')}>
	{#snippet action()}
		<Button size="sm" onclick={openNew}><Plus size={18} />{t('entry_types_new_short')}</Button>
	{/snippet}

	<p class="ui-hint">{t('entry_types_hint')}</p>

	<ul class="list">
		{#each entryTypes as type (type.id)}
			<li class="row" class:hidden={type.hidden}>
				{#if type.isBuiltIn}
					<span class="main">
						<EntryTypeIcon entryType={type} size={42} badge />
						<span class="text">
							<span class="name">{localizeBuiltInText(type.name)}</span>
							<span class="meta">{categoryLabel(type.category)} · {t('entry_types_builtin')}</span>
						</span>
					</span>
				{:else}
					<button type="button" class="main" onclick={() => openEdit(type)} aria-label={t('triggers_edit', { name: type.name })}>
						<EntryTypeIcon entryType={type} size={42} badge />
						<span class="text">
							<span class="name">{type.name}</span>
							<span class="meta">{categoryLabel(type.category)}</span>
						</span>
						<ChevronRight size={18} class="flip-rtl chevron" />
					</button>
				{/if}
				<Switch
					checked={!type.hidden}
					color={type.color}
					label={t('entry_types_show', { name: localizeBuiltInText(type.name) })}
					onchange={(on) => setShown(type, on)}
				/>
			</li>
		{/each}
	</ul>
</SubPage>

<Sheet bind:open={sheetOpen} title={editing ? editing.name : t('entry_types_new')} closeLabel={t('close')} tint={color}>
	<div class="form" style="--type: {color}">
		<div class="preview">
			<EntryTypeIcon entryType={{ id: 'preview', icon, color }} size={64} badge />
		</div>

		<div class="ui-field">
			<label class="ui-label" for="type-name">{t('entry_types_name')}</label>
			<input id="type-name" class="ui-input" bind:value={name} placeholder={t('entry_types_name_placeholder')} maxlength="120" />
		</div>

		{#if !editing}
			<div class="ui-field">
				<span class="ui-label">{t('entry_types_kind')}</span>
				<Segmented
					label={t('entry_types_kind')}
					bind:value={category}
					options={[
						{ value: 'symptom', label: t('entry_category_symptom') },
						{ value: 'event', label: t('entry_category_event') }
					]}
				/>
				<p class="ui-hint">{category === 'symptom' ? t('entry_types_kind_symptom') : t('entry_types_kind_event')}</p>
			</div>
		{/if}

		<div class="ui-field">
			<span class="ui-label" id="color-label">{t('entry_types_color')}</span>
			<div class="colors" role="radiogroup" aria-labelledby="color-label">
				{#each COLORS as option (option)}
					<button
						type="button"
						role="radio"
						aria-checked={color === option}
						aria-label={option}
						class="color"
						class:picked={color === option}
						style="--swatch: {option}"
						onclick={() => (color = option)}
					></button>
				{/each}
			</div>
		</div>

		<div class="ui-field">
			<span class="ui-label" id="icon-label">{t('entry_types_icon')}</span>
			<div class="icons" role="radiogroup" aria-labelledby="icon-label">
				{#each ICON_KEYS as key (key)}
					{@const Icon = ENTRY_ICONS[key]}
					<button
						type="button"
						role="radio"
						aria-checked={icon === `lucide:${key}`}
						aria-label={key}
						class="icon"
						class:picked={icon === `lucide:${key}`}
						onclick={() => (icon = `lucide:${key}`)}
					>
						<Icon size={24} />
					</button>
				{/each}
			</div>
		</div>
	</div>

	{#snippet footer()}
		<div class="actions">
			{#if editing}
				<Button variant="danger" onclick={remove}>{t('delete')}</Button>
			{/if}
			<Button block loading={saving} disabled={!name.trim()} onclick={save} style="--accent: {color}">
				{editing ? t('save') : t('entry_types_add')}
			</Button>
		</div>
	{/snippet}
</Sheet>

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

	.form {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.preview {
		display: flex;
		justify-content: center;
	}

	.colors {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.color {
		width: 40px;
		height: 40px;
		border-radius: 50%;
		border: 3px solid var(--bg);
		background: var(--swatch);
		box-shadow: 0 0 0 2px transparent;
		cursor: pointer;
		transition: transform 0.25s var(--ease-spring);
	}

	.color.picked {
		box-shadow: 0 0 0 2px var(--swatch);
		transform: scale(1.1);
	}

	.icons {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(52px, 1fr));
		gap: var(--space-2);
	}

	.icon {
		aspect-ratio: 1;
		display: grid;
		place-items: center;
		border-radius: var(--radius-md);
		border: 1.5px solid var(--border);
		background: var(--bg-card);
		color: var(--text-muted);
		cursor: pointer;
		transition: transform 0.25s var(--ease-spring);
	}

	.icon:active {
		transform: scale(0.92);
	}

	.icon.picked {
		border-color: var(--type);
		background: color-mix(in srgb, var(--type) 15%, var(--bg-card));
		color: var(--type);
	}

	.actions {
		display: flex;
		gap: var(--space-2);
	}
</style>

<script lang="ts">
	/**
	 * Create or edit an entry type: name, kind, colour and icon. For built-in
	 * types only the options the user added can be removed.
	 */
	import X from '@lucide/svelte/icons/x';
	import { api } from '$lib/api.js';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import Sheet from '$lib/ui/Sheet.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { ENTRY_ICONS } from '$lib/ui/entry-icons.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import type { EntryType } from '$lib/types/index.js';

	interface Props {
		open: boolean;
		/** The type to edit; null to create one. */
		editing: EntryType | null;
		/** Picks the colour of a new type, so neighbours differ. */
		colorIndex?: number;
		onsaved: (type: EntryType, created: boolean) => void;
		ondeleted?: (id: string) => void;
	}

	let { open = $bindable(), editing, colorIndex = 0, onsaved, ondeleted }: Props = $props();

	const COLORS = ['#e0679a', '#d6455d', '#d9822b', '#e3a13b', '#2f9e6e', '#3aa6b9', '#5b8def', '#7462e8', '#a855f7', '#94a3b8'];
	const ICON_KEYS = Object.keys(ENTRY_ICONS);

	let name = $state('');
	let icon = $state('lucide:activity');
	let color = $state(COLORS[0]);
	let category = $state<'symptom' | 'event'>('symptom');
	let extraOptions = $state<Record<string, string[]>>({});
	let saving = $state(false);

	// Fill the form whenever the sheet opens.
	$effect(() => {
		if (!open) return;
		name = editing?.name ?? '';
		icon = editing?.icon ?? 'lucide:activity';
		color = editing?.color ?? COLORS[colorIndex % COLORS.length];
		category = 'symptom';
		extraOptions = structuredClone($state.snapshot(editing?.extraOptions ?? {}));
	});

	const builtIn = $derived(!!editing?.isBuiltIn);
	const ownOptions = $derived(
		(editing?.metricSchema.fields ?? [])
			.filter((f) => (extraOptions[f.key] ?? []).length > 0)
			.map((f) => ({ key: f.key, label: localizeBuiltInText(f.label), options: extraOptions[f.key] }))
	);

	function schemaFor(kind: 'symptom' | 'event') {
		const notes = { key: 'notes', label: 'Notes', type: 'textarea', required: false, config: { placeholder: 'Additional notes...' } };
		const intensity = { key: 'intensity', label: 'Intensity', type: 'slider', required: true, config: { min: 1, max: 5, step: 1 } };
		return { fields: kind === 'symptom' ? [intensity, notes] : [notes] };
	}

	async function save() {
		if (!builtIn && !name.trim()) return;
		saving = true;
		const body = builtIn
			? { extraOptions }
			: editing
				? { name: name.trim(), icon, color, extraOptions }
				: { name: name.trim(), icon, color, category, metricSchema: schemaFor(category) };
		const res = await api(editing ? `/api/entry-types/${editing.id}` : '/api/entry-types', {
			method: editing ? 'PUT' : 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(body)
		});
		saving = false;
		if (!res.ok) {
			const err = await res.json().catch(() => ({}));
			toasts.show(res.status === 409 ? t('entry_types_exists') : (err.message ?? t('network_error')), 'error');
			return;
		}
		const saved: EntryType = await res.json();
		toasts.show(t('log_saved', { name: localizeBuiltInText(saved.name) }), 'success');
		open = false;
		onsaved(saved, !editing);
	}

	async function remove() {
		const target = editing;
		if (!target || !confirm(t('entry_types_delete_confirm'))) return;
		const res = await api(`/api/entry-types/${target.id}`, { method: 'DELETE' });
		if (res.status === 409) {
			toasts.show(t('entry_types_in_use'), 'error');
			return;
		}
		open = false;
		ondeleted?.(target.id);
	}

	function dropOption(key: string, option: string) {
		extraOptions = { ...extraOptions, [key]: (extraOptions[key] ?? []).filter((o) => o !== option) };
	}
</script>

<Sheet bind:open title={editing ? localizeBuiltInText(editing.name) : t('entry_types_new')} closeLabel={t('close')} tint={color}>
	<div class="form" style="--type: {color}">
		<div class="preview">
			<EntryTypeIcon entryType={{ id: 'preview', icon, color }} size={64} badge />
		</div>

		{#if !builtIn}
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
		{/if}

		{#if ownOptions.length}
			<div class="ui-field">
				<span class="ui-label">{t('entry_types_own_options')}</span>
				{#each ownOptions as field (field.key)}
					<p class="ui-hint">{field.label}</p>
					<div class="own">
						{#each field.options as option (option)}
							<button type="button" class="own-chip" onclick={() => dropOption(field.key, option)} aria-label={t('entry_types_remove_option', { name: option })}>
								{option}
								<X size={14} />
							</button>
						{/each}
					</div>
				{/each}
			</div>
		{/if}
	</div>

	{#snippet footer()}
		<div class="actions">
			{#if editing && !builtIn}
				<Button variant="danger" onclick={remove}>{t('delete')}</Button>
			{/if}
			<Button block loading={saving} disabled={!builtIn && !name.trim()} onclick={save} style="--accent: {color}">
				{editing ? t('save') : t('entry_types_add')}
			</Button>
		</div>
	{/snippet}
</Sheet>

<style>
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
	.own {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.own-chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 36px;
		padding: 0 12px;
		border: 1.5px solid var(--border);
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-sm);
		font-weight: 600;
		cursor: pointer;
	}
</style>

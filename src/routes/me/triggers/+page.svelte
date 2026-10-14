<script lang="ts">
	import Plus from '@lucide/svelte/icons/plus';
	import Pencil from '@lucide/svelte/icons/pencil';
	import Utensils from '@lucide/svelte/icons/utensils';
	import CloudSun from '@lucide/svelte/icons/cloud-sun';
	import Footprints from '@lucide/svelte/icons/footprints';
	import Sparkles from '@lucide/svelte/icons/sparkles';
	import Pill from '@lucide/svelte/icons/pill';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';
	import { api } from '$lib/api.js';
	import SubPage from '$lib/me/SubPage.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import Sheet from '$lib/ui/Sheet.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import type { Trigger, TriggerCategory } from '$lib/types/index.js';

	let { data }: { data: { triggers: Trigger[] } } = $props();
	// svelte-ignore state_referenced_locally
	let triggers = $state<Trigger[]>([...data.triggers]);

	const CATEGORIES = [
		{ value: 'food', icon: Utensils, color: '#d9822b', label: () => t('trigger_food') },
		{ value: 'environment', icon: CloudSun, color: '#3aa6b9', label: () => t('trigger_environment') },
		{ value: 'activity', icon: Footprints, color: '#7462e8', label: () => t('trigger_activity') },
		{ value: 'medication', icon: Pill, color: '#e0679a', label: () => t('trigger_medication') },
		{ value: 'custom', icon: Sparkles, color: '#94a3b8', label: () => t('trigger_custom') }
	] as const;

	const byName = (a: Trigger, b: Trigger) => localizeBuiltInText(a.name).localeCompare(localizeBuiltInText(b.name));

	let sheetOpen = $state(false);
	let editing = $state<Trigger | null>(null);
	let name = $state('');
	let category = $state<TriggerCategory>('custom');
	let saving = $state(false);

	function openNew(kind: TriggerCategory = 'custom') {
		editing = null;
		name = '';
		category = kind;
		sheetOpen = true;
	}

	function openEdit(trigger: Trigger) {
		editing = trigger;
		name = localizeBuiltInText(trigger.name);
		category = trigger.category;
		sheetOpen = true;
	}

	const hidden = $derived(triggers.filter((tr) => tr.hidden).sort(byName));

	async function restore(trigger: Trigger) {
		const res = await api(`/api/triggers/${trigger.id}`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ hidden: false })
		});
		if (res.ok) {
			const saved: Trigger = await res.json();
			triggers = triggers.map((tr) => (tr.id === saved.id ? saved : tr));
		}
	}

	async function save() {
		if (!name.trim()) return;
		// A built-in name shown in translation counts as unchanged.
		const unchanged = editing && name.trim() === localizeBuiltInText(editing.name);
		// Another trigger with this name, also under its translated name ("Koffein" for "Caffeine").
		const lower = name.trim().toLowerCase();
		const same = unchanged
			? undefined
			: triggers.find((tr) => tr.id !== editing?.id && (tr.name.toLowerCase() === lower || localizeBuiltInText(tr.name).toLowerCase() === lower));
		// A deleted one is brought back by adding it again; anything else is a clash.
		if (same && (editing || !same.hidden)) {
			toasts.show(t('triggers_exists'), 'error');
			return;
		}
		saving = true;
		const res = await api(editing ? `/api/triggers/${editing.id}` : '/api/triggers', {
			method: editing ? 'PUT' : 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(unchanged ? { category } : { name: same?.name ?? name.trim(), category })
		});
		saving = false;
		if (!res.ok) {
			const err = await res.json().catch(() => ({}));
			toasts.show(res.status === 409 ? t('triggers_exists') : (err.message ?? t('network_error')), 'error');
			return;
		}
		const saved: Trigger = await res.json();
		// A restored trigger is already in the list (as a deleted one): replace it.
		triggers = triggers.some((tr) => tr.id === saved.id) ? triggers.map((tr) => (tr.id === saved.id ? saved : tr)) : [...triggers, saved];
		toasts.show(t('log_saved', { name: localizeBuiltInText(saved.name) }), 'success');
		sheetOpen = false;
	}

	async function remove() {
		const target = editing;
		if (!target || !confirm(t('triggers_delete_confirm', { name: localizeBuiltInText(target.name) }))) return;
		await api(`/api/triggers/${target.id}`, { method: 'DELETE' });
		// Built-in or used triggers stay as hidden ones (see deleteTrigger).
		const res = await api('/api/triggers');
		triggers = res.ok ? await res.json() : triggers.filter((tr) => tr.id !== target.id);
		sheetOpen = false;
	}
</script>

<SubPage title={t('triggers_title')}>
	{#snippet action()}
		<Button size="sm" onclick={() => openNew()}><Plus size={18} />{t('entry_types_new_short')}</Button>
	{/snippet}

	<p class="ui-hint">{t('triggers_hint')}</p>

	{#each CATEGORIES as group (group.value)}
		{@const items = triggers.filter((tr) => tr.category === group.value && !tr.hidden).sort(byName)}
		<Card>
			<h2 class="group-title" style="--group: {group.color}">
				<span class="bubble" aria-hidden="true"><group.icon size={18} /></span>
				{group.label()}
			</h2>
			<div class="chips">
				{#each items as trigger (trigger.id)}
					<button
						type="button"
						class="chip"
						class:own={!trigger.isBuiltIn}
						onclick={() => openEdit(trigger)}
						aria-label={t('triggers_edit', { name: localizeBuiltInText(trigger.name) })}
					>
						{localizeBuiltInText(trigger.name)}
						<Pencil size={14} />
					</button>
				{/each}
				<button type="button" class="chip add" onclick={() => openNew(group.value)} aria-label={t('triggers_add_to', { group: group.label() })}>
					<Plus size={16} />
				</button>
			</div>
		</Card>
	{/each}
	{#if hidden.length}
		<details class="deleted">
			<summary>{t('triggers_deleted', { count: hidden.length })}</summary>
			<p class="ui-hint">{t('triggers_deleted_hint')}</p>
			<div class="chips">
				{#each hidden as trigger (trigger.id)}
					<button type="button" class="chip" onclick={() => restore(trigger)} aria-label={t('triggers_restore', { name: localizeBuiltInText(trigger.name) })}>
						{localizeBuiltInText(trigger.name)}
						<RotateCcw size={14} />
					</button>
				{/each}
			</div>
		</details>
	{/if}
</SubPage>

<Sheet bind:open={sheetOpen} title={editing ? editing.name : t('triggers_new')} closeLabel={t('close')}>
	<form class="form" onsubmit={(e) => { e.preventDefault(); save(); }}>
		<div class="ui-field">
			<label class="ui-label" for="trigger-name">{t('entry_types_name')}</label>
			<input id="trigger-name" class="ui-input" bind:value={name} placeholder={t('triggers_new_placeholder')} maxlength="120" />
		</div>
		<div class="ui-field">
			<span class="ui-label" id="trigger-category">{t('triggers_category')}</span>
			<div class="categories" role="radiogroup" aria-labelledby="trigger-category">
				{#each CATEGORIES as option (option.value)}
					<button
						type="button"
						role="radio"
						aria-checked={category === option.value}
						class="category"
						class:picked={category === option.value}
						style="--group: {option.color}"
						onclick={() => (category = option.value)}
					>
						<option.icon size={18} />
						{option.label()}
					</button>
				{/each}
			</div>
		</div>
	</form>

	{#snippet footer()}
		<div class="actions">
			{#if editing}
				<Button variant="danger" onclick={remove}>{t('delete')}</Button>
			{/if}
			<Button block loading={saving} disabled={!name.trim()} onclick={save}>{editing ? t('save') : t('triggers_add')}</Button>
		</div>
	{/snippet}
</Sheet>

<style>
	.group-title {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		margin: 0 0 var(--space-3);
		font-size: var(--text-md);
		font-weight: 800;
	}

	.bubble {
		width: 32px;
		height: 32px;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: color-mix(in srgb, var(--group) 15%, var(--bg-card));
		color: var(--group);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 36px;
		padding: 0 14px;
		border-radius: var(--radius-pill);
		border: 1.5px solid var(--border);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-sm);
		font-weight: 600;
	}

	button.chip {
		cursor: pointer;
	}

	.chip :global(svg) {
		color: var(--text-subtle);
	}

	.chip.own {
		border-color: var(--accent);
		background: var(--accent-soft);
	}

	.deleted {
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-lg);
		background: var(--bg-subtle);
	}

	.deleted summary {
		font-weight: 800;
		cursor: pointer;
	}

	.deleted .ui-hint {
		margin: var(--space-2) 0;
	}

	.chip.add {
		padding: 0 10px;
		border-style: dashed;
		color: var(--text-muted);
		cursor: pointer;
	}

	.form {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.categories {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-2);
	}

	.category {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-height: 48px;
		padding: 0 var(--space-3);
		border-radius: var(--radius-md);
		border: 1.5px solid var(--border);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-sm);
		font-weight: 700;
		text-align: start;
		cursor: pointer;
	}

	.category :global(svg) {
		color: var(--group);
		flex-shrink: 0;
	}

	.category.picked {
		border-color: var(--group);
		background: color-mix(in srgb, var(--group) 14%, var(--bg-card));
	}

	.actions {
		display: flex;
		gap: var(--space-2);
	}
</style>

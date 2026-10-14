<script lang="ts">
	import Plus from '@lucide/svelte/icons/plus';
	import Pencil from '@lucide/svelte/icons/pencil';
	import Utensils from '@lucide/svelte/icons/utensils';
	import CloudSun from '@lucide/svelte/icons/cloud-sun';
	import Footprints from '@lucide/svelte/icons/footprints';
	import Sparkles from '@lucide/svelte/icons/sparkles';
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
		{ value: 'custom', icon: Sparkles, color: '#e0679a', label: () => t('trigger_custom') }
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
		name = trigger.name;
		category = trigger.category;
		sheetOpen = true;
	}

	async function save() {
		if (!name.trim()) return;
		saving = true;
		const res = await api(editing ? `/api/triggers/${editing.id}` : '/api/triggers', {
			method: editing ? 'PUT' : 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ name: name.trim(), category })
		});
		saving = false;
		if (!res.ok) {
			const err = await res.json().catch(() => ({}));
			toasts.show(err.message ?? t('network_error'), 'error');
			return;
		}
		const saved: Trigger = await res.json();
		triggers = editing ? triggers.map((tr) => (tr.id === saved.id ? saved : tr)) : [...triggers, saved];
		toasts.show(t('log_saved', { name: saved.name }), 'success');
		sheetOpen = false;
	}

	async function remove() {
		if (!editing || !confirm(t('triggers_delete_confirm', { name: editing.name }))) return;
		await api(`/api/triggers/${editing.id}`, { method: 'DELETE' });
		triggers = triggers.filter((tr) => tr.id !== editing?.id);
		sheetOpen = false;
	}
</script>

<SubPage title={t('triggers_title')}>
	{#snippet action()}
		<Button size="sm" onclick={() => openNew()}><Plus size={18} />{t('entry_types_new_short')}</Button>
	{/snippet}

	<p class="ui-hint">{t('triggers_hint')}</p>

	{#each CATEGORIES as group (group.value)}
		{@const items = triggers.filter((tr) => tr.category === group.value).sort(byName)}
		<Card>
			<h2 class="group-title" style="--group: {group.color}">
				<span class="bubble" aria-hidden="true"><group.icon size={18} /></span>
				{group.label()}
			</h2>
			<div class="chips">
				{#each items as trigger (trigger.id)}
					{#if trigger.isBuiltIn}
						<span class="chip">{localizeBuiltInText(trigger.name)}</span>
					{:else}
						<button type="button" class="chip own" onclick={() => openEdit(trigger)} aria-label={t('triggers_edit', { name: trigger.name })}>
							{trigger.name}
							<Pencil size={14} />
						</button>
					{/if}
				{/each}
				<button type="button" class="chip add" onclick={() => openNew(group.value)} aria-label={t('triggers_add_to', { group: group.label() })}>
					<Plus size={16} />
				</button>
			</div>
		</Card>
	{/each}
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

	.chip.own {
		cursor: pointer;
		border-color: var(--accent);
		background: var(--accent-soft);
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

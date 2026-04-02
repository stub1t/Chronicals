<script lang="ts">
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import { localizeBuiltInText, t } from '$lib/stores/locale.js';
	import type { EntryType } from '$lib/types/index.js';

	let { data } = $props();
	// svelte-ignore state_referenced_locally
	let entryTypes = $state<EntryType[]>([...data.entryTypes]);
	let showForm = $state(false);
	let newName = $state('');
	let newIcon = $state('🔴');
	let newColor = $state('#ef4444');
	let saving = $state(false);

	async function addType() {
		if (!newName.trim()) return;
		saving = true;
		const res = await fetch('/api/entry-types', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({
				name: newName.trim(),
				icon: newIcon,
				color: newColor,
				category: 'symptom',
				metricSchema: {
					fields: [
						{
							key: 'intensity',
							label: 'Intensity',
							type: 'slider',
							required: true,
							config: { min: 1, max: 5, step: 1 }
						},
						{
							key: 'notes',
							label: 'Notes',
							type: 'textarea',
							required: false,
							config: { placeholder: 'Additional notes...' }
						}
					]
				}
			})
		});
		if (res.ok) {
			entryTypes = [...entryTypes, await res.json()];
			newName = '';
			showForm = false;
		}
		saving = false;
	}

	async function deleteType(id: string) {
		if (!confirm(t('entry_types_delete_confirm'))) return;
		await fetch(`/api/entry-types/${id}`, { method: 'DELETE' });
		entryTypes = entryTypes.filter((t) => t.id !== id);
	}

	function categoryLabel(category: string): string {
		if (category === 'symptom') return t('entry_category_symptom');
		if (category === 'event') return t('entry_category_event');
		return category;
	}
</script>

<div class="page">
	<header class="page-header">
		<a href="/settings" class="back">‹ {t('settings_title')}</a>
		<h1>{t('entry_types_title')}</h1>
	</header>

	<div class="list">
		{#each entryTypes as type}
			<div class="type-row" style="border-left: 4px solid {type.color ?? '#888'}">
				<EntryTypeIcon entryType={type} size={20} class="type-icon" />
				<div class="type-info">
					<span class="type-name">{localizeBuiltInText(type.name)}</span>
					<span class="type-cat">{categoryLabel(type.category)}</span>
				</div>
				{#if !type.isBuiltIn}
					<button class="delete-btn" onclick={() => deleteType(type.id)}>✕</button>
				{:else}
					<span class="built-in-badge">{t('entry_types_builtin')}</span>
				{/if}
			</div>
		{/each}
	</div>

	{#if showForm}
		<div class="form-card">
			<input bind:value={newName} placeholder={t('entry_types_name_placeholder')} class="input" />
			<div class="row">
				<div class="field">
					<label for="new-type-icon">{t('entry_types_icon')}</label>
					<input id="new-type-icon" bind:value={newIcon} class="input small" maxlength="2" />
				</div>
				<div class="field">
					<label for="new-type-color">{t('entry_types_color')}</label>
					<input id="new-type-color" type="color" bind:value={newColor} class="color-input" />
				</div>
			</div>
			<div class="form-actions">
				<button onclick={() => { showForm = false; }} class="btn-secondary">{t('cancel')}</button>
				<button onclick={addType} disabled={saving || !newName.trim()} class="btn-primary">
					{saving ? t('entry_types_saving') : t('entry_types_add')}
				</button>
			</div>
		</div>
	{:else}
		<button onclick={() => { showForm = true; }} class="add-btn">+ {t('entry_types_add_custom')}</button>
	{/if}
</div>

<style>
	.page { padding: 1rem; }
	.page-header { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.5rem; }
	.page-header h1 { margin: 0; font-size: 1.25rem; }
	.back { color: var(--text-muted); text-decoration: none; font-size: 0.875rem; }
	.list { display: flex; flex-direction: column; gap: 0.5rem; }
	.type-row { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem; background: var(--bg-card); border-radius: 0.5rem; }
	.type-icon { color: var(--text); }
	.type-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
	.type-name { font-weight: 500; font-size: 0.9rem; }
	.type-cat { font-size: 0.75rem; color: var(--text-subtle); text-transform: capitalize; }
	.delete-btn { background: none; border: none; color: var(--text-subtle); cursor: pointer; padding: 0.25rem; }
	.built-in-badge { font-size: 0.7rem; color: var(--text-subtle); background: var(--bg-subtle); padding: 2px 6px; border-radius: 999px; }
	.form-card { margin-top: 1rem; background: var(--bg-card); border-radius: 0.75rem; padding: 1rem; display: flex; flex-direction: column; gap: 0.75rem; }
	.row { display: flex; gap: 1rem; }
	.field { flex: 1; display: flex; flex-direction: column; gap: 4px; }
	.field label { font-size: 0.8rem; color: var(--text-muted); }
	.input { width: 100%; padding: 0.5rem 0.75rem; background: var(--bg-subtle); border: 1px solid var(--border); border-radius: 0.5rem; color: var(--text); font-size: 0.9rem; }
	.input.small { width: 60px; text-align: center; }
	.color-input { width: 60px; height: 38px; border: 1px solid var(--border); border-radius: 0.5rem; cursor: pointer; }
	.form-actions { display: flex; gap: 0.5rem; justify-content: flex-end; }
	.btn-primary { background: var(--text); color: var(--bg); padding: 0.5rem 1rem; border: none; border-radius: 0.5rem; cursor: pointer; font-size: 0.875rem; }
	.btn-primary:disabled { opacity: 0.5; }
	.btn-secondary { background: var(--bg-subtle); color: var(--text); padding: 0.5rem 1rem; border: none; border-radius: 0.5rem; cursor: pointer; font-size: 0.875rem; }
	.add-btn { width: 100%; margin-top: 1rem; padding: 0.75rem; background: var(--bg-card); border: 1px dashed var(--border); border-radius: 0.5rem; color: var(--text-muted); cursor: pointer; font-size: 0.875rem; }
</style>

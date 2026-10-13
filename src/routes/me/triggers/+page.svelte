<script lang="ts">
	import { api } from '$lib/api.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import type { Trigger, TriggerCategory } from '$lib/types/index.js';

	let { data } = $props();
	// svelte-ignore state_referenced_locally
	let triggers = $state<Trigger[]>([...data.triggers]);
	let newName = $state('');
	let newCategory = $state<TriggerCategory>('custom');
	let saving = $state(false);

	const categories: TriggerCategory[] = ['food', 'activity', 'custom'];
	const categoryLabels: Record<TriggerCategory, string> = {
		food: t('trigger_food'),
		environment: t('analysis_environment'),
		activity: t('trigger_activity'),
		custom: t('trigger_custom')
	};

	function groupedTriggers() {
		const map = new Map<string, Trigger[]>();
		for (const cat of categories) map.set(cat, []);
		for (const t of triggers) {
			map.get(t.category)?.push(t);
		}
		return map;
	}

	async function addTrigger() {
		if (!newName.trim()) return;
		saving = true;
		const res = await api('/api/triggers', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ name: newName.trim(), category: newCategory })
		});
		if (res.ok) {
			triggers = [...triggers, await res.json()];
			newName = '';
		}
		saving = false;
	}

	let editingId = $state<string | null>(null);
	let editName = $state('');
	let editCategory = $state<TriggerCategory>('custom');

	function startEdit(trigger: Trigger) {
		editingId = trigger.id;
		editName = trigger.name;
		editCategory = trigger.category;
	}

	async function saveEdit() {
		if (!editingId || !editName.trim()) return;
		const res = await api(`/api/triggers/${editingId}`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ name: editName.trim(), category: editCategory })
		});
		if (res.ok) {
			const updated: Trigger = await res.json();
			triggers = triggers.map((tr) => (tr.id === updated.id ? updated : tr));
			editingId = null;
		}
	}

	async function deleteTrigger(id: string) {
		await api(`/api/triggers/${id}`, { method: 'DELETE' });
		triggers = triggers.filter((t) => t.id !== id);
	}
</script>

<div class="page">
	<header class="page-header">
		<a href="/me" class="back">‹ {t('settings_title')}</a>
		<h1>{t('triggers_title')}</h1>
	</header>

	{#each categories as cat}
		{@const group = groupedTriggers().get(cat) ?? []}
		<section class="category-section">
			<h2 class="cat-title">{categoryLabels[cat]}</h2>
			<div class="chip-grid">
				{#each group as trigger}
					{#if editingId === trigger.id}
						<form class="edit-form" onsubmit={(e) => { e.preventDefault(); saveEdit(); }}>
							<input bind:value={editName} class="input" aria-label={t('triggers_new_placeholder')} />
							<select bind:value={editCategory} class="select" aria-label={t('triggers_category')}>
								{#each categories as option}
									<option value={option}>{categoryLabels[option]}</option>
								{/each}
							</select>
							<button type="submit" class="add-btn" disabled={!editName.trim()}>{t('save')}</button>
							<button type="button" class="cancel-btn" onclick={() => (editingId = null)}>{t('cancel')}</button>
						</form>
					{:else}
						<div class="chip">
							{localizeBuiltInText(trigger.name)}
							{#if !trigger.isBuiltIn}
								<button onclick={() => startEdit(trigger)} class="chip-edit" aria-label={t('triggers_edit', { name: trigger.name })}>✎</button>
								<button onclick={() => deleteTrigger(trigger.id)} class="chip-delete" aria-label={t('triggers_delete', { name: trigger.name })}>✕</button>
							{/if}
						</div>
					{/if}
				{/each}
			</div>
		</section>
	{/each}

	<div class="add-form">
		<input bind:value={newName} placeholder={t('triggers_new_placeholder')} class="input" onkeydown={(e) => e.key === 'Enter' && addTrigger()} />
		<select bind:value={newCategory} class="select">
			{#each categories as cat}
				<option value={cat}>{categoryLabels[cat]}</option>
			{/each}
		</select>
		<button onclick={addTrigger} disabled={saving || !newName.trim()} class="add-btn">{t('triggers_add')}</button>
	</div>
</div>

<style>
	.page { padding: 1rem; }
	.page-header { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.5rem; }
	.page-header h1 { margin: 0; font-size: 1.25rem; }
	.back { color: var(--text-muted); text-decoration: none; font-size: 0.875rem; }
	.category-section { margin-bottom: 1.25rem; }
	.cat-title { font-size: 0.8rem; font-weight: 600; color: var(--text-subtle); text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 0.5rem; }
	.chip-grid { display: flex; flex-wrap: wrap; gap: 0.4rem; }
	.chip { display: flex; align-items: center; gap: 0.25rem; padding: 0.3rem 0.6rem; background: var(--bg-card); border: 1px solid var(--border); border-radius: 999px; font-size: 0.8rem; }
	.chip-delete { background: none; border: none; color: var(--text-subtle); cursor: pointer; padding: 0; font-size: 0.7rem; line-height: 1; }
	.add-form { display: flex; gap: 0.5rem; margin-top: 1rem; }
	.input { flex: 1; padding: 0.5rem 0.75rem; background: var(--bg-card); border: 1px solid var(--border); border-radius: 0.5rem; color: var(--text); font-size: 0.875rem; }
	.select { padding: 0.5rem; background: var(--bg-card); border: 1px solid var(--border); border-radius: 0.5rem; color: var(--text); font-size: 0.875rem; }
	.add-btn { padding: 0.5rem 1rem; background: var(--text); color: var(--bg); border: none; border-radius: 0.5rem; cursor: pointer; font-size: 0.875rem; }
	.add-btn:disabled { opacity: 0.5; }
	.chip-edit { background: none; border: none; color: var(--text-subtle); cursor: pointer; padding: 0; font-size: 0.75rem; line-height: 1; }
	.edit-form { display: flex; flex-wrap: wrap; gap: 0.4rem; width: 100%; }
	.cancel-btn { padding: 0.5rem 0.75rem; background: none; border: 1px solid var(--border); border-radius: 0.5rem; color: var(--text-muted); cursor: pointer; font-size: 0.875rem; }
</style>

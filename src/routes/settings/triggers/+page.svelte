<script lang="ts">
	import type { Trigger, TriggerCategory } from '$lib/types/index.js';

	let { data } = $props();
	// svelte-ignore state_referenced_locally
	let triggers = $state<Trigger[]>([...data.triggers]);
	let newName = $state('');
	let newCategory = $state<TriggerCategory>('custom');
	let saving = $state(false);

	const categories: TriggerCategory[] = ['food', 'activity', 'custom'];
	const categoryLabels: Record<TriggerCategory, string> = {
		food: 'Essen & Trinken',
		environment: 'Umwelt',
		activity: 'Aktivitaet',
		custom: 'Eigene Trigger'
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
		const res = await fetch('/api/triggers', {
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

	async function deleteTrigger(id: string) {
		await fetch(`/api/triggers/${id}`, { method: 'DELETE' });
		triggers = triggers.filter((t) => t.id !== id);
	}
</script>

<div class="page">
	<header class="page-header">
		<a href="/settings" class="back">‹ Einstellungen</a>
		<h1>Trigger</h1>
	</header>

	{#each categories as cat}
		{@const group = groupedTriggers().get(cat) ?? []}
		<section class="category-section">
			<h2 class="cat-title">{categoryLabels[cat]}</h2>
			<div class="chip-grid">
				{#each group as trigger}
					<div class="chip">
						{trigger.name}
						{#if !trigger.isBuiltIn}
							<button onclick={() => deleteTrigger(trigger.id)} class="chip-delete">✕</button>
						{/if}
					</div>
				{/each}
			</div>
		</section>
	{/each}

	<div class="add-form">
		<input bind:value={newName} placeholder="Neuer Trigger" class="input" onkeydown={(e) => e.key === 'Enter' && addTrigger()} />
		<select bind:value={newCategory} class="select">
			{#each categories as cat}
				<option value={cat}>{categoryLabels[cat]}</option>
			{/each}
		</select>
		<button onclick={addTrigger} disabled={saving || !newName.trim()} class="add-btn">Hinzufuegen</button>
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
</style>

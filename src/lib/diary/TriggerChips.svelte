<script lang="ts">
	/**
	 * One-tap triggers for a day: the most used ones as chips, everything else
	 * (and a field to add a new trigger) in a sheet.
	 */
	import Chip from '$lib/ui/Chip.svelte';
	import Sheet from '$lib/ui/Sheet.svelte';
	import Plus from '@lucide/svelte/icons/plus';
	import { api } from '$lib/api.js';
	import { DiaryDay } from './day.svelte.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import { hapticTap } from '$lib/haptics.js';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import type { Trigger, TriggerCategory } from '$lib/types';

	interface Props {
		day: DiaryDay;
		triggers: Trigger[];
		/** Trigger ids by how often they were used recently, most used first. */
		ranking: string[];
		max?: number;
	}

	let { day, triggers = $bindable(), ranking, max = 8 }: Props = $props();

	let showAll = $state(false);
	let newName = $state('');

	const CATEGORIES: TriggerCategory[] = ['activity', 'food', 'environment', 'medication', 'custom'];
	const categoryLabel = (c: TriggerCategory) =>
		({
			activity: t('trigger_activity'),
			food: t('trigger_food'),
			environment: t('trigger_environment'),
			medication: t('trigger_medication'),
			custom: t('trigger_custom')
		})[c];

	// Deleted triggers only show on days that have them.
	const offered = $derived(triggers.filter((tr) => !tr.hidden || day.hasTrigger(tr.id)));

	// Most used first, then today's other picks, then the rest alphabetically.
	const quick = $derived.by(() => {
		const byId = new Map(offered.map((tr) => [tr.id, tr]));
		const ordered = [
			...ranking.map((id) => byId.get(id)).filter((tr): tr is Trigger => !!tr),
			...offered.filter((tr) => day.hasTrigger(tr.id) && !ranking.includes(tr.id)),
			...offered.filter((tr) => !ranking.includes(tr.id))
		];
		return [...new Map(ordered.map((tr) => [tr.id, tr])).values()].slice(0, max);
	});

	async function toggle(id: string) {
		hapticTap();
		try {
			await day.toggleTrigger(id);
		} catch {
			toasts.show(t('network_error'), 'error');
		}
	}

	async function addTrigger() {
		const name = newName.trim();
		if (!name) return;
		// A trigger with that name exists already, also under its translated name
		// ("Koffein" for the built-in "Caffeine"): pick it instead of adding a second one.
		const existing = triggers.find((tr) => tr.name.toLowerCase() === name.toLowerCase() || localizeBuiltInText(tr.name).toLowerCase() === name.toLowerCase());
		if (existing && !existing.hidden) {
			newName = '';
			if (!day.hasTrigger(existing.id)) await toggle(existing.id);
			return;
		}
		// A deleted one comes back (the server restores it by its stored name).
		const res = await api('/api/triggers', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ name: existing?.name ?? name, category: existing?.category ?? 'custom' })
		});
		if (!res.ok) {
			toasts.show(t('network_error'), 'error');
			return;
		}
		const created: Trigger = await res.json();
		triggers = [...triggers.filter((tr) => tr.id !== created.id), created];
		newName = '';
		await toggle(created.id);
	}
</script>

<div class="chips">
	{#each quick as trigger (trigger.id)}
		<Chip selected={day.hasTrigger(trigger.id)} onclick={() => toggle(trigger.id)}>
			{localizeBuiltInText(trigger.name)}
		</Chip>
	{/each}
	<button type="button" class="more" onclick={() => (showAll = true)}>
		<Plus size={16} />
		{t('add_new')}
	</button>
</div>

<Sheet bind:open={showAll} title={t('triggers_title')} closeLabel={t('close')}>
	{#each CATEGORIES as category (category)}
		{@const group = offered.filter((tr) => tr.category === category)}
		{#if group.length}
			<h3 class="group">{categoryLabel(category)}</h3>
			<div class="chips">
				{#each group as trigger (trigger.id)}
					<Chip selected={day.hasTrigger(trigger.id)} onclick={() => toggle(trigger.id)}>
						{localizeBuiltInText(trigger.name)}
					</Chip>
				{/each}
			</div>
		{/if}
	{/each}
	<form
		class="add"
		onsubmit={(e) => {
			e.preventDefault();
			addTrigger();
		}}
	>
		<input class="input" bind:value={newName} placeholder={t('triggers_new_placeholder')} aria-label={t('triggers_new_placeholder')} />
		<button class="add-button" type="submit" disabled={!newName.trim()} aria-label={t('triggers_add')}><Plus size={20} /></button>
	</form>
</Sheet>

<style>
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.more {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		min-height: 38px;
		padding: 0 14px;
		border: 1.5px dashed var(--border);
		border-radius: var(--radius-pill);
		background: transparent;
		color: var(--text-muted);
		font-size: var(--text-sm);
		font-weight: 700;
		cursor: pointer;
	}

	.group {
		margin: var(--space-4) 0 var(--space-2);
		font-size: var(--text-sm);
		font-weight: 800;
		color: var(--text-muted);
	}

	.add {
		display: flex;
		gap: var(--space-2);
		margin-top: var(--space-5);
	}

	.input {
		flex: 1;
		min-height: 46px;
		padding: 0 var(--space-4);
		border: 1.5px solid var(--border);
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-md);
	}

	.add-button {
		width: 46px;
		height: 46px;
		display: grid;
		place-items: center;
		border: none;
		border-radius: 50%;
		background: var(--accent);
		color: var(--on-accent);
		cursor: pointer;
	}

	.add-button:disabled {
		opacity: 0.4;
	}
</style>

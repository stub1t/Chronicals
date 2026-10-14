<script lang="ts">
	/** Do my medications help: the 30 days before each start or dose change against the 30 days after. */
	import Pill from '@lucide/svelte/icons/pill';
	import Card from '$lib/ui/Card.svelte';
	import { dateLocale, formatNumber, t } from '$lib/i18n';
	import type { MedicationEffect } from './engine.js';

	interface Props {
		typeName: string;
		color: string;
		effects: MedicationEffect[];
	}

	let { typeName, color, effects }: Props = $props();

	const one = (n: number | null) => (n === null ? '–' : formatNumber(n, { maximumFractionDigits: 1 }));
	const date = (d: string) => new Date(`${d}T12:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' });
	const title = (e: MedicationEffect) =>
		e.event.kind === 'started'
			? t('insights_med_started', { name: e.event.name, dose: e.event.newDosage ?? '' }).trim()
			: t('insights_med_dose', { name: e.event.name, old: e.event.oldDosage ?? '?', new: e.event.newDosage ?? '?' });
	const scale = (e: MedicationEffect) => Math.max(e.before.per30 ?? 0, e.after.per30 ?? 0, 1);
</script>

<Card>
	<p class="intro">{t('insights_meds_intro')}</p>
	<ul class="effects">
		{#each effects as effect (effect.event.medicationId + effect.event.date + effect.event.kind)}
			<li class="effect">
				<div class="head">
					<span class="bubble" aria-hidden="true"><Pill size={18} /></span>
					<span class="text">
						<span class="name">{title(effect)}</span>
						<span class="date">{date(effect.event.date)}</span>
					</span>
				</div>
				{#if effect.result === null}
					<p class="muted">{t('insights_med_unknown')}</p>
				{:else}
					<div class="compare" style="--type: {color}">
						{#each [['insights_before', effect.before], ['insights_after', effect.after]] as const as [key, side] (key)}
							<span class="label">{t(key)}</span>
							<span class="track"><span class="bar" class:after={key === 'insights_after'} style="width: {((side.per30 ?? 0) / scale(effect)) * 100}%"></span></span>
							<span class="value">
								{t('insights_per30_short', { value: one(side.per30) })}
								{#if side.avgIntensity !== null}<span class="detail">{t('insights_intensity_short', { value: one(side.avgIntensity) })}</span>{/if}
							</span>
						{/each}
					</div>
					<p class="result {effect.result}">{t(`insights_med_${effect.result}`, { type: typeName })}</p>
				{/if}
			</li>
		{/each}
	</ul>
	<p class="note">{t('insights_meds_note')}</p>
</Card>

<style>
	.intro,
	.muted {
		margin: 0;
		color: var(--text-muted);
		font-size: var(--text-sm);
	}

	.effects {
		list-style: none;
		margin: var(--space-4) 0 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin-bottom: var(--space-3);
	}

	.bubble {
		width: 36px;
		height: 36px;
		flex-shrink: 0;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: color-mix(in srgb, #e0679a 15%, var(--bg-card));
		color: #e0679a;
	}

	.text {
		display: flex;
		flex-direction: column;
	}

	.name {
		font-weight: 800;
	}

	.date {
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.compare {
		display: grid;
		grid-template-columns: auto 1fr auto;
		align-items: center;
		gap: var(--space-2) var(--space-3);
	}

	.label {
		font-size: var(--text-xs);
		font-weight: 700;
		color: var(--text-muted);
	}

	.track {
		height: 12px;
		border-radius: var(--radius-pill);
		background: var(--bg-subtle);
		overflow: hidden;
	}

	.bar {
		display: block;
		height: 100%;
		min-width: 3px;
		border-radius: var(--radius-pill);
		background: color-mix(in srgb, var(--type) 35%, var(--bg-card));
	}

	.bar.after {
		background: var(--type);
	}

	.value {
		font-size: var(--text-sm);
		font-weight: 800;
		text-align: end;
	}

	.detail {
		display: block;
		font-size: var(--text-xs);
		font-weight: 600;
		color: var(--text-muted);
	}

	.result {
		margin: var(--space-3) 0 0;
		font-weight: 800;
		font-size: var(--text-sm);
	}

	.result.fewer {
		color: var(--positive);
	}

	.result.more {
		color: var(--warning);
	}

	.result.similar {
		color: var(--text-muted);
	}

	.note {
		margin: var(--space-5) 0 0;
		font-size: var(--text-xs);
		color: var(--text-muted);
		line-height: 1.45;
	}
</style>

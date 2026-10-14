<script lang="ts">
	/** What goes along with it: triggers and weather with clear links first, the rest as chips. */
	import Card from '$lib/ui/Card.svelte';
	import { formatNumber, localizeBuiltInText, t, type MessageKey } from '$lib/i18n';
	import type { Link } from './engine.js';

	interface Props {
		typeName: string;
		color: string;
		links: Link[];
		/** Trigger names by id. */
		names: Map<string, string>;
	}

	let { typeName, color, links, names }: Props = $props();

	const nameOf = (link: Link) =>
		link.kind === 'weather' ? t(`insights_weather_${link.key.replaceAll('-', '_')}` as MessageKey) : localizeBuiltInText(names.get(link.key) ?? link.key);
	const percent = (v: number) => formatNumber(v, { style: 'percent', maximumFractionDigits: 0 });

	const found = $derived(links.filter((l) => l.label === 'likely' || l.label === 'possible' || l.label === 'fewer'));
	const unclear = $derived(links.filter((l) => l.label === 'none'));
	const tooFew = $derived(links.filter((l) => l.label === 'not-enough'));
</script>

<Card>
	{#if links.length === 0}
		<p class="muted">{t('insights_no_triggers')}</p>
	{:else}
		{#if found.length === 0}
			<p class="muted">{t('insights_nothing_found', { type: typeName })}</p>
		{/if}
		<ul class="found">
			{#each found as link (link.key)}
				<li class="link {link.label}">
					<div class="title">
						<span class="name">{nameOf(link)}</span>
						<span class="pill">{t(`insights_label_${link.label as 'likely' | 'possible' | 'fewer'}`)}</span>
					</div>
					<p class="sentence">
						{t(link.lag ? 'insights_link_next' : 'insights_link_same', {
							trigger: nameOf(link),
							type: typeName,
							with: percent(link.withRate),
							without: percent(link.withoutRate)
						})}
						{#if link.label !== 'fewer' && Number.isFinite(link.ratio)}
							<strong>{t('insights_ratio', { ratio: formatNumber(link.ratio, { maximumFractionDigits: 1 }) })}</strong>
						{/if}
					</p>
					<div class="compare" style="--type: {color}" aria-hidden="true">
						<span class="bar-label">{t(link.lag ? 'insights_with_next' : 'insights_with')}</span>
						<span class="track"><span class="bar strong" style="width: {link.withRate * 100}%"></span></span>
						<span class="value">{percent(link.withRate)}</span>
						<span class="bar-label">{t('insights_without')}</span>
						<span class="track"><span class="bar" style="width: {link.withoutRate * 100}%"></span></span>
						<span class="value">{percent(link.withoutRate)}</span>
					</div>
					<p class="days">{t('insights_link_days', { count: link.exposedDays })}</p>
				</li>
			{/each}
		</ul>

		{#if unclear.length}
			<h3 class="sub">{t('insights_no_link')}</h3>
			<p class="chips">{#each unclear as link (link.key)}<span class="chip">{nameOf(link)}</span>{/each}</p>
		{/if}
		{#if tooFew.length}
			<h3 class="sub">{t('insights_not_enough')}</h3>
			<p class="chips">{#each tooFew as link (link.key)}<span class="chip faint">{nameOf(link)}</span>{/each}</p>
		{/if}
	{/if}
	<p class="note">{t('insights_disclaimer')}</p>
</Card>

<style>
	.muted {
		margin: 0;
		color: var(--text-muted);
	}

	.found {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.title {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
	}

	.name {
		font-size: var(--text-lg);
		font-weight: 800;
	}

	.pill {
		flex-shrink: 0;
		padding: 3px 10px;
		border-radius: var(--radius-pill);
		font-size: var(--text-xs);
		font-weight: 800;
	}

	.likely .pill {
		background: var(--danger-soft);
		color: var(--danger);
	}

	.possible .pill {
		background: var(--warning-soft);
		color: var(--warning);
	}

	.fewer .pill {
		background: var(--positive-soft);
		color: var(--positive);
	}

	.sentence {
		margin: var(--space-1) 0 var(--space-3);
		color: var(--text-muted);
		line-height: 1.45;
	}

	.sentence strong {
		color: var(--text);
	}

	.compare {
		display: grid;
		grid-template-columns: auto 1fr auto;
		align-items: center;
		gap: 6px var(--space-3);
	}

	.bar-label {
		font-size: var(--text-xs);
		font-weight: 700;
		color: var(--text-muted);
	}

	.track {
		height: 10px;
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

	.bar.strong {
		background: var(--type);
	}

	.value {
		font-size: var(--text-sm);
		font-weight: 800;
		text-align: end;
		font-variant-numeric: tabular-nums;
	}

	.days {
		margin: var(--space-2) 0 0;
		font-size: var(--text-xs);
		color: var(--text-subtle);
	}

	.sub {
		margin: var(--space-5) 0 var(--space-2);
		font-size: var(--text-sm);
		font-weight: 800;
		color: var(--text-muted);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		margin: 0;
	}

	.chip {
		padding: 4px 12px;
		border-radius: var(--radius-pill);
		background: var(--bg-subtle);
		font-size: var(--text-sm);
		font-weight: 600;
	}

	.chip.faint {
		color: var(--text-muted);
	}

	.note {
		margin: var(--space-5) 0 0;
		font-size: var(--text-xs);
		color: var(--text-muted);
		line-height: 1.45;
	}
</style>

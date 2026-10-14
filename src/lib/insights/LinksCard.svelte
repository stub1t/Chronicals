<script lang="ts">
	/** What goes along with it: clear links first, then what is worth watching; the rest folded away. */
	import Card from '$lib/ui/Card.svelte';
	import { formatNumber, language, localizeBuiltInText, t, type MessageKey } from '$lib/i18n';
	import type { Link } from './engine.js';

	interface Props {
		typeName: string;
		color: string;
		links: Link[];
		/** Trigger names by id. */
		names: Map<string, string>;
	}

	let { typeName, color, links, names }: Props = $props();

	const nameOf = (link: Link) => {
		if (link.kind === 'weather') return t(`insights_weather_${link.key.replaceAll('-', '_')}` as MessageKey);
		if (link.kind === 'pollen') return t('insights_pollen_high', { species: t(`pollen_${link.key}` as MessageKey) });
		if (link.kind === 'food') return localizeBuiltInText(link.key);
		return localizeBuiltInText(names.get(link.key) ?? link.key);
	};
	const percent = (v: number) => formatNumber(v, { style: 'percent', maximumFractionDigits: 0 });
	// Inside a sentence a name starts in lower case ("after poor sleep"), except in German and for abbreviations.
	const inSentence = (name: string) =>
		$language === 'de' || /^\p{Lu}{2}/u.test(name) ? name : name.charAt(0).toLocaleLowerCase() + name.slice(1);

	const shown = $derived(links.filter((l) => l.label === 'likely' || l.label === 'fewer' || l.label === 'watch'));
	const rest = $derived(links.filter((l) => l.label === 'none' || l.label === 'not-enough'));
</script>

<Card>
	{#if links.length === 0}
		<p class="muted">{t('insights_no_triggers')}</p>
	{:else}
		{#if shown.length === 0}
			<p class="muted">{t('insights_nothing_found', { type: typeName })}</p>
		{/if}
		<ul class="found">
			{#each shown as link (link.kind + link.key)}
				<li class="link {link.label}">
					<div class="title">
						<span class="name">{nameOf(link)}</span>
						<span class="pill">{t(`insights_label_${link.label as 'likely' | 'fewer' | 'watch'}`)}</span>
					</div>
					<p class="tags">
						<span>{t(`insights_kind_${link.kind}`)}</span>
						<span>{t(link.lag ? 'insights_lag_next' : 'insights_lag_same')}</span>
					</p>
					<p class="sentence">
						{t(link.lag ? 'insights_link_next' : 'insights_link_same', {
							trigger: inSentence(nameOf(link)),
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

		{#if rest.length}
			<details class="rest">
				<summary>{t('insights_rest', { count: rest.length })}</summary>
				<p class="chips">
					{#each rest as link (link.kind + link.key)}
						<span class="chip" class:faint={link.label === 'not-enough'} title={link.label === 'not-enough' ? t('insights_not_enough') : t('insights_no_link')}>
							{nameOf(link)}{#if link.label === 'not-enough'}<span class="sr-only"> ({t('insights_not_enough')})</span>{/if}
						</span>
					{/each}
				</p>
				<p class="muted small">{t('insights_rest_hint')}</p>
			</details>
		{/if}
	{/if}
	<p class="note">{t('insights_disclaimer')}</p>
</Card>

<style>
	.muted {
		margin: 0;
		color: var(--text-muted);
	}

	.small {
		font-size: var(--text-xs);
		margin-top: var(--space-2);
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

	.watch .pill {
		background: var(--bg-subtle);
		color: var(--text-muted);
	}

	.fewer .pill {
		background: var(--positive-soft);
		color: var(--positive);
	}

	.tags {
		display: flex;
		gap: 6px;
		margin: 4px 0 0;
		font-size: var(--text-xs);
		font-weight: 700;
		color: var(--text-muted);
	}

	.tags span + span::before {
		content: '·';
		margin-inline-end: 6px;
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

	.watch .bar.strong {
		background: color-mix(in srgb, var(--type) 60%, var(--bg-card));
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

	.rest {
		margin-top: var(--space-5);
		padding: var(--space-3);
		border-radius: var(--radius-md);
		background: var(--bg-subtle);
	}

	.rest summary {
		font-size: var(--text-sm);
		font-weight: 800;
		cursor: pointer;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		margin: var(--space-2) 0 0;
	}

	.chip {
		padding: 4px 12px;
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		font-size: var(--text-sm);
		font-weight: 600;
	}

	.chip.faint {
		color: var(--text-muted);
		border: 1px dashed var(--border);
	}

	.note {
		margin: var(--space-5) 0 0;
		font-size: var(--text-xs);
		color: var(--text-muted);
		line-height: 1.45;
	}
</style>

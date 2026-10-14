<script lang="ts">
	/** Horizontal bars for shares (0 to 1) with the value written out, e.g. symptom days per weekday. */
	import { formatNumber } from '$lib/i18n';

	interface Row {
		label: string;
		value: number;
		/** Small text after the value, e.g. "3 of 12 days". */
		detail?: string;
		highlight?: boolean;
		/** Drawn faintly, e.g. too few days to say anything. */
		faint?: boolean;
	}

	interface Props {
		rows: Row[];
		color: string;
		/** Scale end; defaults to the largest value. */
		max?: number;
		/** Show values as counts instead of percentages. */
		count?: boolean;
	}

	let { rows, color, max, count = false }: Props = $props();

	const top = $derived(max ?? Math.max(...rows.map((r) => r.value), count ? 1 : 0.01));
</script>

<ul class="rows" style="--bar: {color}">
	{#each rows as row (row.label)}
		<li class="row" class:faint={row.faint}>
			<span class="label">{row.label}</span>
			<span class="track"><span class="bar" class:highlight={row.highlight} style="width: {(row.value / top) * 100}%"></span></span>
			<span class="value">
				{count ? formatNumber(row.value) : formatNumber(row.value, { style: 'percent', maximumFractionDigits: 0 })}
				{#if row.detail}<span class="detail">{row.detail}</span>{/if}
			</span>
		</li>
	{/each}
</ul>

<style>
	.rows {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: auto 1fr auto;
		align-items: center;
		gap: var(--space-2) var(--space-3);
	}

	.row {
		display: contents;
	}

	.label {
		font-size: var(--text-sm);
		font-weight: 700;
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
		background: color-mix(in srgb, var(--bar) 55%, var(--bg-card));
	}

	.bar.highlight {
		background: var(--bar);
	}

	.value {
		font-size: var(--text-sm);
		font-weight: 800;
		font-variant-numeric: tabular-nums;
		text-align: end;
	}

	.detail {
		display: block;
		font-size: var(--text-xs);
		font-weight: 600;
		color: var(--text-muted);
	}

	.row.faint > * {
		opacity: 0.45;
	}
</style>

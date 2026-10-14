<script lang="ts">
	import { WELLBEING_COLORS } from './wellbeing.js';

	/** "How are you today?" as five steps from very bad (1) to very good (5), each with its word. */
	interface Props {
		value: number | null;
		labels: [string, string, string, string, string];
		groupLabel: string;
		onchange?: (value: number) => void;
	}

	let { value = $bindable(), labels, groupLabel, onchange }: Props = $props();
</script>

<div class="scale" role="radiogroup" aria-label={groupLabel}>
	{#each labels as label, i (i)}
		{@const rating = i + 1}
		<button
			type="button"
			role="radio"
			aria-checked={value === rating}
			class="step"
			class:selected={value === rating}
			class:dimmed={value !== null && value !== rating}
			style="--step: {WELLBEING_COLORS[i]}"
			onclick={() => {
				value = rating;
				onchange?.(rating);
			}}
		>
			<span class="bar" aria-hidden="true"></span>
			<span class="word">{label}</span>
		</button>
	{/each}
</div>

<style>
	.scale {
		display: grid;
		grid-template-columns: repeat(5, 1fr);
		gap: 6px;
	}

	.step {
		display: flex;
		flex-direction: column;
		align-items: stretch;
		gap: 6px;
		min-height: 64px;
		padding: 8px 4px;
		border: 2px solid transparent;
		border-radius: var(--radius-md);
		background: color-mix(in srgb, var(--step) 14%, var(--bg-card));
		color: var(--text);
		cursor: pointer;
		transition:
			transform 0.3s var(--ease-spring),
			opacity 0.2s,
			border-color 0.2s;
	}

	.step:active {
		transform: scale(0.94);
	}

	.bar {
		height: 8px;
		border-radius: var(--radius-pill);
		background: var(--step);
	}

	.word {
		font-size: var(--text-xs);
		font-weight: 700;
		line-height: 1.2;
		text-align: center;
		hyphens: auto;
	}

	.selected {
		border-color: var(--step);
		background: color-mix(in srgb, var(--step) 30%, var(--bg-card));
		transform: translateY(-2px);
	}

	.selected .word {
		font-weight: 800;
	}

	.dimmed {
		opacity: 0.55;
	}
</style>

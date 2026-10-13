<script lang="ts" generics="T extends string | number">
	interface Props {
		options: Array<{ value: T; label: string }>;
		value: T;
		label: string;
		onchange?: (value: T) => void;
	}

	let { options, value = $bindable(), label, onchange }: Props = $props();
</script>

<div class="segmented" role="radiogroup" aria-label={label}>
	{#each options as option (option.value)}
		<button
			type="button"
			role="radio"
			aria-checked={value === option.value}
			class:active={value === option.value}
			onclick={() => {
				value = option.value;
				onchange?.(option.value);
			}}
		>
			{option.label}
		</button>
	{/each}
</div>

<style>
	.segmented {
		display: flex;
		padding: 4px;
		gap: 4px;
		background: var(--bg-subtle);
		border-radius: var(--radius-pill);
	}

	button {
		flex: 1;
		min-height: 38px;
		padding: 0 var(--space-3);
		border: none;
		border-radius: var(--radius-pill);
		background: transparent;
		color: var(--text-muted);
		font-size: var(--text-sm);
		font-weight: 700;
		cursor: pointer;
		transition:
			background-color 0.2s,
			color 0.2s,
			box-shadow 0.2s;
	}

	.active {
		background: var(--bg-card);
		color: var(--text);
		box-shadow: var(--shadow-sm);
	}
</style>

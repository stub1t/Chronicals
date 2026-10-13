<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		selected?: boolean;
		/** Accent colour when selected (defaults to the app accent). */
		color?: string | null;
		onclick?: () => void;
		label?: string;
		children: Snippet;
	}

	let { selected = false, color = null, onclick, label, children }: Props = $props();
</script>

<button
	type="button"
	class="chip"
	class:selected
	style={color ? `--chip: ${color};` : ''}
	aria-pressed={selected}
	aria-label={label}
	{onclick}
>
	{@render children()}
</button>

<style>
	.chip {
		--chip: var(--accent);
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 38px;
		padding: 0 14px;
		border-radius: var(--radius-pill);
		border: 1.5px solid var(--border);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-sm);
		font-weight: 600;
		cursor: pointer;
		transition:
			transform 0.25s var(--ease-spring),
			background-color 0.15s,
			border-color 0.15s;
	}

	.chip:active {
		transform: scale(0.94);
	}

	.selected {
		background: color-mix(in srgb, var(--chip) 16%, var(--bg-card));
		border-color: var(--chip);
		color: var(--text);
	}
</style>

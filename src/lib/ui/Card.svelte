<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLAttributes } from 'svelte/elements';

	interface Props extends HTMLAttributes<HTMLDivElement> {
		/** An entry type colour: tints the background softly. */
		tint?: string | null;
		padding?: 'none' | 'sm' | 'md';
		children: Snippet;
	}

	let { tint = null, padding = 'md', class: className = '', style = '', children, ...rest }: Props = $props();
</script>

<div
	class="card pad-{padding} {className}"
	style="{tint ? `--tint: ${tint};` : ''}{style}"
	class:tinted={!!tint}
	{...rest}
>
	{@render children()}
</div>

<style>
	.card {
		background: var(--bg-card);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
	}

	.tinted {
		background: color-mix(in srgb, var(--tint) 10%, var(--bg-card));
		border-color: color-mix(in srgb, var(--tint) 22%, transparent);
	}

	.pad-sm {
		padding: var(--space-3);
	}

	.pad-md {
		padding: var(--space-4) var(--space-5);
	}
</style>

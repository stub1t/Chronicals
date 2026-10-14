<script lang="ts">
	/** A titled card of MenuRows, divided by thin lines. */
	import type { Snippet } from 'svelte';

	interface Props {
		title?: string;
		children: Snippet;
	}

	let { title, children }: Props = $props();
</script>

<section class="group">
	{#if title}<h2 class="title">{title}</h2>{/if}
	<div class="card">{@render children()}</div>
</section>

<style>
	.group {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.title {
		margin: 0 var(--space-1);
		font-size: var(--text-lg);
		font-weight: 800;
	}

	.card {
		background: var(--bg-card);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		overflow: hidden;
	}

	.card > :global(*) + :global(*) {
		border-top: 1px solid var(--border);
	}
</style>

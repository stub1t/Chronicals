<script lang="ts">
	/** One row in a settings list: a link, a button, or a container for a control. */
	import type { Component, Snippet } from 'svelte';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';

	interface Props {
		icon: Component<{ size?: number }>;
		/** Colour of the icon bubble. */
		color?: string;
		label: string;
		detail?: string | null;
		href?: string;
		/** Opens `href` outside the app. */
		external?: boolean;
		onclick?: () => void;
		/** A control on the trailing side (toggle, time input...); replaces the chevron. */
		trailing?: Snippet;
	}

	let { icon: Icon, color = 'var(--accent)', label, detail = null, href, external = false, onclick, trailing }: Props = $props();
</script>

{#snippet body()}
	<span class="bubble" style="--bubble: {color}" aria-hidden="true"><Icon size={20} /></span>
	<span class="text">
		<span class="label">{label}</span>
		{#if detail}<span class="detail">{detail}</span>{/if}
	</span>
	{#if trailing}
		{@render trailing()}
	{:else if href || onclick}
		<ChevronRight size={20} class="flip-rtl chevron" />
	{/if}
{/snippet}

{#if href}
	<a class="row" {href} target={external ? '_blank' : undefined} rel={external ? 'noopener noreferrer' : undefined}>{@render body()}</a>
{:else if onclick}
	<button type="button" class="row" {onclick}>{@render body()}</button>
{:else}
	<div class="row">{@render body()}</div>
{/if}

<style>
	.row {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		width: 100%;
		min-height: 64px;
		padding: var(--space-2) var(--space-4);
		border: none;
		background: none;
		color: var(--text);
		text-align: start;
		text-decoration: none;
		font-size: var(--text-md);
		box-sizing: border-box;
	}

	a.row,
	button.row {
		cursor: pointer;
	}

	a.row:active,
	button.row:active {
		background: var(--bg-subtle);
	}

	.bubble {
		width: 40px;
		height: 40px;
		flex-shrink: 0;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: color-mix(in srgb, var(--bubble) 15%, var(--bg-card));
		color: var(--bubble);
	}

	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}

	.label {
		font-weight: 700;
	}

	.detail {
		font-size: var(--text-sm);
		color: var(--text-muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.row :global(.chevron) {
		color: var(--text-subtle);
		flex-shrink: 0;
	}
</style>

<script lang="ts">
	import type { Component, Snippet } from 'svelte';

	interface Props {
		icon: Component<{ size?: number; strokeWidth?: number }>;
		title: string;
		text?: string;
		/** Colour of the soft blobs behind the icon. */
		color?: string;
		action?: Snippet;
	}

	let { icon: Icon, title, text, color = 'var(--accent)', action }: Props = $props();
</script>

<div class="empty" style="--blob: {color}">
	<div class="art" aria-hidden="true">
		<span class="blob one"></span>
		<span class="blob two"></span>
		<span class="icon"><Icon size={34} strokeWidth={1.8} /></span>
	</div>
	<p class="title">{title}</p>
	{#if text}<p class="text">{text}</p>{/if}
	{#if action}<div class="action">{@render action()}</div>{/if}
</div>

<style>
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: var(--space-2);
		padding: var(--space-6) var(--space-4);
	}

	.art {
		position: relative;
		width: 110px;
		height: 96px;
		margin-bottom: var(--space-2);
	}

	.blob {
		position: absolute;
		border-radius: 50% 45% 55% 48%;
		background: color-mix(in srgb, var(--blob) 18%, transparent);
	}

	.one {
		inset: 4px 14px 0 6px;
		animation: float 6s ease-in-out infinite;
	}

	.two {
		inset: 18px 0 10px 30px;
		background: color-mix(in srgb, var(--blob) 12%, transparent);
		animation: float 7s ease-in-out infinite reverse;
	}

	.icon {
		position: absolute;
		inset: 0;
		display: grid;
		place-items: center;
		color: var(--blob);
	}

	.title {
		margin: 0;
		font-weight: 800;
		font-size: var(--text-lg);
	}

	.text {
		margin: 0;
		max-width: 32ch;
		color: var(--text-muted);
		font-size: var(--text-sm);
		line-height: 1.5;
	}

	.action {
		margin-top: var(--space-3);
	}

	@keyframes float {
		50% {
			transform: translateY(-5px) rotate(4deg);
		}
	}
</style>

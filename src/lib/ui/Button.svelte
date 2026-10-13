<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	interface Props extends HTMLButtonAttributes {
		variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
		size?: 'sm' | 'md' | 'lg';
		block?: boolean;
		loading?: boolean;
		children: Snippet;
	}

	let {
		variant = 'primary',
		size = 'md',
		block = false,
		loading = false,
		type = 'button',
		disabled,
		class: className = '',
		children,
		...rest
	}: Props = $props();
</script>

<button
	{type}
	class="btn {variant} {size} {className}"
	class:block
	disabled={disabled || loading}
	aria-busy={loading || undefined}
	{...rest}
>
	{#if loading}<span class="spinner" aria-hidden="true"></span>{/if}
	{@render children()}
</button>

<style>
	.btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		border: 1.5px solid transparent;
		border-radius: var(--radius-pill);
		font-weight: 700;
		cursor: pointer;
		transition:
			transform 0.25s var(--ease-spring),
			background-color 0.15s,
			border-color 0.15s,
			opacity 0.15s;
		user-select: none;
		white-space: nowrap;
	}

	.btn:active:not(:disabled) {
		transform: scale(0.95);
	}

	.btn:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.sm {
		min-height: 36px;
		padding: 0 var(--space-3);
		font-size: var(--text-sm);
	}

	.md {
		min-height: 46px;
		padding: 0 var(--space-5);
		font-size: var(--text-md);
	}

	.lg {
		min-height: 54px;
		padding: 0 var(--space-6);
		font-size: var(--text-lg);
	}

	.block {
		width: 100%;
	}

	.primary {
		background: var(--accent);
		color: var(--on-accent);
		box-shadow: 0 4px 14px color-mix(in srgb, var(--accent) 30%, transparent);
	}

	.secondary {
		background: var(--bg-card);
		color: var(--text);
		border-color: var(--border);
	}

	.ghost {
		background: transparent;
		color: var(--text-muted);
	}

	.ghost:hover:not(:disabled) {
		background: var(--bg-subtle);
		color: var(--text);
	}

	.danger {
		background: var(--danger-soft);
		color: var(--danger);
	}

	.spinner {
		width: 1em;
		height: 1em;
		border: 2px solid currentColor;
		border-inline-end-color: transparent;
		border-radius: 50%;
		animation: spin 0.7s linear infinite;
	}

	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}
</style>

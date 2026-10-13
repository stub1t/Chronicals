<script lang="ts">
	import { fly } from 'svelte/transition';
	import { backOut } from 'svelte/easing';
	import CircleCheck from '@lucide/svelte/icons/circle-check';
	import Info from '@lucide/svelte/icons/info';
	import TriangleAlert from '@lucide/svelte/icons/triangle-alert';
	import { toasts } from './toast.svelte.js';
</script>

<div class="toaster" aria-live="polite">
	{#each toasts.items as toast (toast.id)}
		<div class="toast {toast.kind}" transition:fly={{ y: 30, duration: 320, easing: backOut }}>
			{#if toast.kind === 'success'}<CircleCheck size={18} />{:else if toast.kind === 'error'}<TriangleAlert
					size={18}
				/>{:else}<Info size={18} />{/if}
			<span>{toast.text}</span>
		</div>
	{/each}
</div>

<style>
	.toaster {
		position: fixed;
		inset-inline: 0;
		inset-block-end: calc(84px + env(safe-area-inset-bottom, 0px));
		z-index: 60;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--space-2);
		pointer-events: none;
	}

	.toast {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		max-width: calc(100vw - 32px);
		padding: 10px 18px;
		border-radius: var(--radius-pill);
		background: var(--text);
		color: var(--bg);
		font-weight: 700;
		font-size: var(--text-sm);
		box-shadow: var(--shadow-md);
	}

	.success :global(svg) {
		color: var(--positive-soft);
	}

	.error {
		background: var(--danger);
		color: #fff;
	}
</style>

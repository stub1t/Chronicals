<script lang="ts">
	import type { Snippet } from 'svelte';
	import { fade, fly } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import X from '@lucide/svelte/icons/x';

	interface Props {
		open: boolean;
		title: string;
		closeLabel: string;
		onclose?: () => void;
		/** Accent colour for the header strip, e.g. the entry type's colour. */
		tint?: string | null;
		children: Snippet;
		footer?: Snippet;
	}

	let { open = $bindable(), title, closeLabel, onclose, tint = null, children, footer }: Props = $props();

	let sheet: HTMLDivElement | undefined = $state();
	let dragY = $state(0);
	let dragStart = $state<number | null>(null);
	const titleId = `sheet-${Math.random().toString(36).slice(2, 9)}`;

	function close() {
		open = false;
		onclose?.();
	}

	// Focus moves into the sheet when it opens and Escape closes it.
	$effect(() => {
		if (!open || !sheet) return;
		const previous = document.activeElement as HTMLElement | null;
		sheet.focus();
		const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close();
		window.addEventListener('keydown', onKey);
		return () => {
			window.removeEventListener('keydown', onKey);
			previous?.focus?.();
		};
	});

	// Drag the handle down to close.
	function onPointerDown(e: PointerEvent) {
		dragStart = e.clientY;
		(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
	}
	function onPointerMove(e: PointerEvent) {
		if (dragStart !== null) dragY = Math.max(0, e.clientY - dragStart);
	}
	function onPointerUp() {
		if (dragY > 110) close();
		dragY = 0;
		dragStart = null;
	}
</script>

{#if open}
	<div class="backdrop" transition:fade={{ duration: 200 }} onclick={close} aria-hidden="true"></div>
	<div
		bind:this={sheet}
		class="sheet"
		role="dialog"
		aria-modal="true"
		aria-labelledby={titleId}
		tabindex="-1"
		style="transform: translateY({dragY}px); {tint ? `--tint: ${tint};` : ''}"
		class:dragging={dragStart !== null}
		transition:fly={{ y: 500, duration: 360, easing: cubicOut, opacity: 1 }}
	>
		<div
			class="handle-area"
			onpointerdown={onPointerDown}
			onpointermove={onPointerMove}
			onpointerup={onPointerUp}
			onpointercancel={onPointerUp}
			aria-hidden="true"
		>
			<span class="handle"></span>
		</div>
		<header class="header">
			<h2 id={titleId} class="title">{title}</h2>
			<button type="button" class="close" aria-label={closeLabel} onclick={close}><X size={20} /></button>
		</header>
		<div class="body">
			{@render children()}
		</div>
		{#if footer}
			<footer class="footer">{@render footer()}</footer>
		{/if}
	</div>
{/if}

<style>
	.backdrop {
		position: fixed;
		inset: 0;
		background: rgb(30 20 15 / 0.4);
		z-index: 50;
	}

	.sheet {
		--tint: var(--accent);
		position: fixed;
		inset-inline: 0;
		inset-block-end: 0;
		z-index: 51;
		max-height: 92dvh;
		max-width: 640px;
		margin: 0 auto;
		display: flex;
		flex-direction: column;
		background: var(--bg);
		border-radius: var(--radius-lg) var(--radius-lg) 0 0;
		box-shadow: var(--shadow-lg);
		outline: none;
		transition: transform 0.35s var(--ease-spring);
	}

	.sheet.dragging {
		transition: none;
	}

	.handle-area {
		display: flex;
		justify-content: center;
		padding: 10px 0 4px;
		cursor: grab;
		touch-action: none;
	}

	.handle {
		width: 42px;
		height: 5px;
		border-radius: var(--radius-pill);
		background: color-mix(in srgb, var(--tint) 45%, var(--border));
	}

	.header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-5) var(--space-3);
	}

	.title {
		margin: 0;
		font-size: var(--text-xl);
		font-weight: 800;
	}

	.close {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		border: none;
		border-radius: 50%;
		background: var(--bg-subtle);
		color: var(--text-muted);
		cursor: pointer;
	}

	.body {
		flex: 1;
		overflow-y: auto;
		overscroll-behavior: contain;
		padding: 0 var(--space-5) var(--space-5);
	}

	.footer {
		padding: var(--space-3) var(--space-5) calc(var(--space-4) + env(safe-area-inset-bottom, 0px));
		border-top: 1px solid var(--border);
		background: var(--bg);
	}
</style>

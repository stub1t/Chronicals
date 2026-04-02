<script lang="ts">
	import '../app.css';
	import { page } from '$app/state';
	import { browser } from '$app/environment';
	import { onMount } from 'svelte';
	import { themeStore } from '$lib/stores/theme.js';
	import { flush, pendingCount } from '$lib/utils/offline-queue.js';

	let { children } = $props();
	let syncPending = $state(0);
	let isOnline = $state(true);

	onMount(() => {
		themeStore.init();

		if (!browser) return;

		isOnline = navigator.onLine;

		// Flush queue on reconnect
		window.addEventListener('online', async () => {
			isOnline = true;
			await flush();
			syncPending = await pendingCount();
		});

		window.addEventListener('offline', () => {
			isOnline = false;
		});

		// Flush any queued mutations from previous sessions
		flush().then(() => pendingCount().then((n) => (syncPending = n)));
	});

	function isActive(href: string): boolean {
		return page.url.pathname.startsWith(href);
	}
</script>

<div class="app-shell">
	{#if !isOnline}
		<div class="offline-banner">Offline — changes will sync when reconnected</div>
	{/if}

	{#if syncPending > 0}
		<div class="sync-banner">{syncPending} change{syncPending > 1 ? 's' : ''} pending sync</div>
	{/if}

	<main class="content">
		{@render children()}
	</main>

	<nav class="bottom-nav" aria-label="Main navigation">
		<a href="/calendar" class="nav-item" class:active={isActive('/calendar')} aria-label="Calendar">
			<svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
				<rect x="3" y="4" width="18" height="18" rx="3"/>
				<path d="M16 2v4M8 2v4M3 10h18"/>
			</svg>
			<span class="nav-label">Calendar</span>
		</a>
		<a href="/analysis" class="nav-item" class:active={isActive('/analysis')} aria-label="Analyse">
			<svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
				<path d="M3 18l4-8 4 5 3-3 4 6"/>
			</svg>
			<span class="nav-label">Analyse</span>
		</a>
		<a href="/settings" class="nav-item" class:active={isActive('/settings')} aria-label="Settings">
			<svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
				<circle cx="12" cy="12" r="3"/>
				<path d="M12 2v2m0 16v2M4.22 4.22l1.42 1.42m12.72 12.72 1.42 1.42M2 12h2m16 0h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
			</svg>
			<span class="nav-label">Settings</span>
		</a>
	</nav>
</div>

<style>
	.app-shell {
		display: flex;
		flex-direction: column;
		height: 100dvh;
		background: var(--bg);
	}

	.offline-banner {
		background: #f97316;
		color: #fff;
		text-align: center;
		font-size: 0.75rem;
		padding: 0.3rem;
		font-weight: 500;
	}

	.sync-banner {
		background: var(--bg-subtle);
		color: var(--text-muted);
		text-align: center;
		font-size: 0.7rem;
		padding: 0.2rem;
	}

	.content {
		flex: 1;
		overflow-y: auto;
		overflow-x: hidden;
	}

	.bottom-nav {
		display: flex;
		background: var(--bg-card);
		border-top: 1px solid var(--border);
		padding-bottom: env(safe-area-inset-bottom, 0);
	}

	.nav-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 0.5rem 0;
		gap: 3px;
		text-decoration: none;
		color: var(--text-subtle);
		transition: color 0.15s;
	}

	.nav-item.active {
		color: var(--text);
	}

	.nav-icon {
		width: 22px;
		height: 22px;
	}

	.nav-label {
		font-size: 0.65rem;
		font-weight: 500;
	}
</style>

<script lang="ts">
	import '../app.css';
	import { page } from '$app/state';
	import { browser } from '$app/environment';
	import { onMount } from 'svelte';
	import { themeStore } from '$lib/stores/theme.js';
	import { language, t } from '$lib/i18n';
	import { flush, pendingCount } from '$lib/utils/offline-queue.js';
	import { isAppLockEnabled } from '$lib/app-lock.js';

	let { children } = $props();
	let syncPending = $state(0);
	let isOnline = $state(true);
	// Start locked (no flash of content) when the Android app lock is on.
	let appLocked = $state(__NATIVE_APP__ && browser && isAppLockEnabled());
	let unlock: () => Promise<boolean> = async () => true;

	onMount(() => {
		themeStore.init();

		if (__NATIVE_APP__) {
			import('$lib/mobile/app-lock.js').then((m) => {
				unlock = m.unlock;
				m.startAppLock();
				m.locked.subscribe((value) => {
					appLocked = value;
					if (value) m.unlock();
				});
			});
			import('$lib/mobile/reminders.js').then((m) => {
				m.listenForReminderTaps();
				m.syncReminders().catch(console.error);
			});
		}

		// The Android app keeps its data on the device, so there is nothing to sync.
		if (!browser || __NATIVE_APP__) return;

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
		<div class="offline-banner">{t('app_offline')}</div>
	{/if}

	{#if syncPending > 0}
		<div class="sync-banner">
			{syncPending === 1 ? t('app_sync_pending_one') : t('app_sync_pending_many', { count: syncPending })}
		</div>
	{/if}

	<!-- Re-render on language change, including the lock screen. -->
	{#key $language}
		{#if appLocked}
			<div class="lock-screen">
				<svg class="lock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
					<rect x="4" y="10" width="16" height="11" rx="2.5" />
					<path d="M8 10V7a4 4 0 0 1 8 0v3" />
				</svg>
				<p class="lock-title">{t('app_lock_title')}</p>
				<button class="lock-button" onclick={() => unlock()}>{t('app_lock_unlock')}</button>
			</div>
		{:else}
			<main class="content">
				{@render children()}
			</main>

			<nav class="bottom-nav" aria-label={t('nav_settings')}>
				<a href="/calendar" class="nav-item" class:active={isActive('/calendar')} aria-label={t('nav_calendar')}>
					<svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
						<rect x="3" y="4" width="18" height="18" rx="3"/>
						<path d="M16 2v4M8 2v4M3 10h18"/>
					</svg>
					<span class="nav-label">{t('nav_calendar')}</span>
				</a>
				<a href="/analysis" class="nav-item" class:active={isActive('/analysis')} aria-label={t('nav_analysis')}>
					<svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
						<path d="M3 18l4-8 4 5 3-3 4 6"/>
					</svg>
					<span class="nav-label">{t('nav_analysis')}</span>
				</a>
				<a href="/settings" class="nav-item" class:active={isActive('/settings')} aria-label={t('nav_settings')}>
					<svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
						<circle cx="12" cy="12" r="3"/>
						<path d="M12 2v2m0 16v2M4.22 4.22l1.42 1.42m12.72 12.72 1.42 1.42M2 12h2m16 0h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
					</svg>
					<span class="nav-label">{t('nav_settings')}</span>
				</a>
			</nav>
		{/if}
	{/key}
</div>

<style>
	.app-shell {
		display: flex;
		flex-direction: column;
		height: 100dvh;
		/* viewport-fit=cover: keep content clear of the status bar (Android app, iOS standalone) */
		padding-top: env(safe-area-inset-top, 0);
		background: var(--bg);
	}

	.lock-screen {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 16px;
		color: var(--text);
	}

	.lock-icon {
		width: 48px;
		height: 48px;
		color: var(--text-muted);
	}

	.lock-title {
		font-size: 1.1rem;
		font-weight: 700;
		margin: 0;
	}

	.lock-button {
		padding: 12px 28px;
		border: none;
		border-radius: 22px;
		background: var(--text);
		color: var(--bg);
		font-family: inherit;
		font-size: 0.95rem;
		font-weight: 700;
		cursor: pointer;
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

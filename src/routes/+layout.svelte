<script lang="ts">
	import '../app.css';
	import { goto } from '$app/navigation';
	import { needsOnboarding } from '$lib/onboarding.js';
	import { page } from '$app/state';
	import { browser } from '$app/environment';
	import { onMount } from 'svelte';
	import { themeStore } from '$lib/stores/theme.js';
	import { language, t, timeFormat } from '$lib/i18n';
	import { flush, pendingCount } from '$lib/utils/offline-queue.js';
	import { isAppLockEnabled } from '$lib/app-lock.js';
	import Toaster from '$lib/ui/Toaster.svelte';
	import House from '@lucide/svelte/icons/house';
	import Utensils from '@lucide/svelte/icons/utensils';
	import CalendarDays from '@lucide/svelte/icons/calendar-days';
	import ChartColumn from '@lucide/svelte/icons/chart-no-axes-column';
	import CircleUser from '@lucide/svelte/icons/circle-user-round';
	import Lock from '@lucide/svelte/icons/lock';

	let { children } = $props();
	let syncPending = $state(0);
	let isOnline = $state(true);
	// Start locked (no flash of content) when the Android app lock is on.
	let appLocked = $state(__NATIVE_APP__ && browser && isAppLockEnabled());
	let unlock: () => Promise<boolean> = async () => true;

	onMount(() => {
		themeStore.init();

		// A fresh install starts with the introduction.
		if (page.url.pathname !== '/welcome') {
			needsOnboarding()
				.then((needed) => {
					if (needed) goto('/welcome', { replaceState: true });
				})
				.catch(() => {});
		}

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
			// With automatic location on: today's place for the weather, on start and on each return to the app.
			import('$lib/mobile/location.js').then((m) => {
				m.updateTodaysPlace().catch(console.error);
				import('@capacitor/app').then(({ App }) =>
					App.addListener('appStateChange', ({ isActive }) => {
						if (isActive) m.updateTodaysPlace().catch(console.error);
					})
				);
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

	const path = () => page.url.pathname;
	const tabs = [
		{ href: '/', icon: House, label: () => t('nav_today'), active: () => path() === '/' },
		{ href: '/meals', icon: Utensils, label: () => t('nav_meals'), active: () => path().startsWith('/meals') },
		{
			href: '/calendar',
			icon: CalendarDays,
			label: () => t('nav_calendar'),
			active: () => path().startsWith('/calendar') || path().startsWith('/search')
		},
		{ href: '/insights', icon: ChartColumn, label: () => t('nav_insights'), active: () => path().startsWith('/insights') },
		{ href: '/me', icon: CircleUser, label: () => t('nav_me'), active: () => path() === '/me' || path().startsWith('/me/') }
	];
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

	<!-- Re-render on language or time format change, including the lock screen. -->
	{#key `${$language}-${$timeFormat}`}
		{#if appLocked}
			<div class="lock-screen">
				<span class="lock-icon"><Lock size={34} /></span>
				<p class="lock-title">{t('app_lock_title')}</p>
				<button class="lock-button" onclick={() => unlock()}>{t('app_lock_unlock')}</button>
			</div>
		{:else}
			<main class="content">
				{@render children()}
			</main>

			{#if path() !== '/welcome'}
			<nav class="bottom-nav" aria-label={t('nav_main')}>
				{#each tabs as tab (tab.href)}
					<a href={tab.href} class="nav-item" class:active={tab.active()} aria-current={tab.active() ? 'page' : undefined}>
						<span class="nav-pill"><tab.icon size={22} strokeWidth={tab.active() ? 2.4 : 2} /></span>
						<span class="nav-label">{tab.label()}</span>
					</a>
				{/each}
			</nav>
			{/if}
		{/if}
	{/key}
</div>

<Toaster />

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
		display: grid;
		place-items: center;
		width: 88px;
		height: 88px;
		border-radius: 50%;
		background: var(--accent-soft);
		color: var(--accent);
	}

	.lock-title {
		font-size: var(--text-xl);
		font-weight: 800;
		margin: 0;
	}

	.lock-button {
		min-height: 52px;
		padding: 0 var(--space-8);
		border: none;
		border-radius: var(--radius-pill);
		background: var(--accent);
		color: var(--on-accent);
		font-size: var(--text-md);
		font-weight: 800;
		cursor: pointer;
		box-shadow: 0 4px 14px color-mix(in srgb, var(--accent) 30%, transparent);
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
		padding: var(--space-2) var(--space-2) calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
		background: color-mix(in srgb, var(--bg-card) 92%, transparent);
		border-top: 1px solid var(--border);
		box-shadow: 0 -4px 20px rgb(70 45 20 / 0.04);
	}

	.nav-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		padding: 2px 0;
		text-decoration: none;
		color: var(--text-subtle);
		-webkit-tap-highlight-color: transparent;
	}

	.nav-pill {
		display: grid;
		place-items: center;
		width: 58px;
		height: 32px;
		border-radius: var(--radius-pill);
		transition:
			background-color 0.25s,
			transform 0.35s var(--ease-spring);
	}

	.nav-item:active .nav-pill {
		transform: scale(0.88);
	}

	.nav-item.active {
		color: var(--text);
	}

	.nav-item.active .nav-pill {
		background: var(--accent-soft);
		color: var(--accent);
	}

	.nav-label {
		font-size: var(--text-xs);
		font-weight: 700;
	}
</style>

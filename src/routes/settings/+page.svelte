<script lang="ts">
	import { themeStore } from '$lib/stores/theme.js';
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';

	type ThemeOption = 'system' | 'light' | 'dark';

	const themeOptions: { value: ThemeOption; label: string }[] = [
		{ value: 'system', label: 'System' },
		{ value: 'light', label: 'Light' },
		{ value: 'dark', label: 'Dark' }
	];

	let currentTheme = $state<ThemeOption>('system');

	onMount(() => {
		currentTheme = themeStore.current;
	});

	function setTheme(t: ThemeOption) {
		currentTheme = t;
		themeStore.set(t);
	}
</script>

<div class="page">
	<header class="page-header">
		<h1 class="page-title">Settings</h1>
	</header>

	<!-- Nav cards -->
	<nav class="nav-list" aria-label="Settings sections">
		<a href="/settings/profile" class="nav-card">
			<span class="nav-icon" aria-hidden="true">
				<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
					<circle cx="10" cy="7" r="3"/>
					<path d="M3 18c0-3.87 3.13-7 7-7s7 3.13 7 7"/>
				</svg>
			</span>
			<div class="nav-text">
				<span class="nav-label">Profile</span>
				<span class="nav-desc">Name, blood type, location</span>
			</div>
			<span class="nav-arrow" aria-hidden="true">›</span>
		</a>
		<a href="/settings/medications" class="nav-card">
			<span class="nav-icon" aria-hidden="true">
				<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
					<rect x="4" y="2" width="12" height="16" rx="2"/>
					<path d="M8 7h4M8 11h4M8 15h2"/>
				</svg>
			</span>
			<div class="nav-text">
				<span class="nav-label">Medications</span>
				<span class="nav-desc">Active meds and change history</span>
			</div>
			<span class="nav-arrow" aria-hidden="true">›</span>
		</a>
		<a href="/settings/entry-types" class="nav-card">
			<span class="nav-icon" aria-hidden="true">
				<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
					<circle cx="10" cy="10" r="3"/>
					<circle cx="10" cy="10" r="8"/>
				</svg>
			</span>
			<div class="nav-text">
				<span class="nav-label">Entry Types</span>
				<span class="nav-desc">Manage symptom and event types</span>
			</div>
			<span class="nav-arrow" aria-hidden="true">›</span>
		</a>
		<a href="/settings/triggers" class="nav-card">
			<span class="nav-icon" aria-hidden="true">
				<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
					<path d="M3 6h14M5 10h10M7 14h6"/>
				</svg>
			</span>
			<div class="nav-text">
				<span class="nav-label">Triggers</span>
				<span class="nav-desc">Manage trigger library</span>
			</div>
			<span class="nav-arrow" aria-hidden="true">›</span>
		</a>
	</nav>

	<!-- Theme toggle -->
	<section class="theme-section" aria-label="Theme">
		<p class="theme-title">Theme</p>
		<div class="theme-pills" role="group" aria-label="Choose theme">
			{#each themeOptions as opt (opt.value)}
				<button
					class="theme-pill"
					class:active={currentTheme === opt.value}
					onclick={() => setTheme(opt.value)}
					aria-pressed={currentTheme === opt.value}
				>
					{opt.label}
				</button>
			{/each}
		</div>
	</section>
</div>

<style>
	.page {
		max-width: 560px;
		margin: 0 auto;
		padding: 24px 16px 80px;
		display: flex;
		flex-direction: column;
		gap: 24px;
	}

	.page-header {
		margin-bottom: 4px;
	}

	.page-title {
		font-size: 1.6rem;
		font-weight: 800;
		color: var(--text);
		margin: 0;
	}

	/* Nav cards */
	.nav-list {
		display: flex;
		flex-direction: column;
		gap: 1px;
		background: var(--border);
		border: 1px solid var(--border);
		border-radius: 16px;
		overflow: hidden;
	}

	.nav-card {
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 16px;
		background: var(--bg-card);
		text-decoration: none;
		color: var(--text);
		transition: background 0.15s;
	}

	.nav-card:hover {
		background: var(--bg-subtle);
	}

	.nav-card:first-child {
		border-radius: 15px 15px 0 0;
	}

	.nav-card:last-child {
		border-radius: 0 0 15px 15px;
	}

	.nav-icon {
		width: 36px;
		height: 36px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--bg-subtle);
		border-radius: 10px;
		flex-shrink: 0;
		color: var(--text-muted);
	}

	.nav-icon svg {
		width: 18px;
		height: 18px;
	}

	.nav-text {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.nav-label {
		font-size: 0.95rem;
		font-weight: 600;
	}

	.nav-desc {
		font-size: 0.78rem;
		color: var(--text-muted);
	}

	.nav-arrow {
		font-size: 1.3rem;
		color: var(--text-subtle);
	}

	/* Theme toggle */
	.theme-section {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.theme-title {
		font-size: 0.82rem;
		font-weight: 700;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.05em;
		margin: 0;
	}

	.theme-pills {
		display: flex;
		gap: 8px;
	}

	.theme-pill {
		flex: 1;
		padding: 10px 0;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 20px;
		font-family: inherit;
		font-size: 0.88rem;
		font-weight: 500;
		color: var(--text-muted);
		cursor: pointer;
		transition: all 0.15s;
	}

	.theme-pill:hover {
		border-color: var(--text-muted);
		color: var(--text);
	}

	.theme-pill.active {
		background: var(--text);
		color: var(--bg);
		border-color: var(--text);
		font-weight: 700;
	}
</style>

<script lang="ts">
	/** Title of the shown period with previous/next, Today, Month/Week and search. */
	import ChevronLeft from '@lucide/svelte/icons/chevron-left';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import Search from '@lucide/svelte/icons/search';
	import { t } from '$lib/i18n';

	interface Props {
		title: string;
		view: 'month' | 'week';
		onprev: () => void;
		onnext: () => void;
		ontoday: () => void;
		showToday: boolean;
		prevLabel: string;
		nextLabel: string;
	}

	let { title, view, onprev, onnext, ontoday, showToday, prevLabel, nextLabel }: Props = $props();
</script>

<header class="header">
	<div class="top">
		<nav class="views" aria-label={t('calendar_views')}>
			<a href="/calendar" class:active={view === 'month'} aria-current={view === 'month' ? 'page' : undefined}>{t('month_view')}</a>
			<a href="/calendar/week" class:active={view === 'week'} aria-current={view === 'week' ? 'page' : undefined}>{t('week_view')}</a>
		</nav>
		<a href="/search" class="icon-button" aria-label={t('search_open')}><Search size={20} /></a>
	</div>
	<div class="period">
		<button type="button" class="icon-button" aria-label={prevLabel} onclick={onprev}><ChevronLeft size={22} class="flip-rtl" /></button>
		<h1 class="title">{title}</h1>
		<button type="button" class="icon-button" aria-label={nextLabel} onclick={onnext}><ChevronRight size={22} class="flip-rtl" /></button>
	</div>
	{#if showToday}
		<button type="button" class="today" onclick={ontoday}>{t('calendar_back_to_today')}</button>
	{/if}
</header>

<style>
	.header {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--space-3);
	}

	.top {
		width: 100%;
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.views {
		display: flex;
		padding: 4px;
		gap: 4px;
		background: var(--bg-subtle);
		border-radius: var(--radius-pill);
	}

	.views a {
		min-height: 34px;
		display: grid;
		place-items: center;
		padding: 0 var(--space-4);
		border-radius: var(--radius-pill);
		color: var(--text-muted);
		text-decoration: none;
		font-size: var(--text-sm);
		font-weight: 700;
	}

	.views a.active {
		background: var(--bg-card);
		color: var(--text);
		box-shadow: var(--shadow-sm);
	}

	.period {
		width: 100%;
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.title {
		margin: 0;
		font-size: var(--text-xl);
		font-weight: 800;
		text-align: center;
	}

	.icon-button {
		display: grid;
		place-items: center;
		width: 44px;
		height: 44px;
		border: none;
		border-radius: 50%;
		background: var(--bg-card);
		color: var(--text);
		box-shadow: var(--shadow-sm);
		cursor: pointer;
	}

	.today {
		min-height: 32px;
		padding: 0 var(--space-4);
		border: none;
		border-radius: var(--radius-pill);
		background: var(--accent-soft);
		color: var(--accent);
		font-weight: 800;
		font-size: var(--text-sm);
		cursor: pointer;
	}
</style>

<script lang="ts">
	import { formatMonthLabel, toYearMonth } from '$lib/utils/date';

	interface Props {
		yearMonth: string;
		onprev?: () => void;
		onnext?: () => void;
		ontoday?: () => void;
	}

	let { yearMonth, onprev, onnext, ontoday }: Props = $props();

	let label = $derived(formatMonthLabel(yearMonth));
	let isCurrentMonth = $derived(yearMonth === toYearMonth(new Date()));
</script>

<nav class="month-nav" aria-label="Month navigation">
	<button class="nav-btn" onclick={onprev} aria-label="Previous month">
		<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
			<path d="M10 3L5 8l5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
		</svg>
	</button>

	<div class="center">
		<span class="month-label">{label}</span>
		{#if !isCurrentMonth}
			<button class="today-btn" onclick={ontoday}>Today</button>
		{/if}
	</div>

	<button class="nav-btn" onclick={onnext} aria-label="Next month">
		<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
			<path d="M6 3l5 5-5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
		</svg>
	</button>
</nav>

<style>
	.month-nav {
		display: flex;
		align-items: center;
		justify-content: space-between;
		width: 100%;
		padding: 4px 0;
		gap: 8px;
	}

	.center {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		flex: 1;
	}

	.month-label {
		font-size: 1rem;
		font-weight: 600;
		color: var(--text);
		letter-spacing: 0.01em;
		white-space: nowrap;
	}

	.nav-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 36px;
		height: 36px;
		border-radius: 8px;
		background: transparent;
		border: none;
		color: var(--text);
		cursor: pointer;
		transition: background 0.15s;
		flex-shrink: 0;
	}

	.nav-btn:hover {
		background: var(--bg-subtle);
	}

	.nav-btn:active {
		background: var(--border);
	}

	.today-btn {
		font-size: 0.72rem;
		font-weight: 500;
		color: var(--text-muted);
		background: transparent;
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 1px 8px;
		cursor: pointer;
		font-family: inherit;
		transition: background 0.15s, color 0.15s;
		line-height: 1.6;
	}

	.today-btn:hover {
		background: var(--bg-subtle);
		color: var(--text);
	}
</style>

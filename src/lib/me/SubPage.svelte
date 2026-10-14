<script lang="ts">
	/** A screen inside Me: back link, title, an optional action and the content. */
	import type { Snippet } from 'svelte';
	import ArrowLeft from '@lucide/svelte/icons/arrow-left';
	import { t } from '$lib/i18n';

	interface Props {
		title: string;
		action?: Snippet;
		children: Snippet;
	}

	let { title, action, children }: Props = $props();
</script>

<div class="page">
	<header class="header">
		<a class="back" href="/me">
			<ArrowLeft size={18} class="flip-rtl" />
			{t('nav_me')}
		</a>
		<div class="title-row">
			<h1 class="title">{title}</h1>
			{@render action?.()}
		</div>
	</header>
	{@render children()}
</div>

<style>
	.page {
		max-width: 640px;
		margin: 0 auto;
		padding: var(--space-5) var(--space-4) var(--space-8);
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.header {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.back {
		align-self: flex-start;
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: 44px;
		color: var(--text-muted);
		font-weight: 700;
		text-decoration: none;
	}

	.title-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
	}

	.title {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 800;
	}
</style>

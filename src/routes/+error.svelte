<script lang="ts">
	import { page } from '$app/state';
	import { t } from '$lib/stores/locale.js';
</script>

<div class="error-page">
	<div class="error-card">
		<span class="error-code">{page.status}</span>
		<h1 class="error-title">
			{#if page.status === 404}
				{t('error_not_found')}
			{:else if page.status === 500}
				{t('error_server')}
			{:else}
				{t('error_generic')}
			{/if}
		</h1>
		<p class="error-message">{page.error?.message ?? t('error_fallback')}</p>
		<a href="/calendar" class="error-home">{t('error_go_calendar')}</a>
	</div>
</div>

<style>
	.error-page {
		display: flex;
		align-items: center;
		justify-content: center;
		min-height: 100dvh;
		padding: 24px;
		background: var(--bg);
	}

	.error-card {
		text-align: center;
		max-width: 320px;
	}

	.error-code {
		display: block;
		font-size: 4rem;
		font-weight: 800;
		color: var(--border);
		line-height: 1;
		margin-bottom: 16px;
	}

	.error-title {
		font-size: 1.4rem;
		font-weight: 700;
		color: var(--text);
		margin: 0 0 8px;
	}

	.error-message {
		font-size: 0.9rem;
		color: var(--text-muted);
		margin: 0 0 24px;
	}

	.error-home {
		display: inline-block;
		background: var(--text);
		color: var(--bg);
		padding: 10px 24px;
		border-radius: 20px;
		font-size: 0.9rem;
		font-weight: 600;
		text-decoration: none;
	}
</style>

<script lang="ts">
	import type { EntryType, EntryCategory } from '$lib/types';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';

	interface Props {
		entryTypes: EntryType[];
		onselect?: (entryType: EntryType) => void;
		onclose?: () => void;
	}

	let { entryTypes, onselect, onclose }: Props = $props();

	const CATEGORY_LABELS: Record<EntryCategory, string> = {
		symptom: t('category_symptom'),
		event: t('category_event'),
		note: t('category_note')
	};

	// Group by category for display order
	const CATEGORY_ORDER: EntryCategory[] = ['symptom', 'event', 'note'];

	let grouped = $derived.by(() => {
		const map = new Map<EntryCategory, EntryType[]>();
		for (const cat of CATEGORY_ORDER) map.set(cat, []);
		for (const et of entryTypes) {
			const list = map.get(et.category as EntryCategory);
			if (list) list.push(et);
		}
		return map;
	});

	function handleOverlayClick(e: MouseEvent) {
		if ((e.target as HTMLElement).classList.contains('overlay')) {
			onclose?.();
		}
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') onclose?.();
	}
</script>

<div
	class="overlay"
	role="dialog"
	tabindex="-1"
	aria-modal="true"
	aria-label={t('entry_type_choose')}
	onclick={handleOverlayClick}
	onkeydown={handleKeydown}
>
	<div class="sheet">
		<div class="sheet-handle" aria-hidden="true"></div>

		<div class="sheet-header">
			<h2 class="sheet-title">{t('entry_type_what_happened')}</h2>
			<button class="close-btn" onclick={onclose} aria-label={t('close')}>
				<svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
					<path d="M4 4l10 10M14 4L4 14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
				</svg>
			</button>
		</div>

		<div class="sheet-body">
			{#each CATEGORY_ORDER as category}
				{@const types = grouped.get(category) ?? []}
				{#if types.length > 0}
					<div class="category-section">
						<h3 class="category-heading">{CATEGORY_LABELS[category]}</h3>
						<div class="type-grid">
							{#each types as et (et.id)}
								<button
									class="type-card"
									style={et.color ? `--accent: ${et.color}` : '--accent: var(--text-muted)'}
									onclick={() => onselect?.(et)}
								>
									<EntryTypeIcon entryType={et} size={22} />
									<span class="type-name">{localizeBuiltInText(et.name)}</span>
									<span class="type-category">{CATEGORY_LABELS[et.category as EntryCategory]}</span>
								</button>
							{/each}
						</div>
					</div>
				{/if}
			{/each}
		</div>
	</div>
</div>

<style>
	.overlay {
		position: fixed;
		inset: 0;
		background: rgba(0, 0, 0, 0.5);
		backdrop-filter: blur(2px);
		-webkit-backdrop-filter: blur(2px);
		display: flex;
		align-items: flex-end;
		justify-content: center;
		z-index: 100;
		padding: 0;
	}

	.sheet {
		background: var(--bg-card);
		border-radius: 20px 20px 0 0;
		width: 100%;
		max-width: 600px;
		max-height: 85dvh;
		display: flex;
		flex-direction: column;
		overflow: hidden;
		box-shadow: 0 -4px 32px rgba(0, 0, 0, 0.18);
	}

	.sheet-handle {
		width: 40px;
		height: 4px;
		background: var(--border);
		border-radius: 2px;
		margin: 12px auto 0;
		flex-shrink: 0;
	}

	.sheet-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 12px 20px 8px;
		flex-shrink: 0;
	}

	.sheet-title {
		font-size: 1.1rem;
		font-weight: 700;
		color: var(--text);
		margin: 0;
	}

	.close-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 32px;
		height: 32px;
		border-radius: 50%;
		background: var(--bg-subtle);
		border: none;
		color: var(--text-muted);
		cursor: pointer;
		transition: background 0.15s;
	}

	.close-btn:hover {
		background: var(--border);
	}

	.sheet-body {
		overflow-y: auto;
		padding: 8px 16px 32px;
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	.category-section {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.category-heading {
		font-size: 0.72rem;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--text-subtle);
		margin: 0;
		padding: 0 4px;
	}

	.type-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
	}

	.type-card {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 4px;
		padding: 12px 14px;
		background: var(--bg);
		border: 1px solid var(--border);
		border-left: 3px solid var(--accent);
		border-radius: 10px;
		cursor: pointer;
		font-family: inherit;
		text-align: left;
		transition: background 0.15s, transform 0.1s;
	}

	.type-card:hover {
		background: var(--bg-subtle);
	}

	.type-card:active {
		transform: scale(0.97);
	}

	.type-name {
		font-size: 0.875rem;
		font-weight: 600;
		color: var(--text);
		line-height: 1.2;
	}

	.type-category {
		font-size: 0.68rem;
		color: var(--text-subtle);
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}
</style>

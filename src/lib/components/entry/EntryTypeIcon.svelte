<script lang="ts">
	import type { EntryType } from '$lib/types';

	interface Props {
		entryType?: Pick<EntryType, 'id' | 'icon' | 'color'> | null;
		size?: number;
		class?: string;
	}

	let { entryType = null, size = 18, class: className = '' }: Props = $props();

	const stroke = $derived(entryType?.color ?? 'currentColor');

	function isBuiltIn(id: string | undefined, expected: string): boolean {
		return id === expected;
	}
</script>

<span
	class={`entry-type-icon ${className}`.trim()}
	style={`--icon-size: ${size}px; --icon-stroke: ${stroke};`}
	aria-hidden="true"
>
	{#if isBuiltIn(entryType?.id, 'builtin-headache')}
		<svg viewBox="0 0 20 20" fill="none">
			<path d="M10 3.8c-2.8 0-5 2.2-5 4.9 0 1.2.4 2.3 1.2 3.2.7.9 1.2 1.8 1.3 2.9h5c.1-1.1.5-2 1.3-2.9.8-.9 1.2-2 1.2-3.2 0-2.7-2.2-4.9-5-4.9Z" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linejoin="round" />
			<path d="M8 16h4" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linecap="round" />
			<path d="m6.6 8.4 1.7-.2-.8 1.7 1.8-.2" stroke="var(--icon-stroke)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
		</svg>
	{:else if isBuiltIn(entryType?.id, 'builtin-stomachache')}
		<svg viewBox="0 0 20 20" fill="none">
			<path d="M7.2 4.3c0 1.6.5 2.8 1.6 3.7 1.2.9 2.2 2 2.2 3.8 0 2-1.3 3.5-3.2 3.5-1.8 0-3.1-1.4-3.1-3.3 0-1.7.7-2.9 1.8-4 .8-.8 1.2-1.8 1.2-3.7h-.5Z" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
			<path d="M11.4 5.4c1.8.6 3 2 3 4.1" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linecap="round" />
			<path d="m7.2 10.8 1 .9 1.2-1.4" stroke="var(--icon-stroke)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
		</svg>
	{:else if isBuiltIn(entryType?.id, 'builtin-allergy')}
		<svg viewBox="0 0 20 20" fill="none">
			<circle cx="10" cy="10" r="1.7" fill="var(--icon-stroke)" />
			<path d="M10 3.8v2.1M10 14.1v2.1M3.8 10h2.1M14.1 10h2.1M5.6 5.6l1.5 1.5M12.9 12.9l1.5 1.5M14.4 5.6l-1.5 1.5M7.1 12.9l-1.5 1.5" stroke="var(--icon-stroke)" stroke-width="1.6" stroke-linecap="round" />
		</svg>
	{:else if isBuiltIn(entryType?.id, 'builtin-period')}
		<svg viewBox="0 0 20 20" fill="none">
			<path d="M10 3.8c2.4 3.1 4.2 5.4 4.2 7.7A4.2 4.2 0 0 1 10 15.7a4.2 4.2 0 0 1-4.2-4.2c0-2.3 1.8-4.6 4.2-7.7Z" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linejoin="round" />
		</svg>
	{:else if isBuiltIn(entryType?.id, 'builtin-hypo')}
		<svg viewBox="0 0 20 20" fill="none">
			<path d="M10 4.2c2.2 2.7 4 4.9 4 7.1a4 4 0 1 1-8 0c0-2.2 1.8-4.4 4-7.1Z" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linejoin="round" />
			<path d="M10 7.7v4.6M8.2 10.5 10 12.3l1.8-1.8" stroke="var(--icon-stroke)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
		</svg>
	{:else if isBuiltIn(entryType?.id, 'builtin-medication-change')}
		<svg viewBox="0 0 20 20" fill="none">
			<path d="M6.4 7.2a2.5 2.5 0 0 1 3.6 0l2.8 2.8a2.5 2.5 0 0 1 0 3.6 2.5 2.5 0 0 1-3.6 0L6.4 10.8a2.5 2.5 0 0 1 0-3.6Z" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linejoin="round" />
			<path d="m7.5 8.3 4.2 4.2" stroke="var(--icon-stroke)" stroke-width="1.6" stroke-linecap="round" />
			<path d="M14.8 6.2h-4M12.3 3.7l-2.5 2.5 2.5 2.5" stroke="var(--icon-stroke)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
		</svg>
	{:else if isBuiltIn(entryType?.id, 'builtin-note')}
		<svg viewBox="0 0 20 20" fill="none">
			<path d="M6 3.8h6l3 3v9.4H6z" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linejoin="round" />
			<path d="M12 3.8v3h3M8 10h5M8 12.8h5" stroke="var(--icon-stroke)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
		</svg>
	{:else}
		<span class="fallback">{entryType?.icon ?? '•'}</span>
	{/if}
</span>

<style>
	.entry-type-icon {
		width: var(--icon-size);
		height: var(--icon-size);
		display: inline-flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		color: var(--icon-stroke);
	}

	svg {
		width: 100%;
		height: 100%;
		display: block;
	}

	.fallback {
		font-size: calc(var(--icon-size) * 0.78);
		font-weight: 700;
		line-height: 1;
	}
</style>

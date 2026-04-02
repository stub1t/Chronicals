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
			<circle cx="10" cy="10" r="6" stroke="var(--icon-stroke)" stroke-width="1.8" />
			<path d="M8 5.5 6.8 8.2h2l-1.1 3.4 3.5-4.6H9.3L10.6 5" stroke="var(--icon-stroke)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
		</svg>
	{:else if isBuiltIn(entryType?.id, 'builtin-stomachache')}
		<svg viewBox="0 0 20 20" fill="none">
			<path d="M7 4.5c0 1.9.8 2.8 2.2 3.6 1.8 1 2.8 2 2.8 4.1 0 2.1-1.4 3.8-3.5 3.8S5 14.3 5 12.2c0-1.7.7-3 2-4" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
			<path d="M11.8 5.2c.9.5 3.2 1.6 3.2 4.7" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linecap="round" />
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
			<path d="M7 6.2a2.8 2.8 0 0 1 4 0l1.8 1.8a2.8 2.8 0 0 1 0 4l-1 1a2.8 2.8 0 0 1-4 0L6 11.2a2.8 2.8 0 0 1 0-4l1-1Z" stroke="var(--icon-stroke)" stroke-width="1.8" stroke-linejoin="round" />
			<path d="m8 7.2 4.8 4.8" stroke="var(--icon-stroke)" stroke-width="1.6" stroke-linecap="round" />
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

<script lang="ts">
	import type { EntryType } from '$lib/types';
	import CircleDot from '@lucide/svelte/icons/circle-dot';
	import { BUILT_IN_ICONS, ENTRY_ICONS } from '$lib/ui/entry-icons.js';
	import DuoIcon from '$lib/ui/icons/DuoIcon.svelte';

	interface Props {
		entryType?: Pick<EntryType, 'id' | 'icon' | 'color'> | null;
		size?: number;
		/** Draw the icon on a soft circle in the type's colour. */
		badge?: boolean;
		class?: string;
	}

	let { entryType = null, size = 18, badge = false, class: className = '' }: Props = $props();

	const color = $derived(entryType?.color ?? 'var(--text-muted)');
	const duo = $derived(entryType ? BUILT_IN_ICONS[entryType.id] : undefined);
	const Icon = $derived(
		(entryType?.icon?.startsWith('lucide:') ? ENTRY_ICONS[entryType.icon.slice(7)] : undefined) ?? (entryType?.icon ? null : CircleDot)
	);
</script>

<span
	class={`entry-type-icon ${className}`.trim()}
	class:badge
	style="--icon-size: {size}px; --icon-color: {color};"
	aria-hidden="true"
>
	{#if duo}
		<DuoIcon name={duo} size={badge ? Math.round(size * 0.58) : size} color="var(--icon-color)" />
	{:else if Icon}
		<Icon size={badge ? Math.round(size * 0.55) : size} strokeWidth={2.1} color="var(--icon-color)" />
	{:else}
		<span class="emoji">{entryType?.icon}</span>
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
		color: var(--icon-color);
	}

	.badge {
		border-radius: 50%;
		background: color-mix(in srgb, var(--icon-color) 16%, var(--bg-card));
	}

	.emoji {
		font-size: calc(var(--icon-size) * 0.62);
		line-height: 1;
	}
</style>

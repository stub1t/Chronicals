<script lang="ts">
	/** A built-in entry type icon in two tones of one colour. The markup comes from our own constants in duo.ts. */
	import { DUO_ICONS, type DuoIconName } from './duo.js';

	interface Props {
		name: DuoIconName;
		size?: number;
		color?: string;
	}

	let { name, size = 24, color = 'currentColor' }: Props = $props();
	const icon = $derived<{ fill: string; stroke: string; solid?: string }>(DUO_ICONS[name]);
</script>

<svg
	xmlns="http://www.w3.org/2000/svg"
	width={size}
	height={size}
	viewBox="0 0 24 24"
	fill="none"
	stroke={color}
	stroke-width="2"
	stroke-linecap="round"
	stroke-linejoin="round"
	aria-hidden="true"
>
	<g fill={color} fill-opacity="0.22" stroke="none">{@html icon.fill}</g>
	{@html icon.stroke}
	{#if icon.solid}<g fill={color} stroke="none">{@html icon.solid}</g>{/if}
</svg>

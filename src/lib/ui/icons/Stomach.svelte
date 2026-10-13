<script lang="ts">
	/** Stomach outline in the Lucide style (24 grid, round 2 px strokes); Lucide has none. */
	let { size = 24, strokeWidth = 2, color = 'currentColor' }: { size?: number; strokeWidth?: number; color?: string } = $props();
</script>

<svg
	xmlns="http://www.w3.org/2000/svg"
	width={size}
	height={size}
	viewBox="0 0 24 24"
	fill="none"
	stroke={color}
	stroke-width={strokeWidth}
	stroke-linecap="round"
	stroke-linejoin="round"
	aria-hidden="true"
>
	<path d="M8.5 2v2.5c0 1.7 1.3 3 3 3h1a6.5 6.5 0 0 1 6.5 6.5a7.5 7.5 0 0 1-7.5 7.5H10a4 4 0 0 1-4-4a3 3 0 0 1 3-3h.5a2.5 2.5 0 0 0 2.5-2.5" />
	<path d="M6 17.5H3.5" />
</svg>

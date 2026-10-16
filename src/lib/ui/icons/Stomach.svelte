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
	<path d="M8.5 3v3.8c0 1.4 1 2.2 2.4 2.2 4.4 0 7.7 2.9 7.7 6.7 0 3.4-2.9 5.8-6.8 5.8-3.3 0-6.1-1.5-6.1-4 0-1.8 1.3-2.8 3-2.8 1.6 0 2.4-.9 2.4-2.2" />
	<path d="M5.9 17.7 3 18.6" />
</svg>

<script lang="ts">
	import { intensityColor } from '$lib/utils/date';

	interface Props {
		color: string;
		intensity: number;
		entryTypeName?: string;
	}

	let { color, intensity, entryTypeName = '' }: Props = $props();

	let bgColor = $derived(intensityColor(color, intensity));
</script>

<span
	class="dot"
	style="background-color: {bgColor};"
	title={entryTypeName}
	aria-label={entryTypeName}
></span>

<style>
	.dot {
		display: inline-block;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		flex-shrink: 0;
	}
</style>

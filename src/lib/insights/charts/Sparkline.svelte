<script lang="ts">
	/** A line for values on a fixed scale (well-being 1 to 5); gaps where a value is missing. */
	interface Props {
		values: Array<number | null>;
		min: number;
		max: number;
		color: string;
		label: string;
	}

	let { values, min, max, color, label }: Props = $props();

	const W = 300;
	const H = 60;
	const x = (i: number) => (values.length > 1 ? (i / (values.length - 1)) * W : W / 2);
	const y = (v: number) => H - 4 - ((v - min) / (max - min)) * (H - 8);

	// Separate segments so a missing week leaves a gap instead of a misleading line.
	const segments = $derived.by(() => {
		const result: string[] = [];
		let current: string[] = [];
		values.forEach((v, i) => {
			if (v === null) {
				if (current.length) result.push(current.join(' '));
				current = [];
			} else current.push(`${x(i).toFixed(1)},${y(v).toFixed(1)}`);
		});
		if (current.length) result.push(current.join(' '));
		return result;
	});
</script>

<svg class="spark" viewBox="0 0 {W} {H}" preserveAspectRatio="none" role="img" aria-label={label}>
	<line x1="0" x2={W} y1={y((min + max) / 2)} y2={y((min + max) / 2)} class="mid" />
	{#each segments as points, i (i)}
		{#if points.includes(' ')}
			<polyline {points} fill="none" stroke={color} stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
		{:else}
			{@const [px, py] = points.split(',')}
			<circle cx={px} cy={py} r="2.5" fill={color} />
		{/if}
	{/each}
</svg>

<style>
	.spark {
		display: block;
		width: 100%;
		height: 60px;
	}

	:global([dir='rtl']) .spark {
		transform: scaleX(-1);
	}

	.mid {
		stroke: var(--border);
		stroke-dasharray: 4 4;
		vector-effect: non-scaling-stroke;
	}
</style>

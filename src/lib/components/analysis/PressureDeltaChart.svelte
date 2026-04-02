<script lang="ts">
	import { onMount } from 'svelte';

	interface DataPoint {
		date: string;
		delta: number | null;
		hasSymptom: boolean;
	}

	let { data, color = '#3b82f6' }: { data: DataPoint[]; color?: string } = $props();

	let canvas: HTMLCanvasElement;

	function draw() {
		if (!canvas || data.length === 0) return;
		const ctx = canvas.getContext('2d')!;
		const dpr = window.devicePixelRatio || 1;
		const { width, height } = canvas.getBoundingClientRect();
		canvas.width = width * dpr;
		canvas.height = height * dpr;
		ctx.scale(dpr, dpr);

		const padL = 36, padR = 8, padT = 8, padB = 24;
		const w = width - padL - padR;
		const h = height - padT - padB;

		const values = data.map((d) => d.delta ?? 0);
		const min = Math.min(...values, -5);
		const max = Math.max(...values, 5);
		const range = max - min || 1;

		const isDark = document.documentElement.classList.contains('dark');
		const gridColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';
		const textColor = isDark ? '#78716c' : '#a8a29e';
		const zeroColor = isDark ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.15)';

		// Grid lines
		ctx.strokeStyle = gridColor;
		ctx.lineWidth = 1;
		for (let i = 0; i <= 4; i++) {
			const y = padT + (h / 4) * i;
			ctx.beginPath(); ctx.moveTo(padL, y); ctx.lineTo(padL + w, y); ctx.stroke();
		}

		// Zero line
		const zeroY = padT + h - ((0 - min) / range) * h;
		ctx.strokeStyle = zeroColor;
		ctx.lineWidth = 1.5;
		ctx.setLineDash([4, 4]);
		ctx.beginPath(); ctx.moveTo(padL, zeroY); ctx.lineTo(padL + w, zeroY); ctx.stroke();
		ctx.setLineDash([]);

		// Y axis labels
		ctx.fillStyle = textColor;
		ctx.font = '10px system-ui';
		ctx.textAlign = 'right';
		ctx.fillText(`+${Math.round(max)}`, padL - 4, padT + 4);
		ctx.fillText(`${Math.round(min)}`, padL - 4, padT + h + 4);
		ctx.fillText('0', padL - 4, zeroY + 4);

		// Bars
		const barW = Math.max(2, w / data.length - 2);
		for (let i = 0; i < data.length; i++) {
			const d = data[i];
			const v = d.delta ?? 0;
			const x = padL + (i / data.length) * w + (w / data.length - barW) / 2;
			const barH = Math.abs((v / range) * h);
			const barY = v >= 0 ? zeroY - barH : zeroY;

			// Color: pressure drop (negative) = warm red, rise = cool blue
			const alpha = d.hasSymptom ? 1 : 0.4;
			ctx.fillStyle = v < 0 ? `rgba(239,68,68,${alpha})` : `rgba(59,130,246,${alpha})`;
			ctx.fillRect(x, barY, barW, barH || 1);
		}

		// X axis: date labels for first and last
		ctx.fillStyle = textColor;
		ctx.textAlign = 'left';
		ctx.font = '9px system-ui';
		if (data.length > 0) {
			ctx.fillText(data[0].date.slice(5), padL, height - 4);
			ctx.textAlign = 'right';
			ctx.fillText(data[data.length - 1].date.slice(5), padL + w, height - 4);
		}
	}

	onMount(() => {
		draw();
	});

	$effect(() => {
		// Redraw when data changes
		data;
		if (canvas) draw();
	});
</script>

<div class="chart-wrap">
	<p class="chart-title">Pressure change (hPa/day) — red bars = drops</p>
	<canvas bind:this={canvas} class="chart"></canvas>
	<p class="chart-legend">
		<span class="legend-dot symptom"></span> Day with symptom &nbsp;
		<span class="legend-dot no-symptom"></span> Day without
	</p>
</div>

<style>
	.chart-wrap {
		background: var(--bg-card);
		border-radius: 0.75rem;
		padding: 1rem;
	}

	.chart-title {
		margin: 0 0 0.5rem;
		font-size: 0.75rem;
		color: var(--text-muted);
	}

	.chart {
		width: 100%;
		height: 120px;
		display: block;
	}

	.chart-legend {
		margin: 0.5rem 0 0;
		font-size: 0.7rem;
		color: var(--text-subtle);
		display: flex;
		align-items: center;
		gap: 0.25rem;
	}

	.legend-dot {
		display: inline-block;
		width: 8px;
		height: 8px;
		border-radius: 50%;
	}

	.legend-dot.symptom { background: #ef4444; }
	.legend-dot.no-symptom { background: rgba(239,68,68,0.3); }
</style>

<script lang="ts">
	/** Five drawn faces for "How are you today?", from awful (1) to great (5). */
	interface Props {
		value: number | null;
		labels: [string, string, string, string, string];
		groupLabel: string;
		onchange?: (value: number) => void;
		size?: number;
	}

	let { value = $bindable(), labels, groupLabel, onchange, size = 52 }: Props = $props();

	const faces = [
		{ color: '#ec8a9a', mouth: 'M15 31 q9 -8 18 0', brows: true },
		{ color: '#f2ac72', mouth: 'M16 31 q8 -4 16 0', brows: false },
		{ color: '#e9c66b', mouth: 'M16 30 h16', brows: false },
		{ color: '#94d29c', mouth: 'M16 28 q8 6 16 0', brows: false },
		{ color: '#62c393', mouth: 'M14 27 q10 11 20 0 z', brows: false }
	];
</script>

<div class="faces" role="radiogroup" aria-label={groupLabel}>
	{#each faces as face, i (i)}
		{@const rating = i + 1}
		<button
			type="button"
			role="radio"
			aria-checked={value === rating}
			aria-label={labels[i]}
			title={labels[i]}
			class="face"
			class:selected={value === rating}
			class:dimmed={value !== null && value !== rating}
			style="--face: {face.color}; --size: {size}px"
			onclick={() => {
				value = rating;
				onchange?.(rating);
			}}
		>
			<svg viewBox="0 0 48 48" aria-hidden="true">
				<circle cx="24" cy="24" r="22" fill="var(--face)" />
				<circle cx="17" cy="20" r="2.6" fill="#3a2a22" />
				<circle cx="31" cy="20" r="2.6" fill="#3a2a22" />
				{#if face.brows}
					<path d="M13 15 l7 2.5 M35 15 l-7 2.5" stroke="#3a2a22" stroke-width="2.2" stroke-linecap="round" />
				{/if}
				{#if rating >= 4}
					<circle cx="12.5" cy="27" r="3" fill="#fff" opacity="0.35" />
					<circle cx="35.5" cy="27" r="3" fill="#fff" opacity="0.35" />
				{/if}
				<path
					d={face.mouth}
					stroke="#3a2a22"
					stroke-width="2.6"
					stroke-linecap="round"
					stroke-linejoin="round"
					fill={rating === 5 ? '#3a2a22' : 'none'}
				/>
			</svg>
		</button>
	{/each}
</div>

<style>
	.faces {
		display: flex;
		justify-content: space-between;
		gap: var(--space-2);
	}

	.face {
		width: var(--size);
		height: var(--size);
		padding: 0;
		border: none;
		border-radius: 50%;
		background: none;
		cursor: pointer;
		transition:
			transform 0.35s var(--ease-spring),
			opacity 0.2s,
			filter 0.2s;
	}

	.face svg {
		width: 100%;
		height: 100%;
		display: block;
	}

	.face:active {
		transform: scale(0.9);
	}

	.selected {
		transform: scale(1.18);
		animation: pop 0.5s var(--ease-spring);
	}

	.dimmed {
		opacity: 0.45;
		filter: saturate(0.6);
	}

	@keyframes pop {
		0% {
			transform: scale(0.85);
		}
		60% {
			transform: scale(1.28);
		}
		100% {
			transform: scale(1.18);
		}
	}
</style>

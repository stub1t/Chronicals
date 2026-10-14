<script lang="ts">
	/** A bare on/off switch; give it a label through `label` (read by screen readers). */
	interface Props {
		checked: boolean;
		label: string;
		disabled?: boolean;
		color?: string | null;
		onchange?: (checked: boolean) => void;
	}

	let { checked = $bindable(), label, disabled = false, color = null, onchange }: Props = $props();
</script>

<label class="switch" style={color ? `--on: ${color}` : ''}>
	<input type="checkbox" role="switch" aria-label={label} {disabled} bind:checked onchange={(e) => onchange?.(e.currentTarget.checked)} />
	<span class="track" aria-hidden="true"><span class="knob"></span></span>
</label>

<style>
	.switch {
		--on: var(--accent);
		position: relative;
		display: inline-flex;
		flex-shrink: 0;
		padding: 7px 0;
		cursor: pointer;
	}

	input {
		position: absolute;
		opacity: 0;
		inset: 0;
		z-index: 1;
		margin: 0;
		cursor: pointer;
	}

	.track {
		width: 50px;
		height: 30px;
		border-radius: var(--radius-pill);
		background: var(--border);
		position: relative;
		transition: background-color 0.2s;
	}

	.knob {
		position: absolute;
		inset-block-start: 3px;
		inset-inline-start: 3px;
		width: 24px;
		height: 24px;
		border-radius: 50%;
		background: #fff;
		box-shadow: var(--shadow-sm);
		transition: transform 0.3s var(--ease-spring);
	}

	input:checked + .track {
		background: var(--on);
	}

	input:checked + .track .knob {
		transform: translateX(20px);
	}

	:global([dir='rtl']) input:checked + .track .knob {
		transform: translateX(-20px);
	}

	input:focus-visible + .track {
		outline: 3px solid color-mix(in srgb, var(--accent) 55%, transparent);
		outline-offset: 2px;
	}

	input:disabled + .track {
		opacity: 0.5;
	}
</style>

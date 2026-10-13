<script lang="ts">
	interface Props {
		checked: boolean;
		label: string;
		description?: string;
		disabled?: boolean;
		onchange?: (checked: boolean) => void;
	}

	let { checked = $bindable(), label, description, disabled = false, onchange }: Props = $props();
	const id = `toggle-${Math.random().toString(36).slice(2, 9)}`;
</script>

<label class="row" for={id} class:disabled>
	<span class="text">
		<span class="label">{label}</span>
		{#if description}<span class="description">{description}</span>{/if}
	</span>
	<input
		{id}
		type="checkbox"
		role="switch"
		{disabled}
		bind:checked
		onchange={(e) => onchange?.(e.currentTarget.checked)}
	/>
	<span class="switch" aria-hidden="true"><span class="knob"></span></span>
</label>

<style>
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		min-height: 52px;
		cursor: pointer;
	}

	.disabled {
		opacity: 0.5;
		cursor: default;
	}

	.text {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.label {
		font-weight: 600;
	}

	.description {
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	input {
		position: absolute;
		opacity: 0;
		width: 1px;
		height: 1px;
	}

	.switch {
		flex-shrink: 0;
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

	input:checked + .switch {
		background: var(--accent);
	}

	input:checked + .switch .knob {
		transform: translateX(20px);
	}

	:global([dir='rtl']) input:checked + .switch .knob {
		transform: translateX(-20px);
	}

	input:focus-visible + .switch {
		outline: 3px solid color-mix(in srgb, var(--accent) 55%, transparent);
		outline-offset: 2px;
	}
</style>

<script lang="ts">
	import type { MetricSchema, MetricField } from '$lib/types';

	interface Props {
		schema: MetricSchema;
		values: Record<string, unknown>;
		onchange?: (key: string, value: unknown) => void;
	}

	let { schema, values, onchange }: Props = $props();

	function getValue(key: string, defaultVal: unknown): unknown {
		return values[key] !== undefined ? values[key] : defaultVal;
	}

	function getSliderDefault(field: MetricField): number {
		const min = field.config.min ?? 0;
		const max = field.config.max ?? 10;
		return Math.round((min + max) / 2);
	}

	function sliderPercent(field: MetricField, val: number): number {
		const min = field.config.min ?? 0;
		const max = field.config.max ?? 10;
		return Math.max(0, Math.min(100, ((val - min) / (max - min)) * 100));
	}

	/** green → orange → red based on 0-100 percent */
	function intensityGradientColor(pct: number): string {
		if (pct <= 50) {
			// green to orange
			const t = pct / 50;
			const r = Math.round(34 + (255 - 34) * t);
			const g = Math.round(197 + (165 - 197) * t);
			const b = Math.round(94 * (1 - t));
			return `rgb(${r},${g},${b})`;
		} else {
			// orange to red
			const t = (pct - 50) / 50;
			const r = Math.round(255);
			const g = Math.round(165 * (1 - t));
			const b = 0;
			return `rgb(${r},${g},${b})`;
		}
	}

	function isChecklistSelected(key: string, option: string): boolean {
		const val = values[key];
		if (Array.isArray(val)) return val.includes(option);
		return false;
	}

	function toggleChecklist(key: string, option: string) {
		const current = (values[key] as string[] | undefined) ?? [];
		const next = current.includes(option)
			? current.filter((v) => v !== option)
			: [...current, option];
		onchange?.(key, next);
	}
</script>

<div class="metric-form">
	{#each schema.fields as field (field.key)}
		<div class="field-group">
			<label class="field-label" for={`field-${field.key}`}>
				{field.label}
				{#if field.required}<span class="required" aria-hidden="true">*</span>{/if}
			</label>

			{#if field.type === 'slider'}
				{@const min = field.config.min ?? 0}
				{@const max = field.config.max ?? 10}
				{@const step = field.config.step ?? 1}
				{@const val = Number(getValue(field.key, getSliderDefault(field)))}
				{@const pct = sliderPercent(field, val)}
				{@const trackColor = intensityGradientColor(pct)}
				<div class="slider-wrapper">
					<input
						id={`field-${field.key}`}
						type="range"
						{min}
						{max}
						{step}
						value={val}
						style="--track-color: {trackColor}; --fill-pct: {pct}%"
						oninput={(e) => onchange?.(field.key, Number((e.target as HTMLInputElement).value))}
					/>
					<div class="slider-meta">
						<span class="slider-min">{min}</span>
						<span class="slider-value" style="color: {trackColor}">{val}</span>
						<span class="slider-max">{max}</span>
					</div>
				</div>

			{:else if field.type === 'select'}
				<select
					id={`field-${field.key}`}
					class="select-input"
					value={String(getValue(field.key, ''))}
					onchange={(e) => onchange?.(field.key, (e.target as HTMLSelectElement).value)}
				>
					<option value="" disabled>Choose…</option>
					{#each field.config.options ?? [] as opt}
						<option value={opt}>{opt}</option>
					{/each}
				</select>

			{:else if field.type === 'checklist'}
				<div class="checklist-grid" id={`field-${field.key}`}>
					{#each field.config.options ?? [] as opt}
						{@const active = isChecklistSelected(field.key, opt)}
						<button
							type="button"
							class="chip"
							class:active
							onclick={() => toggleChecklist(field.key, opt)}
							aria-pressed={active}
						>
							{opt}
						</button>
					{/each}
				</div>

			{:else if field.type === 'textarea'}
				<textarea
					id={`field-${field.key}`}
					class="textarea-input"
					rows={3}
					placeholder={field.config.placeholder ?? ''}
					value={String(getValue(field.key, ''))}
					oninput={(e) => onchange?.(field.key, (e.target as HTMLTextAreaElement).value)}
				></textarea>

			{:else if field.type === 'text'}
				<input
					id={`field-${field.key}`}
					type="text"
					class="text-input"
					placeholder={field.config.placeholder ?? ''}
					value={String(getValue(field.key, ''))}
					oninput={(e) => onchange?.(field.key, (e.target as HTMLInputElement).value)}
				/>
			{/if}
		</div>
	{/each}
</div>

<style>
	.metric-form {
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	.field-group {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.field-label {
		font-size: 0.85rem;
		font-weight: 600;
		color: var(--text);
	}

	.required {
		color: #e05c5c;
		margin-left: 2px;
	}

	/* Slider */
	.slider-wrapper {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	input[type='range'] {
		-webkit-appearance: none;
		appearance: none;
		width: 100%;
		height: 6px;
		border-radius: 3px;
		background: linear-gradient(
			to right,
			var(--track-color) 0%,
			var(--track-color) var(--fill-pct),
			var(--border) var(--fill-pct),
			var(--border) 100%
		);
		outline: none;
		cursor: pointer;
	}

	input[type='range']::-webkit-slider-thumb {
		-webkit-appearance: none;
		appearance: none;
		width: 20px;
		height: 20px;
		border-radius: 50%;
		background: var(--bg-card);
		border: 2px solid var(--track-color);
		cursor: pointer;
		box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
	}

	input[type='range']::-moz-range-thumb {
		width: 20px;
		height: 20px;
		border-radius: 50%;
		background: var(--bg-card);
		border: 2px solid var(--track-color);
		cursor: pointer;
		box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
	}

	.slider-meta {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.slider-min,
	.slider-max {
		font-size: 0.72rem;
		color: var(--text-subtle);
	}

	.slider-value {
		font-size: 0.9rem;
		font-weight: 700;
		transition: color 0.2s;
	}

	/* Select */
	.select-input {
		appearance: none;
		-webkit-appearance: none;
		width: 100%;
		padding: 10px 36px 10px 12px;
		background: var(--bg) url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'%3E%3Cpath d='M1 1l5 5 5-5' stroke='%23888' stroke-width='1.5' fill='none' stroke-linecap='round'/%3E%3C/svg%3E") no-repeat right 12px center;
		border: 1px solid var(--border);
		border-radius: 8px;
		font-size: 0.9rem;
		color: var(--text);
		font-family: inherit;
		cursor: pointer;
		transition: border-color 0.15s;
	}

	.select-input:focus {
		outline: none;
		border-color: var(--text-muted);
	}

	/* Checklist chips */
	.checklist-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}

	.chip {
		padding: 6px 14px;
		border-radius: 20px;
		border: 1.5px solid var(--border);
		background: var(--bg);
		color: var(--text-muted);
		font-size: 0.8rem;
		font-weight: 500;
		cursor: pointer;
		font-family: inherit;
		transition: background 0.15s, color 0.15s, border-color 0.15s;
	}

	.chip.active {
		background: var(--text);
		color: var(--bg);
		border-color: var(--text);
	}

	.chip:hover:not(.active) {
		background: var(--bg-subtle);
		border-color: var(--text-muted);
	}

	/* Textarea */
	.textarea-input {
		width: 100%;
		padding: 10px 12px;
		background: var(--bg);
		border: 1px solid var(--border);
		border-radius: 8px;
		font-size: 0.9rem;
		color: var(--text);
		font-family: inherit;
		resize: vertical;
		transition: border-color 0.15s;
		box-sizing: border-box;
	}

	.textarea-input:focus {
		outline: none;
		border-color: var(--text-muted);
	}

	.textarea-input::placeholder {
		color: var(--text-subtle);
	}

	/* Text input */
	.text-input {
		width: 100%;
		padding: 10px 12px;
		background: var(--bg);
		border: 1px solid var(--border);
		border-radius: 8px;
		font-size: 0.9rem;
		color: var(--text);
		font-family: inherit;
		transition: border-color 0.15s;
		box-sizing: border-box;
	}

	.text-input:focus {
		outline: none;
		border-color: var(--text-muted);
	}

	.text-input::placeholder {
		color: var(--text-subtle);
	}
</style>

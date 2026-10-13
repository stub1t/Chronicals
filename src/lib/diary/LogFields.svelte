<script lang="ts">
	/**
	 * The form fields of an entry type (its metric schema), made quick to fill
	 * on a phone: 1–5 scales become five big buttons, short option lists become
	 * chips, longer ones a list.
	 */
	import type { MetricField, MetricSchema } from '$lib/types';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import { hapticTap } from '$lib/haptics.js';

	interface Props {
		schema: MetricSchema;
		values: Record<string, unknown>;
		color: string;
		onchange: (key: string, value: unknown) => void;
	}

	let { schema, values, color, onchange }: Props = $props();

	const isScale = (f: MetricField) => f.type === 'slider' && (f.config.min ?? 0) === 1 && (f.config.max ?? 10) === 5;
	const scaleLabels = () => [t('intensity_1'), t('intensity_2'), t('intensity_3'), t('intensity_4'), t('intensity_5')];

	function selected(key: string): unknown {
		return values[key];
	}

	function toggleOption(key: string, option: string) {
		const current = Array.isArray(values[key]) ? (values[key] as string[]) : [];
		onchange(key, current.includes(option) ? current.filter((o) => o !== option) : [...current, option]);
		hapticTap();
	}
</script>

<div class="fields" style="--type: {color}">
	{#each schema.fields as field (field.key)}
		<div class="field">
			<span class="label" id="label-{field.key}">
				{localizeBuiltInText(field.label)}
			</span>

			{#if isScale(field)}
				{@const value = Number(selected(field.key) ?? 0)}
				<div class="scale" role="radiogroup" aria-labelledby="label-{field.key}">
					{#each [1, 2, 3, 4, 5] as level (level)}
						<button
							type="button"
							role="radio"
							aria-checked={value === level}
							aria-label="{level}: {scaleLabels()[level - 1]}"
							class="level"
							class:on={value >= level}
							class:current={value === level}
							style="--fill: {0.25 + level * 0.15}"
							onclick={() => {
								onchange(field.key, level);
								hapticTap();
							}}
						>
							{level}
						</button>
					{/each}
				</div>
				<p class="scale-word" aria-hidden="true">{value ? scaleLabels()[value - 1] : ' '}</p>
			{:else if field.type === 'slider'}
				{@const min = field.config.min ?? 0}
				{@const max = field.config.max ?? 10}
				{@const value = Number(selected(field.key) ?? Math.round((min + max) / 2))}
				<div class="range">
					<input
						type="range"
						{min}
						{max}
						step={field.config.step ?? 1}
						{value}
						aria-labelledby="label-{field.key}"
						oninput={(e) => onchange(field.key, Number(e.currentTarget.value))}
					/>
					<span class="range-value">{value}</span>
				</div>
			{:else if field.type === 'select' && (field.config.options?.length ?? 0) <= 10}
				<div class="chips" role="radiogroup" aria-labelledby="label-{field.key}">
					{#each field.config.options ?? [] as option (option)}
						<button
							type="button"
							role="radio"
							aria-checked={selected(field.key) === option}
							class="option"
							class:picked={selected(field.key) === option}
							onclick={() => {
								onchange(field.key, selected(field.key) === option ? undefined : option);
								hapticTap();
							}}
						>
							{localizeBuiltInText(option)}
						</button>
					{/each}
				</div>
			{:else if field.type === 'select'}
				<select
					class="input"
					aria-labelledby="label-{field.key}"
					value={String(selected(field.key) ?? '')}
					onchange={(e) => onchange(field.key, e.currentTarget.value || undefined)}
				>
					<option value="">{t('metric_choose')}</option>
					{#each field.config.options ?? [] as option (option)}
						<option value={option}>{localizeBuiltInText(option)}</option>
					{/each}
				</select>
			{:else if field.type === 'checklist'}
				<div class="chips" role="group" aria-labelledby="label-{field.key}">
					{#each field.config.options ?? [] as option (option)}
						{@const on = Array.isArray(selected(field.key)) && (selected(field.key) as string[]).includes(option)}
						<button type="button" aria-pressed={on} class="option" class:picked={on} onclick={() => toggleOption(field.key, option)}>
							{localizeBuiltInText(option)}
						</button>
					{/each}
				</div>
			{:else if field.type === 'textarea'}
				<textarea
					class="input"
					rows="3"
					aria-labelledby="label-{field.key}"
					placeholder={localizeBuiltInText(field.config.placeholder ?? '')}
					value={String(selected(field.key) ?? '')}
					oninput={(e) => onchange(field.key, e.currentTarget.value)}
				></textarea>
			{:else}
				<input
					class="input"
					type="text"
					aria-labelledby="label-{field.key}"
					placeholder={localizeBuiltInText(field.config.placeholder ?? '')}
					value={String(selected(field.key) ?? '')}
					oninput={(e) => onchange(field.key, e.currentTarget.value)}
				/>
			{/if}
		</div>
	{/each}
</div>

<style>
	.fields {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.label {
		font-weight: 800;
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.scale {
		display: grid;
		grid-template-columns: repeat(5, 1fr);
		gap: var(--space-2);
	}

	.level {
		aspect-ratio: 1;
		max-height: 60px;
		border-radius: var(--radius-md);
		border: 2px solid color-mix(in srgb, var(--type) 30%, var(--border));
		background: var(--bg-card);
		color: var(--text-muted);
		font-size: var(--text-lg);
		font-weight: 800;
		cursor: pointer;
		transition:
			transform 0.3s var(--ease-spring),
			background-color 0.2s;
	}

	.level.on {
		background: color-mix(in srgb, var(--type) calc(var(--fill) * 100%), var(--bg-card));
		border-color: var(--type);
		color: var(--text);
	}

	.level.current {
		transform: scale(1.08);
		box-shadow: 0 4px 14px color-mix(in srgb, var(--type) 35%, transparent);
	}

	.level:active {
		transform: scale(0.92);
	}

	.scale-word {
		margin: 0;
		text-align: center;
		font-weight: 700;
		color: var(--type);
	}

	.range {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.range input {
		flex: 1;
		accent-color: var(--type);
	}

	.range-value {
		min-width: 2ch;
		font-weight: 800;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.option {
		min-height: 40px;
		padding: 0 14px;
		border-radius: var(--radius-pill);
		border: 1.5px solid var(--border);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-sm);
		font-weight: 600;
		cursor: pointer;
		transition:
			transform 0.25s var(--ease-spring),
			background-color 0.15s;
	}

	.option:active {
		transform: scale(0.94);
	}

	/* Built-in options are stored in lower case ("temples"); start them with a capital. */
	.option::first-letter {
		text-transform: uppercase;
	}

	.option.picked {
		background: color-mix(in srgb, var(--type) 18%, var(--bg-card));
		border-color: var(--type);
	}

	.input {
		width: 100%;
		min-height: 46px;
		padding: 10px 14px;
		border: 1.5px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-md);
		resize: vertical;
	}
</style>

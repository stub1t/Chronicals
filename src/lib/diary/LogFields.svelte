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
		/** Options the user added, by field key. */
		extra?: Record<string, string[]>;
		/** Called with a new option for an extendable field; the caller saves it on the type. */
		onaddoption?: (key: string, option: string) => void;
	}

	let { schema, values, color, onchange, extra = {}, onaddoption }: Props = $props();

	const optionsOf = (f: MetricField) => [...new Set([...(f.config.options ?? []), ...(extra[f.key] ?? [])])];

	// The "+ Own" field that is open, and what is typed in it.
	let adding = $state<string | null>(null);
	let draft = $state('');

	function addOption(field: MetricField) {
		const option = draft.trim();
		adding = null;
		draft = '';
		if (!option) return;
		const known = optionsOf(field).find((o) => o.toLowerCase() === option.toLowerCase() || localizeBuiltInText(o).toLowerCase() === option.toLowerCase());
		if (!known) onaddoption?.(field.key, option);
		const pick = known ?? option;
		if (field.type === 'checklist') {
			const current = Array.isArray(values[field.key]) ? (values[field.key] as string[]) : [];
			if (!current.includes(pick)) onchange(field.key, [...current, pick]);
		} else {
			onchange(field.key, pick);
		}
		hapticTap();
	}

	// Number fields remember the last unit per field.
	function unitOf(field: MetricField): string | undefined {
		const stored = values[`${field.key}_unit`];
		if (typeof stored === 'string') return stored;
		try {
			return localStorage.getItem(`unit:${field.key}`) ?? field.config.units?.[0];
		} catch {
			return field.config.units?.[0];
		}
	}

	function setUnit(field: MetricField, unit: string) {
		onchange(`${field.key}_unit`, unit);
		try {
			localStorage.setItem(`unit:${field.key}`, unit);
		} catch {
			// Not remembered without storage.
		}
	}

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
			{:else if field.type === 'select' && optionsOf(field).length <= 12}
				<div class="chips" role="radiogroup" aria-labelledby="label-{field.key}">
					{#each optionsOf(field) as option (option)}
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
					{#if field.config.extendable && onaddoption}
						{#if adding === field.key}
							<form class="add" onsubmit={(e) => { e.preventDefault(); addOption(field); }}>
								<!-- svelte-ignore a11y_autofocus -->
								<input class="add-input" bind:value={draft} aria-label={t('log_own_option')} placeholder={t('log_own_option')} maxlength="80" autofocus onblur={() => addOption(field)} />
							</form>
						{:else}
							<button type="button" class="option own" onclick={() => (adding = field.key)}>+ {t('log_own_option')}</button>
						{/if}
					{/if}
				</div>
			{:else if field.type === 'select'}
				<select
					class="input"
					aria-labelledby="label-{field.key}"
					value={String(selected(field.key) ?? '')}
					onchange={(e) => onchange(field.key, e.currentTarget.value || undefined)}
				>
					<option value="">{t('metric_choose')}</option>
					{#each optionsOf(field) as option (option)}
						<option value={option}>{localizeBuiltInText(option)}</option>
					{/each}
				</select>
			{:else if field.type === 'checklist'}
				<div class="chips" role="group" aria-labelledby="label-{field.key}">
					{#each optionsOf(field) as option (option)}
						{@const on = Array.isArray(selected(field.key)) && (selected(field.key) as string[]).includes(option)}
						<button type="button" aria-pressed={on} class="option" class:picked={on} onclick={() => toggleOption(field.key, option)}>
							{localizeBuiltInText(option)}
						</button>
					{/each}
					{#if field.config.extendable && onaddoption}
						{#if adding === field.key}
							<form class="add" onsubmit={(e) => { e.preventDefault(); addOption(field); }}>
								<!-- svelte-ignore a11y_autofocus -->
								<input class="add-input" bind:value={draft} aria-label={t('log_own_option')} placeholder={t('log_own_option')} maxlength="80" autofocus onblur={() => addOption(field)} />
							</form>
						{:else}
							<button type="button" class="option own" onclick={() => (adding = field.key)}>+ {t('log_own_option')}</button>
						{/if}
					{/if}
				</div>
			{:else if field.type === 'number'}
				<div class="number">
					<input
						class="input"
						type="number"
						inputmode="decimal"
						step="any"
						aria-labelledby="label-{field.key}"
						value={selected(field.key) ?? ''}
						oninput={(e) => onchange(field.key, e.currentTarget.value === '' ? undefined : Number(e.currentTarget.value))}
					/>
					{#if field.config.units?.length}
						{@const unit = unitOf(field)}
						<div class="units" role="radiogroup" aria-label={t('log_unit')}>
							{#each field.config.units as option (option)}
								<button type="button" role="radio" aria-checked={unit === option} class="option unit" class:picked={unit === option} onclick={() => setUnit(field, option)}>
									{option}
								</button>
							{/each}
						</div>
					{/if}
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
	.option:not(.unit)::first-letter {
		text-transform: uppercase;
	}

	.option.picked {
		background: color-mix(in srgb, var(--type) 18%, var(--bg-card));
		border-color: var(--type);
	}

	.option.own {
		border-style: dashed;
		color: var(--text-muted);
	}

	.add-input {
		min-height: 40px;
		width: 160px;
		padding: 0 14px;
		border: 1.5px solid var(--type);
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-sm);
	}

	.number {
		display: flex;
		gap: var(--space-2);
		align-items: center;
	}

	.number .input {
		flex: 1;
		min-width: 0;
	}

	.units {
		display: flex;
		gap: 6px;
		flex-shrink: 0;
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

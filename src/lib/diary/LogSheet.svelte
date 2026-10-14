<script lang="ts">
	import { untrack } from 'svelte';
	/**
	 * Add or edit one log (e.g. a headache) on a day. Two taps for the common
	 * case: the start is "now" (or noon on past days), the duration is "still
	 * going" and 1–5 scales start in the middle.
	 */
	import { api } from '$lib/api.js';
	import Sheet from '$lib/ui/Sheet.svelte';
	import Button from '$lib/ui/Button.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import LogFields from './LogFields.svelte';
	import { hasDuration } from './categories.js';
	import { DiaryDay, localDateTime, localTime } from './day.svelte.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { hapticSuccess, hapticTap } from '$lib/haptics.js';
	import { toDateString } from '$lib/utils/date';
	import type { EntryType, EntryTypeLog, Medication } from '$lib/types';

	interface Props {
		open: boolean;
		day: DiaryDay;
		entryType: EntryType | null;
		/** An existing log to edit; null to add a new one. */
		log?: EntryTypeLog | null;
		/** Active ones are offered under "Taken anything for it?". */
		medications?: Medication[];
		/** The type changed (an own option was added). */
		ontypechange?: (type: EntryType) => void;
	}

	let { open = $bindable(), day, entryType, log = null, medications = [], ontypechange }: Props = $props();

	async function addOption(key: string, option: string) {
		if (!entryType) return;
		const extraOptions = { ...entryType.extraOptions, [key]: [...(entryType.extraOptions?.[key] ?? []), option] };
		const res = await api(`/api/entry-types/${entryType.id}`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ extraOptions })
		});
		if (res.ok) ontypechange?.(await res.json());
	}

	const DURATIONS = [15, 30, 60, 120, 240];

	let time = $state('12:00');
	let duration = $state<'ongoing' | 'custom' | number>('ongoing');
	let endTime = $state('13:00');
	let values = $state<Record<string, unknown>>({});
	let saving = $state(false);

	// "Painkiller" is stored in English, like other built-in options, and translated when shown.
	const PAINKILLER = 'Painkiller';
	let reliefInput = $state('');
	let addingRelief = $state(false);
	const relief = $derived(Array.isArray(values.relief) ? (values.relief as string[]) : []);
	const reliefOptions = $derived([
		...new Set([...medications.filter((m) => m.isActive).map((m) => m.name), PAINKILLER, ...relief])
	]);

	function toggleRelief(name: string) {
		values = { ...values, relief: relief.includes(name) ? relief.filter((r) => r !== name) : [...relief, name] };
		hapticTap();
	}

	function addRelief() {
		const name = reliefInput.trim();
		if (name && !relief.includes(name)) values = { ...values, relief: [...relief, name] };
		reliefInput = '';
		addingRelief = false;
	}

	// Reset the form whenever the sheet opens, and only then: the type also
	// changes while open when an own option is added, and that must keep the input.
	$effect(() => {
		if (!open) return;
		untrack(() => reset());
	});

	function reset() {
		if (!entryType) return;
		if (log) {
			time = localTime(log.startTime);
			values = { ...log.metricValues };
			endTime = time;
			if (!log.endTime) duration = 'ongoing';
			else {
				const minutes = Math.round((Date.parse(log.endTime) - Date.parse(log.startTime)) / 60000);
				duration = DURATIONS.includes(minutes) ? minutes : 'custom';
				endTime = localTime(log.endTime);
			}
		} else {
			time = day.date === toDateString(new Date()) ? localTime(new Date().toISOString()) : '12:00';
			// Symptoms usually start without a known end; events like a hypo are short.
			duration = entryType.category === 'event' ? 30 : 'ongoing';
			endTime = time;
			values = Object.fromEntries(
				entryType.metricSchema.fields
					.filter((f) => f.type === 'slider' && f.required)
					.map((f) => [f.key, Math.round(((f.config.min ?? 0) + (f.config.max ?? 10)) / 2)])
			);
		}
	}

	/** 'HH:MM' some minutes later, wrapping past midnight. */
	function addMinutes(hhmm: string, minutes: number): string {
		const [h, m] = hhmm.split(':').map(Number);
		const total = (h * 60 + m + minutes) % 1440;
		return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
	}

	function durationLabel(minutes: number): string {
		return minutes < 60 ? t('duration_minutes', { count: minutes }) : t('duration_hours', { count: minutes / 60 });
	}

	function computedEnd(): string | null {
		if (duration === 'ongoing') return null;
		const start = localDateTime(day.date, time);
		if (duration === 'custom') {
			const end = localDateTime(day.date, endTime);
			// An end before the start means the symptom went on past midnight.
			return end >= start ? end : new Date(Date.parse(end) + 86_400_000).toISOString();
		}
		return new Date(Date.parse(start) + duration * 60_000).toISOString();
	}

	async function save() {
		if (!entryType || !time || (duration === 'custom' && !endTime)) return;
		saving = true;
		const cleaned = Object.fromEntries(
			Object.entries(values).filter(([, v]) => v !== undefined && v !== '' && !(Array.isArray(v) && v.length === 0))
		);
		try {
			if (log) {
				await day.updateLog(log.id, { startTime: localDateTime(day.date, time), endTime: computedEnd(), metricValues: cleaned });
			} else {
				await day.addLog({
					entryTypeId: entryType.id,
					startTime: localDateTime(day.date, time),
					endTime: computedEnd(),
					metricValues: cleaned
				});
			}
			hapticSuccess();
			toasts.show(t('log_saved', { name: localizeBuiltInText(entryType.name) }), 'success');
			open = false;
		} catch (err) {
			toasts.show((err as Error).message, 'error');
		} finally {
			saving = false;
		}
	}

	async function remove() {
		if (!log || !entryType || !confirm(t('log_delete_confirm'))) return;
		await day.deleteLog(log.id);
		toasts.show(t('log_deleted'));
		open = false;
	}
</script>

{#if entryType}
	<Sheet
		bind:open
		title={localizeBuiltInText(entryType.name)}
		closeLabel={t('close')}
		tint={entryType.color}
	>
		<div class="content" style="--type: {entryType.color ?? 'var(--accent)'}">
			<div class="hero">
				<EntryTypeIcon {entryType} size={56} badge />
			</div>

			<section class="when">
				<label class="label" for="log-time">{hasDuration(entryType.category) ? t('log_started') : t('log_time')}</label>
				<div class="time-row">
					<input id="log-time" class="time" type="time" bind:value={time} />
					{#if day.date === toDateString(new Date())}
						<button type="button" class="now" onclick={() => (time = localTime(new Date().toISOString()))}>{t('log_now')}</button>
					{/if}
				</div>

				{#if hasDuration(entryType.category)}
					<span class="label">{t('log_how_long')}</span>
					<div class="chips">
						<button
							type="button"
							class="option"
							class:picked={duration === 'ongoing'}
							aria-pressed={duration === 'ongoing'}
							onclick={() => {
								duration = 'ongoing';
								hapticTap();
							}}>{t('log_ongoing')}</button
						>
						{#each DURATIONS as minutes (minutes)}
							<button
								type="button"
								class="option"
								class:picked={duration === minutes}
								aria-pressed={duration === minutes}
								onclick={() => {
									duration = minutes;
									hapticTap();
								}}>{durationLabel(minutes)}</button
							>
						{/each}
						<button
							type="button"
							class="option"
							class:picked={duration === 'custom'}
							aria-pressed={duration === 'custom'}
							onclick={() => {
								// Start from the chosen duration (or an hour) after the start, not the start itself.
								if (time && duration !== 'custom') endTime = addMinutes(time, typeof duration === 'number' ? duration : 60);
								duration = 'custom';
							}}>{t('log_until')}</button
						>
					</div>
					{#if duration === 'custom'}
						<input class="time" type="time" bind:value={endTime} aria-label={t('log_end_time')} />
					{/if}
				{/if}
			</section>

			<LogFields
				schema={entryType.metricSchema}
				{values}
				color={entryType.color ?? 'var(--accent)'}
				extra={entryType.extraOptions}
				onaddoption={addOption}
				onchange={(key, value) => (values = { ...values, [key]: value })}
			/>

			{#if entryType.category === 'symptom'}
				<section class="when">
					<span class="label" id="relief-label">{t('log_relief')}</span>
					<div class="chips" role="group" aria-labelledby="relief-label">
						{#each reliefOptions as option (option)}
							<button
								type="button"
								class="option"
								class:picked={relief.includes(option)}
								aria-pressed={relief.includes(option)}
								onclick={() => toggleRelief(option)}>{localizeBuiltInText(option)}</button
							>
						{/each}
						{#if addingRelief}
							<form class="add-relief" onsubmit={(e) => { e.preventDefault(); addRelief(); }}>
								<!-- svelte-ignore a11y_autofocus -->
								<input class="relief-input" bind:value={reliefInput} placeholder={t('log_relief_placeholder')} aria-label={t('log_relief_other')} maxlength="80" autofocus onblur={addRelief} />
							</form>
						{:else}
							<button type="button" class="option add" onclick={() => (addingRelief = true)}>+ {t('log_relief_other')}</button>
						{/if}
					</div>
				</section>
			{/if}
		</div>

		{#snippet footer()}
			<div class="actions">
				{#if log}
					<Button variant="danger" onclick={remove}>{t('delete')}</Button>
				{/if}
				<Button block loading={saving} disabled={!time || (duration === 'custom' && !endTime)} onclick={save} style="--accent: {entryType.color ?? 'var(--accent)'}">
					{log ? t('save') : t('log_add', { name: localizeBuiltInText(entryType.name) })}
				</Button>
			</div>
		{/snippet}
	</Sheet>
{/if}

<style>
	.content {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.hero {
		display: flex;
		justify-content: center;
		padding-top: var(--space-1);
	}

	.when {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.label {
		font-weight: 800;
		font-size: var(--text-sm);
		color: var(--text-muted);
		margin-top: var(--space-2);
	}

	.time-row {
		display: flex;
		gap: var(--space-2);
		align-items: center;
	}

	.time {
		min-height: 48px;
		padding: 0 var(--space-4);
		border: 1.5px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-lg);
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}

	.now {
		min-height: 40px;
		padding: 0 var(--space-4);
		border: none;
		border-radius: var(--radius-pill);
		background: color-mix(in srgb, var(--type) 15%, var(--bg-card));
		color: var(--text);
		font-weight: 700;
		cursor: pointer;
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
		transition: transform 0.25s var(--ease-spring);
	}

	.option:active {
		transform: scale(0.94);
	}

	.option.picked {
		background: color-mix(in srgb, var(--type) 18%, var(--bg-card));
		border-color: var(--type);
	}

	.option.add {
		border-style: dashed;
		color: var(--text-muted);
	}

	.relief-input {
		min-height: 40px;
		width: 160px;
		padding: 0 14px;
		border: 1.5px solid var(--type);
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-sm);
	}

	.actions {
		display: flex;
		gap: var(--space-2);
	}
</style>

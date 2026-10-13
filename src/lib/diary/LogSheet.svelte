<script lang="ts">
	/**
	 * Add or edit one log (e.g. a headache) on a day. Two taps for the common
	 * case: the start is "now" (or noon on past days), the duration is "still
	 * going" and 1–5 scales start in the middle.
	 */
	import Sheet from '$lib/ui/Sheet.svelte';
	import Button from '$lib/ui/Button.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import LogFields from './LogFields.svelte';
	import { DiaryDay, localDateTime, localTime } from './day.svelte.js';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { hapticSuccess, hapticTap } from '$lib/haptics.js';
	import { toDateString } from '$lib/utils/date';
	import type { EntryType, EntryTypeLog } from '$lib/types';

	interface Props {
		open: boolean;
		day: DiaryDay;
		entryType: EntryType | null;
		/** An existing log to edit; null to add a new one. */
		log?: EntryTypeLog | null;
	}

	let { open = $bindable(), day, entryType, log = null }: Props = $props();

	const DURATIONS = [15, 30, 60, 120, 240];

	let time = $state('12:00');
	let duration = $state<'ongoing' | 'custom' | number>('ongoing');
	let endTime = $state('13:00');
	let values = $state<Record<string, unknown>>({});
	let saving = $state(false);

	// Reset the form whenever the sheet opens.
	$effect(() => {
		if (!open || !entryType) return;
		if (log) {
			time = localTime(log.startTime);
			values = { ...log.metricValues };
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
	});

	function durationLabel(minutes: number): string {
		return minutes < 60 ? t('duration_minutes', { count: minutes }) : t('duration_hours', { count: minutes / 60 });
	}

	function computedEnd(): string | null {
		if (duration === 'ongoing') return null;
		const start = localDateTime(day.date, time);
		if (duration === 'custom') {
			const end = localDateTime(day.date, endTime);
			// An end before the start means the symptom went on past midnight.
			return end > start ? end : new Date(Date.parse(end) + 86_400_000).toISOString();
		}
		return new Date(Date.parse(start) + duration * 60_000).toISOString();
	}

	async function save() {
		if (!entryType) return;
		saving = true;
		const cleaned = Object.fromEntries(Object.entries(values).filter(([, v]) => v !== undefined && v !== ''));
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
				<label class="label" for="log-time">{t('log_started')}</label>
				<div class="time-row">
					<input id="log-time" class="time" type="time" bind:value={time} />
					{#if day.date === toDateString(new Date())}
						<button type="button" class="now" onclick={() => (time = localTime(new Date().toISOString()))}>{t('log_now')}</button>
					{/if}
				</div>

				{#if entryType.category !== 'note'}
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
							onclick={() => (duration = 'custom')}>{t('log_until')}</button
						>
					</div>
					{#if duration === 'custom'}
						<input class="time" type="time" bind:value={endTime} aria-label={t('log_end_time')} />
					{/if}
				{/if}
			</section>

			<LogFields schema={entryType.metricSchema} {values} color={entryType.color ?? 'var(--accent)'} onchange={(key, value) => (values = { ...values, [key]: value })} />
		</div>

		{#snippet footer()}
			<div class="actions">
				{#if log}
					<Button variant="danger" onclick={remove}>{t('delete')}</Button>
				{/if}
				<Button block loading={saving} onclick={save} style="--accent: {entryType.color ?? 'var(--accent)'}">
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

	.actions {
		display: flex;
		gap: var(--space-2);
	}
</style>

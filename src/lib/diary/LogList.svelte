<script lang="ts">
	/** The logs of one day as tappable cards; ongoing ones get an "End now" button. */
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import CircleStop from '@lucide/svelte/icons/circle-stop';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import { dateLocale, formatTime, localizeBuiltInText, t } from '$lib/i18n';
	import type { EntryTypeLog } from '$lib/types';

	interface Props {
		logs: EntryTypeLog[];
		onopen: (log: EntryTypeLog) => void;
		onend: (log: EntryTypeLog) => void;
	}

	let { logs, onopen, onend }: Props = $props();

	const time = (iso: string) =>
		formatTime(iso);

	function duration(log: EntryTypeLog): string {
		if (!log.endTime) return '';
		const minutes = Math.round((Date.parse(log.endTime) - Date.parse(log.startTime)) / 60000);
		return minutes < 60
			? t('duration_minutes', { count: minutes })
			: t('duration_hours', { count: Math.round(minutes / 30) / 2 });
	}

	const sorted = $derived([...logs].sort((a, b) => a.startTime.localeCompare(b.startTime)));
</script>

<ul class="logs">
	{#each sorted as log (log.id)}
		{@const color = log.entryType?.color ?? 'var(--accent)'}
		<li class="log" style="--type: {color}">
			<button type="button" class="main" onclick={() => onopen(log)}>
				<EntryTypeIcon entryType={log.entryType} size={42} badge />
				<span class="text">
					<span class="name">{localizeBuiltInText(log.entryType?.name ?? '')}</span>
					<span class="meta">
						{time(log.startTime)}{#if log.endTime}&nbsp;–&nbsp;{time(log.endTime)} · {duration(log)}{/if}
					</span>
				</span>
				{#if log.intensityCache}
					<span class="dots" aria-label={t('log_intensity_value', { value: log.intensityCache })}>
						{#each [1, 2, 3, 4, 5] as level (level)}
							<span class="dot" class:on={log.intensityCache >= level}></span>
						{/each}
					</span>
				{/if}
				<ChevronRight size={18} class="flip-rtl chevron" />
			</button>
			{#if !log.endTime && log.entryType?.category !== 'note'}
				<button type="button" class="end" onclick={() => onend(log)}>
					<CircleStop size={16} />
					<span class="ongoing">{t('log_ongoing')}</span>
					<span class="end-label">{t('log_end_now')}</span>
				</button>
			{/if}
		</li>
	{/each}
</ul>

<style>
	.logs {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.log {
		border-radius: var(--radius-md);
		background: color-mix(in srgb, var(--type) 9%, var(--bg-card));
		border: 1px solid color-mix(in srgb, var(--type) 20%, transparent);
		overflow: hidden;
	}

	.main {
		width: 100%;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3);
		border: none;
		background: none;
		color: var(--text);
		text-align: start;
		cursor: pointer;
	}

	.text {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}

	.name {
		font-weight: 800;
	}

	.meta {
		font-size: var(--text-sm);
		color: var(--text-muted);
		font-variant-numeric: tabular-nums;
	}

	.dots {
		display: flex;
		gap: 3px;
	}

	.dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: color-mix(in srgb, var(--type) 25%, var(--bg-card));
	}

	.dot.on {
		background: var(--type);
	}

	.main :global(.chevron) {
		color: var(--text-subtle);
	}

	.end {
		width: 100%;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border: none;
		border-top: 1px dashed color-mix(in srgb, var(--type) 30%, transparent);
		background: color-mix(in srgb, var(--type) 6%, transparent);
		color: var(--type);
		font-weight: 700;
		font-size: var(--text-sm);
		cursor: pointer;
	}

	.ongoing {
		flex: 1;
		text-align: start;
		animation: pulse 2s ease-in-out infinite;
	}

	.end-label {
		color: var(--text);
	}

	@keyframes pulse {
		50% {
			opacity: 0.55;
		}
	}
</style>

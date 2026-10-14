<script lang="ts">
	import ArrowLeft from '@lucide/svelte/icons/arrow-left';
	import Printer from '@lucide/svelte/icons/printer';
	import Sheet from '@lucide/svelte/icons/sheet';
	import { getJson } from '$lib/api.js';
	import { saveFile } from '$lib/files.js';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { reportToCsv } from '$lib/report/csv.js';
	import { reportToHtml } from '$lib/report/html.js';
	import { buildReport, type Report } from '$lib/report/summary.js';
	import { addDays } from '$lib/insights/dates.js';
	import { dateLocale, formatNumber, isRtl, language, localizeBuiltInText, t, type MessageKey } from '$lib/i18n';
	import type { Entry, EntryType, EnvironmentalData, Medication, MedicationChange, UserProfile } from '$lib/types/index.js';
	import { toDateString } from '$lib/utils/date';

	interface Props {
		data: { entryTypes: EntryType[]; profile: UserProfile; medications: Medication[]; changes: MedicationChange[] };
	}

	let { data }: Props = $props();

	function monthsAgo(months: number): string {
		const d = new Date();
		d.setMonth(d.getMonth() - months);
		return addDays(toDateString(d), 1);
	}

	type Range = '1' | '3' | '12' | 'custom';
	let range = $state<Range>('3');
	let from = $state(monthsAgo(3));
	let to = $state(toDateString(new Date()));
	let report = $state<Report | null>(null);
	let html = $state('');
	let loading = $state(false);

	const formatDate = (date: string) =>
		new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' });
	// 24-hour times: shorter in the tables, and usual in medical records.
	const formatTime = (iso: string) => new Date(iso).toLocaleTimeString(dateLocale(), { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });

	function pick(value: Range) {
		range = value;
		if (value !== 'custom') {
			from = monthsAgo(Number(value));
			to = toDateString(new Date());
		}
	}

	// Rebuild whenever the period or the language changes.
	$effect(() => {
		const [start, end] = [from, to];
		void $language;
		if (!start || !end || start > end) return;
		let cancelled = false;
		loading = true;
		Promise.all([
			getJson<Entry[]>(globalThis.fetch, `/api/entries?from=${addDays(start, -1)}&to=${end}`),
			getJson<EnvironmentalData[]>(globalThis.fetch, `/api/environmental?from=${addDays(start, -1)}&to=${end}`)
		])
			.then(([entries, weather]) => {
				if (cancelled) return;
				const built = buildReport(
					{ from: start, to: end, name: data.profile.displayName, entries, weather, entryTypes: data.entryTypes, medications: data.medications, changes: data.changes },
					{
						translate: localizeBuiltInText,
						time: formatTime,
						weatherName: (key) => t(`insights_weather_${key.replaceAll('-', '_')}` as MessageKey)
					}
				);
				report = built;
				html = reportToHtml(built, {
					t,
					date: formatDate,
					number: formatNumber,
					lang: $language,
					dir: isRtl() ? 'rtl' : 'ltr',
					created: formatDate(toDateString(new Date()))
				});
			})
			.catch((err) => toasts.show(t('report_failed', { message: (err as Error).message }), 'error'))
			.finally(() => (loading = false));
		return () => (cancelled = true);
	});

	const filename = (ext: string) => `chronicals-report-${from}-${to}.${ext}`;

	async function print() {
		try {
			const { Printer: printer } = await import('@capgo/capacitor-printer');
			await printer.printHtml({ name: filename('pdf').replace(/\.pdf$/, ''), html });
		} catch (err) {
			toasts.show(t('report_failed', { message: (err as Error).message }), 'error');
		}
	}

	async function saveCsv() {
		if (!report) return;
		// Where the decimal mark is a comma, spreadsheets expect ';' between cells.
		const separator = formatNumber(1.5).includes(',') ? ';' : ',';
		const csv = reportToCsv(report, {
			separator,
			headings: [
				t('report_col_date'),
				t('report_col_type'),
				t('report_col_start'),
				t('report_col_end'),
				t('report_col_minutes'),
				t('report_col_intensity_single'),
				t('report_col_details'),
				t('report_col_triggers'),
				t('report_col_note')
			]
		});
		try {
			await saveFile(new Blob([csv], { type: 'text/csv;charset=utf-8' }), filename('csv'));
		} catch (err) {
			toasts.show(t('report_failed', { message: (err as Error).message }), 'error');
		}
	}
</script>

<div class="page">
	<header class="header">
		<a class="back" href="/insights"><ArrowLeft size={18} class="flip-rtl" />{t('nav_insights')}</a>
		<h1 class="title">{t('report_title')}</h1>
		<p class="ui-hint">{t('report_intro')}</p>
	</header>

	<Card>
		<div class="controls">
			<Segmented
				label={t('report_period_label')}
				value={range}
				onchange={pick}
				options={[
					{ value: '1', label: t('report_range_1m') },
					{ value: '3', label: t('report_range_3m') },
					{ value: '12', label: t('report_range_12m') },
					{ value: 'custom', label: t('report_range_custom') }
				]}
			/>
			{#if range === 'custom'}
				<div class="dates">
					<label class="ui-field">
						<span class="ui-label">{t('report_from')}</span>
						<input class="ui-input" type="date" bind:value={from} max={to} />
					</label>
					<label class="ui-field">
						<span class="ui-label">{t('report_to')}</span>
						<input class="ui-input" type="date" bind:value={to} min={from} />
					</label>
				</div>
			{/if}
			{#if report}
				<p class="meta">
					{t('report_period', { from: formatDate(report.from), to: formatDate(report.to) })} ·
					{t('report_days_with_entries', { count: report.trackedDays })}
				</p>
			{/if}
			<div class="actions">
				<Button block disabled={!html || loading} onclick={print}><Printer size={18} />{t('report_print')}</Button>
				<Button block variant="secondary" disabled={!report || loading} onclick={saveCsv}><Sheet size={18} />{t('report_save_csv')}</Button>
			</div>
		</div>
	</Card>

	{#if html}
		<!-- The report as it will print; sandboxed, so nothing in it can run. -->
		<iframe class="preview" class:loading title={t('report_preview')} srcdoc={html} sandbox=""></iframe>
	{/if}
</div>

<style>
	.page {
		max-width: 760px;
		margin: 0 auto;
		padding: var(--space-5) var(--space-4) var(--space-8);
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.header {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.back {
		align-self: flex-start;
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: 44px;
		color: var(--text-muted);
		font-weight: 700;
		text-decoration: none;
	}

	.title {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 800;
	}

	.controls {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.dates {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-3);
	}

	.meta {
		margin: 0;
		font-size: var(--text-sm);
		font-weight: 700;
		color: var(--text-muted);
	}

	.actions {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-2);
	}

	.preview {
		width: 100%;
		height: 70vh;
		border: none;
		border-radius: var(--radius-md);
		background: #fff;
		box-shadow: var(--shadow-md);
		transition: opacity 0.2s;
	}

	.preview.loading {
		opacity: 0.5;
	}

	@media (max-width: 480px) {
		.actions {
			grid-template-columns: 1fr;
		}
	}
</style>

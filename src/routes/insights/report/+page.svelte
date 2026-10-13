<script lang="ts">
	import { api, getJson } from '$lib/api.js';
	import { saveFile } from '$lib/files.js';
	import { reportToCsv } from '$lib/report/csv.js';
	import { buildReport, formatDuration, type Report, type ReportInput } from '$lib/report/summary.js';
	import { dateLocale, language, localizeBuiltInText, t } from '$lib/i18n';
	import type { Entry, EntryType, Medication, MedicationChange, UserProfile } from '$lib/types/index.js';
	import { toDateString } from '$lib/utils/date';

	let { data }: { data: { entryTypes: EntryType[]; profile: UserProfile } } = $props();

	function monthsAgo(months: number): string {
		const d = new Date();
		d.setMonth(d.getMonth() - months);
		return toDateString(d);
	}

	let from = $state(monthsAgo(3));
	let to = $state(toDateString(new Date()));
	let report = $state<Report | null>(null);
	let busy = $state(false);
	let error = $state<string | null>(null);

	const formatDate = (date: string) =>
		new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' });
	// 24-hour times in both languages: shorter in the tables, and usual in medical records.
	const formatTime = (iso: string) =>
		new Date(iso).toLocaleTimeString(dateLocale(), { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });

	async function createReport() {
		busy = true;
		error = null;
		try {
			const [entries, medications, changes] = await Promise.all([
				getJson<Entry[]>(globalThis.fetch, `/api/entries?from=${from}&to=${to}`),
				getJson<Medication[]>(globalThis.fetch, '/api/medications'),
				getJson<MedicationChange[]>(globalThis.fetch, '/api/medication-changes')
			]);

			// Correlations only make sense for symptoms that were logged in the period.
			const logged = new Set(entries.flatMap((e) => (e.logs ?? []).map((l) => l.entryTypeId)));
			const symptoms = data.entryTypes.filter((type) => type.category === 'symptom' && logged.has(type.id));
			const correlations: ReportInput['correlations'] = {};
			await Promise.all(
				symptoms.map(async (type) => {
					const res = await api(`/api/analysis/correlations?entryTypeId=${type.id}&startDate=${from}&endDate=${to}`);
					if (res.ok) correlations[type.id] = await res.json();
				})
			);

			report = buildReport(
				{ from, to, name: data.profile.displayName, entries, entryTypes: data.entryTypes, medications, changes, correlations },
				{ translate: localizeBuiltInText, time: formatTime }
			);
		} catch (err) {
			error = t('report_failed', { message: (err as Error).message });
		} finally {
			busy = false;
		}
	}

	async function savePdf() {
		if (!report) return;
		busy = true;
		try {
			const { reportToPdf } = await import('$lib/report/pdf.js');
			const blob = reportToPdf(report, { t, date: formatDate, translate: localizeBuiltInText });
			await saveFile(blob, `chronicals-report-${report.from}-${report.to}.pdf`);
		} catch (err) {
			error = t('report_failed', { message: (err as Error).message });
		} finally {
			busy = false;
		}
	}

	async function saveCsv() {
		if (!report) return;
		const csv = reportToCsv(report, {
			separator: $language === 'de' ? ';' : ',',
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
			await saveFile(new Blob([csv], { type: 'text/csv;charset=utf-8' }), `chronicals-report-${report.from}-${report.to}.csv`);
		} catch (err) {
			error = t('report_failed', { message: (err as Error).message });
		}
	}
</script>

<div class="page">
	<header class="page-header">
		<a href="/insights" class="back-link">← {t('analysis_title')}</a>
		<h1 class="page-title">{t('report_title')}</h1>
		<p class="intro">{t('report_intro')}</p>
	</header>

	<section class="controls">
		<div class="ranges" role="group">
			{#each [[1, 'report_range_1m'], [3, 'report_range_3m'], [12, 'report_range_12m']] as const as [months, key] (months)}
				<button class="pill" onclick={() => { from = monthsAgo(months); to = toDateString(new Date()); }}>{t(key)}</button>
			{/each}
		</div>
		<div class="dates">
			<label class="field">
				<span>{t('report_from')}</span>
				<input class="input" type="date" bind:value={from} max={to} />
			</label>
			<label class="field">
				<span>{t('report_to')}</span>
				<input class="input" type="date" bind:value={to} min={from} />
			</label>
		</div>
		<button class="primary" onclick={createReport} disabled={busy || !from || !to}>
			{busy && !report ? t('report_creating') : t('report_create')}
		</button>
		{#if error}
			<p class="error" role="alert">{error}</p>
		{/if}
	</section>

	{#if report}
		<section class="result">
			<p class="meta">
				{t('report_period', { from: formatDate(report.from), to: formatDate(report.to) })} ·
				{t('report_days_with_entries', { count: report.daysWithEntries })}
			</p>
			<div class="actions">
				<button class="primary" onclick={savePdf} disabled={busy}>{t('report_save_pdf')}</button>
				<button class="secondary" onclick={saveCsv} disabled={busy}>{t('report_save_csv')}</button>
			</div>

			<h2>{t('report_summary')}</h2>
			{#if report.types.length === 0}
				<p class="muted">{t('report_empty')}</p>
			{:else}
				<table>
					<thead>
						<tr>
							<th>{t('report_col_type')}</th>
							<th>{t('report_col_days')}</th>
							<th>{t('report_col_intensity')}</th>
							<th>{t('report_col_duration')}</th>
						</tr>
					</thead>
					<tbody>
						{#each report.types as summary (summary.entryType.id)}
							<tr>
								<td>{summary.name}</td>
								<td>{summary.days}</td>
								<td>{summary.avgIntensity === null ? '' : summary.avgIntensity.toFixed(1)}</td>
								<td>{formatDuration(summary.avgDurationMinutes)}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			{/if}

			<h2>{t('report_triggers')}</h2>
			{#if report.types.every((s) => s.triggers.length === 0 && s.environmental.length === 0)}
				<p class="muted">{t('report_no_triggers')}</p>
			{:else}
				<ul class="list">
					{#each report.types as summary (summary.entryType.id)}
						{#each summary.triggers as c (c.triggerId)}
							<li>
								<strong>{summary.name}:</strong> {localizeBuiltInText(c.triggerName)}
								({t(c.strength === 'strong' ? 'report_strength_strong' : 'report_strength_possible')}, φ {c.phi.toFixed(2)})
							</li>
						{/each}
						{#each summary.environmental as c (c.variable)}
							<li>
								<strong>{summary.name}:</strong> {t(c.variable === 'uv_index' ? 'report_uv_index' : 'report_pressure_drop')}
								({t(c.strength === 'strong' ? 'report_strength_strong' : 'report_strength_possible')}, φ {c.phi.toFixed(2)})
							</li>
						{/each}
					{/each}
				</ul>
			{/if}

			<h2>{t('report_medications')}</h2>
			{#if report.medications.length === 0}
				<p class="muted">{t('report_no_medications')}</p>
			{:else}
				<ul class="list">
					{#each report.medications as { medication, changes } (medication.id)}
						<li>
							<strong>{medication.name}</strong>
							{[medication.dosage, medication.unit].filter(Boolean).join(' ')} ·
							{t('report_period', {
								from: formatDate(medication.startDate),
								to: medication.endDate ? formatDate(medication.endDate) : t('report_ongoing')
							})}
							{#each changes as c (c.id)}
								<br /><span class="muted"
									>{formatDate(c.changeDate)}: {t(`report_change_${c.changeType}`, {
										old: c.oldDosage ?? '',
										new: c.newDosage ?? ''
									})}</span
								>
							{/each}
						</li>
					{/each}
				</ul>
			{/if}

			<p class="muted">{t('report_disclaimer')}</p>
		</section>
	{/if}
</div>

<style>
	.page {
		max-width: 560px;
		margin: 0 auto;
		padding: 24px 16px 80px;
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	.page-header {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.back-link {
		font-size: 0.82rem;
		color: var(--text-muted);
		text-decoration: none;
		align-self: flex-start;
	}

	.page-title {
		font-size: 1.6rem;
		font-weight: 800;
		color: var(--text);
		margin: 0;
	}

	.intro,
	.muted,
	.meta {
		font-size: 0.85rem;
		color: var(--text-muted);
		margin: 0;
	}

	.controls,
	.result {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.ranges,
	.dates,
	.actions {
		display: flex;
		gap: 8px;
	}

	.field {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 4px;
		font-size: 0.78rem;
		font-weight: 600;
		color: var(--text-muted);
	}

	.input {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 9px 12px;
		font-family: inherit;
		font-size: 0.9rem;
		color: var(--text);
	}

	.pill,
	.secondary {
		padding: 8px 14px;
		border: 1px solid var(--border);
		border-radius: 18px;
		background: var(--bg-card);
		font-family: inherit;
		font-size: 0.82rem;
		color: var(--text);
		cursor: pointer;
	}

	.primary {
		padding: 11px 18px;
		border: none;
		border-radius: 20px;
		background: var(--text);
		color: var(--bg);
		font-family: inherit;
		font-size: 0.9rem;
		font-weight: 700;
		cursor: pointer;
	}

	.actions .primary,
	.actions .secondary {
		flex: 1;
	}

	button:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.error {
		color: #dc2626;
		font-size: 0.85rem;
		margin: 0;
	}

	h2 {
		font-size: 0.82rem;
		font-weight: 700;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.05em;
		margin: 8px 0 0;
	}

	table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.85rem;
	}

	th,
	td {
		text-align: left;
		padding: 6px 4px;
		border-bottom: 1px solid var(--border);
	}

	th {
		font-size: 0.75rem;
		color: var(--text-muted);
	}

	.list {
		margin: 0;
		padding-left: 18px;
		display: flex;
		flex-direction: column;
		gap: 6px;
		font-size: 0.88rem;
	}
</style>

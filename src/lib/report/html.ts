/**
 * The doctor report as one self-contained HTML document, printed through the
 * system print dialog (which also offers "Save as PDF"). Real text in any
 * script, no fonts or scripts to load. Every value from the diary is escaped.
 */
import type { MessageKey } from '$lib/i18n';
import { formatDuration, type Report } from './summary.js';

export interface HtmlOptions {
	t: (key: MessageKey, vars?: Record<string, string | number>) => string;
	/** A date for people, e.g. "14 Oct 2026". */
	date: (date: string) => string;
	number: (value: number, options?: Intl.NumberFormatOptions) => string;
	lang: string;
	dir: 'ltr' | 'rtl';
	/** When the report was made, already formatted. */
	created: string;
}

const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const escapeHtml = (value: unknown): string => String(value ?? '').replace(/[&<>"']/g, (c) => ESCAPES[c]);

function table(head: string[], rows: Array<Array<string | number | null>>, className = ''): string {
	const cell = (v: string | number | null) => `<td>${escapeHtml(v ?? '').replaceAll('\n', '<br>')}</td>`;
	return `<table class="${className}"><thead><tr>${head.map((h) => `<th>${escapeHtml(h)}</th>`).join('')}</tr></thead><tbody>${rows
		.map((row) => `<tr>${row.map(cell).join('')}</tr>`)
		.join('')}</tbody></table>`;
}

export function reportToHtml(report: Report, { t, date, number, lang, dir, created }: HtmlOptions): string {
	const one = (v: number | null) => (v === null ? '' : number(v, { maximumFractionDigits: 1 }));
	const percent = (v: number) => number(v, { style: 'percent', maximumFractionDigits: 0 });
	const section = (title: string, body: string) => `<section><h2>${escapeHtml(title)}</h2>${body}</section>`;
	const empty = (key: MessageKey) => `<p class="muted">${escapeHtml(t(key))}</p>`;

	const summary = report.types.length
		? table(
				[t('report_col_type'), t('report_col_days'), t('report_col_per30'), t('report_col_logs'), t('report_col_intensity'), t('report_col_duration')],
				report.types.map((s) => [
					s.name,
					s.days,
					one(s.per30),
					s.logs,
					s.avgIntensity === null ? '' : `${one(s.avgIntensity)} / 5`,
					formatDuration(s.avgDurationMinutes)
				])
			)
		: empty('report_empty');

	const links = report.types.flatMap((s) =>
		s.links.map((l) => [
			s.name,
			l.name,
			t(l.lag ? 'report_timing_next' : 'report_timing_same'),
			percent(l.withRate),
			percent(l.withoutRate),
			l.exposedDays,
			t(`insights_label_${l.label as 'likely' | 'possible' | 'fewer'}`)
		])
	);
	const linksBody = links.length
		? table(
				[t('report_col_type'), t('report_col_trigger'), t('report_col_timing'), t('report_col_with'), t('report_col_without'), t('report_col_seen'), t('report_col_assessment')],
				links
			) + `<p class="muted">${escapeHtml(t('report_links_note'))}</p>`
		: empty('report_no_triggers');

	const medications = report.medications.length
		? table(
				[t('report_col_medication'), t('report_col_dosage'), t('report_col_taken'), t('report_col_changes')],
				report.medications.map(({ medication: m, changes }) => [
					m.name,
					[m.dosage, m.unit].filter(Boolean).join(' '),
					t('report_period', { from: date(m.startDate), to: m.endDate ? date(m.endDate) : t('report_ongoing') }),
					changes
						.map((c) => `${date(c.changeDate)}: ${t(`report_change_${c.changeType}`, { old: c.oldDosage ?? '', new: c.newDosage ?? '' })}`)
						.join('\n')
				])
			)
		: empty('report_no_medications');

	const entries = report.rows.length
		? table(
				[t('report_col_date'), t('report_col_time'), t('report_col_type'), t('report_col_intensity_single'), t('report_col_details'), t('report_col_triggers'), t('report_col_note')],
				report.rows.map((r) => [date(r.date), [r.start, r.end].filter(Boolean).join(' – '), r.type, r.intensity === null ? '' : `${r.intensity}/5`, r.details, r.triggers, r.note]),
				'entries'
			)
		: empty('report_empty');

	const facts = [
		report.name ? `<div><dt>${escapeHtml(t('report_name'))}</dt><dd>${escapeHtml(report.name)}</dd></div>` : '',
		`<div><dt>${escapeHtml(t('report_period_label'))}</dt><dd>${escapeHtml(t('report_period', { from: date(report.from), to: date(report.to) }))}</dd></div>`,
		`<div><dt>${escapeHtml(t('report_tracked_days'))}</dt><dd>${escapeHtml(report.trackedDays)}</dd></div>`
	].join('');

	return `<!doctype html>
<html lang="${escapeHtml(lang)}" dir="${dir}">
<head>
<meta charset="utf-8">
<title>${escapeHtml(t('report_title'))}</title>
<style>
	@page { size: A4; margin: 16mm 14mm 18mm; }
	* { box-sizing: border-box; }
	body { margin: 0; font: 10pt/1.4 system-ui, -apple-system, 'Segoe UI', Roboto, 'Noto Sans', sans-serif; color: #1d1b19; }
	header { border-bottom: 2px solid #1d1b19; padding-bottom: 8pt; margin-bottom: 12pt; }
	h1 { font-size: 18pt; margin: 0 0 6pt; }
	dl { display: flex; flex-wrap: wrap; gap: 4pt 24pt; margin: 0; }
	dt { font-size: 8pt; color: #6b625a; text-transform: uppercase; letter-spacing: 0.04em; }
	dd { margin: 0; font-weight: 600; }
	h2 { font-size: 12pt; margin: 16pt 0 6pt; break-after: avoid; }
	table { width: 100%; border-collapse: collapse; font-size: 9pt; }
	th, td { text-align: start; vertical-align: top; padding: 3pt 5pt; border-bottom: 0.5pt solid #d8d2ca; }
	th { background: #efebe6; font-weight: 700; }
	tr { break-inside: avoid; }
	thead { display: table-header-group; }
	table.entries td:nth-child(1), table.entries td:nth-child(2) { white-space: nowrap; }
	.muted { color: #6b625a; font-size: 9pt; }
	@media screen { body { padding: 20px; min-width: 620px; } }
	footer { margin-top: 18pt; padding-top: 6pt; border-top: 0.5pt solid #d8d2ca; font-size: 8pt; color: #6b625a; }
</style>
</head>
<body>
<header>
<h1>${escapeHtml(t('report_title'))}</h1>
<dl>${facts}</dl>
</header>
${section(t('report_summary'), summary)}
${section(t('report_triggers'), linksBody)}
${section(t('report_medications'), medications)}
${section(t('report_entries'), entries)}
<footer>${escapeHtml(t('report_disclaimer'))} ${escapeHtml(t('report_created', { date: created }))}</footer>
</body>
</html>`;
}

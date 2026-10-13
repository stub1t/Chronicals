import { jsPDF } from 'jspdf';
import { autoTable, type UserOptions } from 'jspdf-autotable';
import { formatDuration, type Report } from './summary.js';

export interface PdfText {
	/** The app's `t()`; the report keys live in `$lib/i18n`. */
	t: (key: never, vars?: Record<string, string | number>) => string;
	/** Long local date, e.g. "15 Sep 2026". */
	date: (isoDate: string) => string;
	/** Translate a built-in name stored in English. */
	translate: (text: string) => string;
}

const MARGIN = 14;
const GREY: [number, number, number] = [110, 110, 110];

const PLAIN: Record<string, string> = {
	'\u2018': "'", '\u2019': "'", '\u201C': '"', '\u201D': '"', '\u2013': '-', '\u2014': '-',
	'\u2026': '...', '\u2192': '->', '\u00A0': ' ', '\u2009': ' ', '\u202F': ' '
};

/** Text the built-in PDF font can show: common typography mapped, anything else outside Latin-1 as "?". */
export function latin1(text: string): string {
	return text.replace(/[^\x00-\xFF]|\u00A0/gu, (ch) => PLAIN[ch] ?? '?');
}

const cells = (rows: Array<Array<string | number>>) =>
	rows.map((row) => row.map((cell) => (typeof cell === 'string' ? latin1(cell) : cell)));

/**
 * A4 report for a doctor. jsPDF's built-in Helvetica only covers Latin-1, so
 * the text avoids arrows and other symbols outside it.
 */
export function reportToPdf(report: Report, text: PdfText): Blob {
	const t = text.t as (key: string, vars?: Record<string, string | number>) => string;
	const doc = new jsPDF({ unit: 'mm', format: 'a4' });
	const width = doc.internal.pageSize.getWidth();
	let y = MARGIN + 4;

	const heading = (title: string) => {
		if (y > 260) {
			doc.addPage();
			y = MARGIN + 4;
		}
		doc.setFont('helvetica', 'bold').setFontSize(12).setTextColor(0);
		doc.text(latin1(title), MARGIN, y);
		y += 3;
	};
	const paragraph = (line: string) => {
		doc.setFont('helvetica', 'normal').setFontSize(9).setTextColor(...GREY);
		doc.text(latin1(line), MARGIN, y + 3);
		y += 9;
	};
	const table = (options: UserOptions & { head: string[][]; body: Array<Array<string | number>> }) => {
		autoTable(doc, {
			startY: y,
			margin: { left: MARGIN, right: MARGIN, bottom: 18 },
			styles: { font: 'helvetica', fontSize: 8, cellPadding: 1.5, overflow: 'linebreak' },
			headStyles: { fillColor: [38, 38, 35], textColor: 255, fontStyle: 'bold' },
			alternateRowStyles: { fillColor: [245, 245, 243] },
			...options,
			head: cells(options.head),
			body: cells(options.body)
		});
		y = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 8;
	};
	const strength = (value: string) => t(value === 'strong' ? 'report_strength_strong' : 'report_strength_possible');

	// Title
	doc.setFont('helvetica', 'bold').setFontSize(18).setTextColor(0);
	doc.text(latin1(t('report_title')), MARGIN, y);
	y += 7;
	doc.setFont('helvetica', 'normal').setFontSize(10).setTextColor(...GREY);
	const subtitle = [
		report.name,
		t('report_period', { from: text.date(report.from), to: text.date(report.to) }),
		t('report_days_with_entries', { count: report.daysWithEntries })
	].filter(Boolean);
	doc.text(latin1(subtitle.join('   |   ')), MARGIN, y);
	y += 10;

	// Summary per entry type
	heading(t('report_summary'));
	if (report.types.length === 0) {
		paragraph(t('report_empty'));
	} else {
		table({
			head: [[t('report_col_type'), t('report_col_days'), t('report_col_logs'), t('report_col_intensity'), t('report_col_duration')]],
			body: report.types.map((s) => [
				s.name,
				s.days,
				s.logs,
				s.avgIntensity === null ? '' : `${s.avgIntensity.toFixed(1)} / 5`,
				formatDuration(s.avgDurationMinutes)
			])
		});
	}

	// Possible triggers per symptom
	heading(t('report_triggers'));
	const associations = report.types.flatMap((s) => [
		...s.triggers.map((c) => [s.name, text.translate(c.triggerName), `${c.coOccurrences} / ${c.daysWithSymptom}`, `${strength(c.strength)} (${c.phi.toFixed(2)})`]),
		...s.environmental.map((c) => [s.name, t(c.variable === 'uv_index' ? 'report_uv_index' : 'report_pressure_drop'), '', `${strength(c.strength)} (${c.phi.toFixed(2)})`])
	]);
	if (associations.length === 0) {
		paragraph(t('report_no_triggers'));
	} else {
		table({
			head: [[t('report_col_type'), t('report_col_trigger'), t('report_col_shared'), t('report_col_strength')]],
			body: associations
		});
	}

	// Medications
	heading(t('report_medications'));
	if (report.medications.length === 0) {
		paragraph(t('report_no_medications'));
	} else {
		table({
			head: [[t('report_col_medication'), t('report_col_dosage'), t('report_col_taken'), t('report_col_changes')]],
			body: report.medications.map(({ medication: m, changes }) => [
				m.name,
				[m.dosage, m.unit].filter(Boolean).join(' '),
				t('report_period', { from: text.date(m.startDate), to: m.endDate ? text.date(m.endDate) : t('report_ongoing') }),
				changes
					.map((c) =>
						`${text.date(c.changeDate)}: ${t(`report_change_${c.changeType}`, { old: c.oldDosage ?? '', new: c.newDosage ?? '' })}`
					)
					.join('\n')
			])
		});
	}

	// Every entry
	heading(t('report_entries'));
	if (report.rows.length === 0) {
		paragraph(t('report_empty'));
	} else {
		table({
			head: [[t('report_col_date'), t('report_col_time'), t('report_col_type'), t('report_col_intensity_single'), t('report_col_details'), t('report_col_triggers'), t('report_col_note')]],
			body: report.rows.map((r) => [
				text.date(r.date),
				[r.start, r.end].filter(Boolean).join(' - '),
				r.type,
				r.intensity ?? '',
				r.details,
				r.triggers,
				r.note
			]),
			// Fixed widths for short columns; Details takes what is left (A4 minus margins is 182 mm).
			columnStyles: {
				0: { cellWidth: 22 },
				1: { cellWidth: 16 },
				2: { cellWidth: 31 },
				3: { cellWidth: 16 },
				5: { cellWidth: 27 },
				6: { cellWidth: 27 }
			}
		});
	}

	// Footer on every page
	const pages = doc.getNumberOfPages();
	for (let page = 1; page <= pages; page++) {
		doc.setPage(page);
		doc.setFont('helvetica', 'normal').setFontSize(7).setTextColor(...GREY);
		const footer = doc.splitTextToSize(latin1(t('report_disclaimer')), width - 2 * MARGIN - 20);
		doc.text(footer, MARGIN, 287);
		doc.text(`${page} / ${pages}`, width - MARGIN, 287, { align: 'right' });
	}

	return doc.output('blob');
}

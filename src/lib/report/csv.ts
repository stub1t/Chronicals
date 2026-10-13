import type { Report } from './summary.js';

export interface CsvOptions {
	/** ';' for German Excel, ',' elsewhere. */
	separator: ';' | ',';
	/** Column headings in this order: date, type, start, end, duration, intensity, details, triggers, note. */
	headings: string[];
}

/**
 * A cell as text. Cells that a spreadsheet would treat as a formula get a
 * leading apostrophe, so opening the file can't run anything.
 */
function cell(value: string | number | null, separator: string): string {
	if (value === null) return '';
	let text = String(value);
	if (typeof value === 'string' && /^[=+\-@\t\r]/.test(text)) text = `'${text}`;
	return text.includes(separator) || /["\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

/** One row per log (days with only a note or triggers get one row), UTF-8 with BOM for Excel. */
export function reportToCsv(report: Report, { separator, headings }: CsvOptions): string {
	const lines = [headings, ...report.rows.map((r) => [
		r.date,
		r.type,
		r.start,
		r.end,
		r.durationMinutes,
		r.intensity,
		r.details,
		r.triggers,
		r.note
	])].map((row) => row.map((value) => cell(value, separator)).join(separator));
	return `﻿${lines.join('\r\n')}\r\n`;
}

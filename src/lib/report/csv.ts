import type { Report } from './summary.js';

export interface CsvOptions {
	/** ';' where the decimal mark is a comma, ',' elsewhere. */
	separator: ';' | ',';
	/**
	 * Column headings in this order: date, well-being of the day, triggers of the
	 * day, note of the day, place, type, start, end, duration (minutes),
	 * intensity, scale, details, taken.
	 */
	headings: string[];
	/** Well-being 1 to 5 as a word. */
	wellbeing: (value: number) => string;
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

/** Local 24-hour "HH:MM", whatever the clock setting: spreadsheets sort and read it. */
function clock(iso: string | null): string {
	if (!iso) return '';
	const d = new Date(iso);
	return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/**
 * One row per entry, with the day's well-being, triggers, note and place
 * repeated on each (they belong to the day, not the entry); a day with no
 * entries gets one row. UTF-8 with BOM for Excel.
 */
export function reportToCsv(report: Report, { separator, headings, wellbeing }: CsvOptions): string {
	const rows = report.days.flatMap((day) => {
		const dayCells = [day.date, day.wellbeing ? wellbeing(day.wellbeing) : '', day.triggers.join(', '), day.note, day.place ?? ''];
		if (!day.entries.length) return [[...dayCells, '', '', '', null, null, null, '', '']];
		return day.entries.map((e) => [...dayCells, e.type, clock(e.startTime), clock(e.endTime), e.durationMinutes, e.intensity, e.intensity === null ? null : 5, e.details, e.taken]);
	});
	const lines = [headings, ...rows].map((row) => row.map((value) => cell(value, separator)).join(separator));
	return `﻿${lines.join('\r\n')}\r\n`;
}

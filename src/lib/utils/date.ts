import { dateLocale, localeStore } from '$lib/i18n';

function currentLocale(): string {
	return dateLocale(localeStore.current);
}

export function toYearMonth(date: Date): string {
	return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** 'YYYY-MM-DD' of the date in local time (toISOString would shift it to UTC). */
export function toDateString(date: Date): string {
	return `${toYearMonth(date)}-${String(date.getDate()).padStart(2, '0')}`;
}

export function formatMonthLabel(yearMonth: string): string {
	const [year, month] = yearMonth.split('-');
	const d = new Date(Number(year), Number(month) - 1, 1);
	return d.toLocaleDateString(currentLocale(), { month: 'long', year: 'numeric' });
}

export function getDaysInMonth(yearMonth: string): number {
	const [year, month] = yearMonth.split('-').map(Number);
	return new Date(year, month, 0).getDate();
}

export function getFirstDayOfMonth(yearMonth: string): number {
	// 0=Sun, 1=Mon ... convert to Mon-start: Mon=0 ... Sun=6
	const [year, month] = yearMonth.split('-').map(Number);
	const day = new Date(year, month - 1, 1).getDay();
	return (day + 6) % 7;
}

export function prevMonth(yearMonth: string): string {
	const [year, month] = yearMonth.split('-').map(Number);
	const d = new Date(year, month - 2, 1);
	return toYearMonth(d);
}

export function nextMonth(yearMonth: string): string {
	const [year, month] = yearMonth.split('-').map(Number);
	const d = new Date(year, month, 1);
	return toYearMonth(d);
}

export function getWeekdayLabels(): string[] {
	return Array.from({ length: 7 }, (_, i) =>
		new Date(2024, 0, 1 + i).toLocaleDateString(currentLocale(), { weekday: 'short' })
	);
}

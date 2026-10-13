import { dateLocale, localeStore } from '$lib/stores/locale.js';

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

export function formatTime(isoString: string): string {
	return new Date(isoString).toLocaleTimeString(currentLocale(), {
		hour: '2-digit',
		minute: '2-digit'
	});
}

export function formatDuration(startIso: string, endIso: string | null): string {
	if (!endIso) return localeStore.current === 'en' ? 'ongoing' : 'laeuft';
	const mins = Math.round((new Date(endIso).getTime() - new Date(startIso).getTime()) / 60000);
	if (mins < 60) return `${mins}m`;
	const h = Math.floor(mins / 60);
	const m = mins % 60;
	return m ? `${h}h ${m}m` : `${h}h`;
}

export function formatFullDate(dateString: string): string {
	const d = new Date(`${dateString}T00:00:00`);
	return d.toLocaleDateString(currentLocale(), {
		weekday: 'long',
		day: 'numeric',
		month: 'long'
	});
}

export function formatDayMonth(dateString: string): string {
	const d = new Date(`${dateString}T00:00:00`);
	return d.toLocaleDateString(currentLocale(), {
		day: 'numeric',
		month: 'short'
	});
}

export function formatDayMonthYear(dateString: string): string {
	const d = new Date(`${dateString}T00:00:00`);
	return d.toLocaleDateString(currentLocale(), {
		day: 'numeric',
		month: 'short',
		year: 'numeric'
	});
}

export function getWeekdayLabels(): string[] {
	return Array.from({ length: 7 }, (_, i) =>
		new Date(2024, 0, 1 + i).toLocaleDateString(currentLocale(), { weekday: 'short' })
	);
}

/** Intensity 1-5 → opacity 0.2-1.0 */
export function intensityToOpacity(intensity: number): number {
	return 0.2 + ((intensity - 1) / 4) * 0.8;
}

/** Intensity 1-5 → rgba color string */
export function intensityColor(hexColor: string, intensity: number): string {
	const opacity = intensityToOpacity(Math.max(1, Math.min(5, intensity)));
	const r = parseInt(hexColor.slice(1, 3), 16);
	const g = parseInt(hexColor.slice(3, 5), 16);
	const b = parseInt(hexColor.slice(5, 7), 16);
	return `rgba(${r}, ${g}, ${b}, ${opacity})`;
}

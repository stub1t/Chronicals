/** Calendar arithmetic on 'YYYY-MM-DD' strings, done in UTC so time zones and DST can't shift a day. */

const DAY = 86_400_000;

const toUtc = (date: string) => Date.UTC(+date.slice(0, 4), +date.slice(5, 7) - 1, +date.slice(8, 10));
const fromUtc = (ms: number) => new Date(ms).toISOString().slice(0, 10);

export function addDays(date: string, days: number): string {
	return fromUtc(toUtc(date) + days * DAY);
}

/** Whole days from `a` to `b` (positive when b is later). */
export function daysBetween(a: string, b: string): number {
	return Math.round((toUtc(b) - toUtc(a)) / DAY);
}

/** 0 = Monday ... 6 = Sunday. */
export function weekdayIndex(date: string): number {
	return (new Date(toUtc(date)).getUTCDay() + 6) % 7;
}

/** The Monday of the week that contains `date`. */
export function weekStart(date: string): string {
	return addDays(date, -weekdayIndex(date));
}

/** Every date from `from` to `to`, both included. */
export function eachDay(from: string, to: string): string[] {
	const days: string[] = [];
	for (let date = from; date <= to; date = addDays(date, 1)) days.push(date);
	return days;
}

/**
 * The first day of a period of `months` months ending on `today`:
 * the same day of the month `months` earlier, plus one. A day that the
 * earlier month does not have (31 May, 3 months back) becomes its last day.
 */
export function monthsBefore(today: string, months: number): string {
	const year = +today.slice(0, 4);
	const month = +today.slice(5, 7) - 1 - months;
	const target = new Date(Date.UTC(year, month, 1));
	const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
	const day = Math.min(+today.slice(8, 10), lastDay);
	return addDays(`${target.toISOString().slice(0, 8)}${String(day).padStart(2, '0')}`, 1);
}

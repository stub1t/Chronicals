import { describe, expect, it } from 'vitest';
import { nextMonth, prevMonth, toDateString, toYearMonth } from './date.js';

// `npm test` runs with TZ=Pacific/Auckland: converting to UTC there moves
// early-morning times to the previous day, which is the bug these guard against.
describe('toDateString', () => {
	it('formats the local calendar date', () => {
		expect(toDateString(new Date(2026, 2, 29, 0, 30))).toBe('2026-03-29');
		expect(toDateString(new Date(2026, 0, 1, 0, 0))).toBe('2026-01-01');
	});

	it('steps over the daylight saving switch one calendar day at a time', () => {
		const d = new Date(2026, 3, 4); // New Zealand leaves DST on 5 April 2026
		const days = Array.from({ length: 3 }, () => {
			const s = toDateString(d);
			d.setDate(d.getDate() + 1);
			return s;
		});
		expect(days).toEqual(['2026-04-04', '2026-04-05', '2026-04-06']);
	});
});

describe('month helpers', () => {
	it('formats and steps months', () => {
		expect(toYearMonth(new Date(2026, 9, 31, 23, 59))).toBe('2026-10');
		expect(prevMonth('2026-01')).toBe('2025-12');
		expect(nextMonth('2026-12')).toBe('2027-01');
	});
});

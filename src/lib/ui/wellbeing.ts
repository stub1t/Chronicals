/** Colours of the daily well-being steps, from very bad (1) to very good (5). */
export const WELLBEING_COLORS = ['#e57d90', '#ef9f66', '#e2b94f', '#86c98f', '#4fb487'] as const;

export const wellbeingColor = (value: number | null | undefined): string | null =>
	value ? (WELLBEING_COLORS[value - 1] ?? null) : null;

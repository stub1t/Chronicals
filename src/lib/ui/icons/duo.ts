/**
 * The drawn icons of the built-in entry types: a soft fill (`fill`, drawn at 22%) under 2 px round
 * strokes (`stroke`), with optional solid details (`solid`). 24 × 24 grid, like Lucide.
 */
const HEAD = 'M7 21.5v-3.2C5.2 17 4 14.9 4 12.5 4 8.6 7.1 5.5 11 5.5c3.3 0 6 2.2 6.7 5.3l1.2 2.9c.2.4-.1.8-.5.8h-1v2.1a1.8 1.8 0 0 1-1.8 1.8h-1.9v3.1';
const STOMACH = 'M8.5 3v3.8c0 1.4 1 2.2 2.4 2.2 4.4 0 7.7 2.9 7.7 6.7 0 3.4-2.9 5.8-6.8 5.8-3.3 0-6.1-1.5-6.1-4 0-1.8 1.3-2.8 3-2.8 1.6 0 2.4-.9 2.4-2.2';
const DROP = 'M12 2.5S5.5 9.3 5.5 14.4a6.5 6.5 0 0 0 13 0C18.5 9.3 12 2.5 12 2.5z';
const FLOWER = 'M12 3.2a3.3 3.3 0 0 1 3.1 4.4 3.3 3.3 0 0 1 1 6.4 3.3 3.3 0 0 1-4.1 2.6 3.3 3.3 0 0 1-4.1-2.6 3.3 3.3 0 0 1 1-6.4A3.3 3.3 0 0 1 12 3.2z';

export const DUO_ICONS = {
	// Head in profile and stomach share the same three short pain rays.
	headache: {
		fill: `<path d="${HEAD}z"/>`,
		stroke: `<path d="${HEAD}"/><path d="M13 2.2v1.4M17.4 3.2l-1 1.2M20.6 6.6l-1.4.8"/>`
	},
	stomachache: {
		fill: '<path d="M8.5 6.8c0 1.4 1 2.2 2.4 2.2 4.4 0 7.7 2.9 7.7 6.7 0 3.4-2.9 5.8-6.8 5.8-3.3 0-6.1-1.5-6.1-4 0-1.8 1.3-2.8 3-2.8 1.6 0 2.4-.9 2.4-2.2z"/>',
		stroke: `<path d="${STOMACH}"/><path d="M5.9 17.7 3 18.6"/><path d="M17 2.4v1.6M20.3 4.1l-1.1 1.1M21.6 7.8H20"/>`
	},
	// A flower with pollen in the air.
	allergy: {
		fill: `<path d="${FLOWER}"/>`,
		stroke: `<path d="${FLOWER}"/><path d="M12 16.6v5.4M12 20c-1.3-1.4-3-1.8-4.6-1.4M12 20c1.3-1.4 3-1.8 4.6-1.4"/>`,
		solid: '<circle cx="12" cy="10.2" r="2"/><circle cx="20" cy="3.6" r="1.2"/><circle cx="3.8" cy="5" r="1.2"/><circle cx="20.6" cy="16" r="1.2"/>'
	},
	// A drop with an arrow pointing down: low blood sugar.
	'blood-sugar': {
		fill: `<path d="${DROP}"/>`,
		stroke: `<path d="${DROP}"/>`,
		solid: '<path d="M10.3 9.6h3.4v4h2.7L12 19l-4.4-5.4h2.7z"/>'
	},
	meal: {
		fill: '<circle cx="12" cy="12" r="5.3"/>',
		stroke: '<circle cx="12" cy="12" r="5.3"/><path d="M3 3v5a1.6 1.6 0 0 0 3.2 0V3M4.6 3v18"/><path d="M21 15h-2.6V8.5C18.4 5.3 19.8 3.3 21 3v18"/>'
	},
	medication: {
		fill: '<path d="M13.8 4.6a4.9 4.9 0 0 1 6.9 6.9l-3.6 3.6-6.9-6.9z"/>',
		stroke: '<path d="M10.2 8.2 13.8 4.6a4.9 4.9 0 0 1 6.9 6.9l-9.2 9.2a4.9 4.9 0 0 1-6.9-6.9z"/><path d="M10.2 8.2l6.9 6.9"/>',
		solid: '<path d="M10.2 8.2l-5.6 5.6a4.9 4.9 0 0 0 6.9 6.9l5.6-5.6z" opacity=".55"/>'
	},
	period: {
		fill: '<path d="M10.5 4S4.5 10.6 4.5 15a6 6 0 0 0 12 0c0-4.4-6-11-6-11z"/>',
		stroke: '<path d="M10.5 4S4.5 10.6 4.5 15a6 6 0 0 0 12 0c0-4.4-6-11-6-11z"/><path d="M7.7 15.4a2.8 2.8 0 0 0 2.8 2.8"/>',
		solid: '<path d="M18.6 2s-2.6 2.8-2.6 4.8a2.6 2.6 0 0 0 5.2 0c0-2-2.6-4.8-2.6-4.8z"/>'
	},
	note: {
		fill: '<path d="M6 2.5h8.5L19 7v13a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 20V4a1.5 1.5 0 0 1 1-1.5z"/>',
		stroke: '<path d="M14.5 2.5H6.5A1.5 1.5 0 0 0 5 4v16a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 20V7z"/><path d="M14.5 2.5V7H19"/><path d="M8.5 12h7M8.5 15.5h7M8.5 19h4"/>'
	}
} as const;

export type DuoIconName = keyof typeof DUO_ICONS;

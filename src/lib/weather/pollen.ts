/** Pollen species from Open-Meteo's air-quality API (Europe), and when a daily peak counts as moderate or high. */
export const POLLEN = ['alder', 'birch', 'grass', 'mugwort', 'olive', 'ragweed'] as const;
export type PollenSpecies = (typeof POLLEN)[number];
export type PollenLevel = 'low' | 'moderate' | 'high';

// Grains per m³ (daily peak), following the usual European bands per plant group.
const BANDS: Record<PollenSpecies, [moderate: number, high: number]> = {
	alder: [10, 100],
	birch: [10, 100],
	olive: [10, 100],
	grass: [10, 50],
	mugwort: [5, 20],
	ragweed: [5, 20]
};

export function pollenLevel(species: PollenSpecies, value: number | null | undefined): PollenLevel | null {
	if (value == null) return null;
	const [moderate, high] = BANDS[species];
	return value >= high ? 'high' : value >= moderate ? 'moderate' : 'low';
}

/** The data field of each species on EnvironmentalData. */
export const POLLEN_FIELD = {
	alder: 'pollenAlder',
	birch: 'pollenBirch',
	grass: 'pollenGrass',
	mugwort: 'pollenMugwort',
	olive: 'pollenOlive',
	ragweed: 'pollenRagweed'
} as const satisfies Record<PollenSpecies, string>;

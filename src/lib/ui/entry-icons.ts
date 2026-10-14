import type { Component } from 'svelte';
import Activity from '@lucide/svelte/icons/activity';
import Apple from '@lucide/svelte/icons/apple';
import Baby from '@lucide/svelte/icons/baby';
import Bandage from '@lucide/svelte/icons/bandage';
import Bed from '@lucide/svelte/icons/bed';
import Bone from '@lucide/svelte/icons/bone';
import Brain from '@lucide/svelte/icons/brain';
import Candy from '@lucide/svelte/icons/candy';
import CloudRain from '@lucide/svelte/icons/cloud-rain';
import Coffee from '@lucide/svelte/icons/coffee';
import Droplet from '@lucide/svelte/icons/droplet';
import Droplets from '@lucide/svelte/icons/droplets';
import Dumbbell from '@lucide/svelte/icons/dumbbell';
import Ear from '@lucide/svelte/icons/ear';
import Eye from '@lucide/svelte/icons/eye';
import Flame from '@lucide/svelte/icons/flame';
import Flower2 from '@lucide/svelte/icons/flower-2';
import Footprints from '@lucide/svelte/icons/footprints';
import Hand from '@lucide/svelte/icons/hand';
import HeartPulse from '@lucide/svelte/icons/heart-pulse';
import Leaf from '@lucide/svelte/icons/leaf';
import Moon from '@lucide/svelte/icons/moon';
import NotebookPen from '@lucide/svelte/icons/notebook-pen';
import Pill from '@lucide/svelte/icons/pill';
import Snowflake from '@lucide/svelte/icons/snowflake';
import Sparkles from '@lucide/svelte/icons/sparkles';
import Stethoscope from '@lucide/svelte/icons/stethoscope';
import Sun from '@lucide/svelte/icons/sun';
import Syringe from '@lucide/svelte/icons/syringe';
import Thermometer from '@lucide/svelte/icons/thermometer';
import Utensils from '@lucide/svelte/icons/utensils';
import Wind from '@lucide/svelte/icons/wind';
import Wine from '@lucide/svelte/icons/wine';
import Zap from '@lucide/svelte/icons/zap';
import Stomach from './icons/Stomach.svelte';
import type { DuoIconName } from './icons/duo.js';

type IconComponent = Component<{ size?: number; strokeWidth?: number; color?: string }>;

/** Icons offered for custom entry types; stored in the database as `lucide:<key>`. */
export const ENTRY_ICONS: Record<string, IconComponent> = {
	activity: Activity,
	'heart-pulse': HeartPulse,
	brain: Brain,
	stomach: Stomach,
	eye: Eye,
	ear: Ear,
	bone: Bone,
	hand: Hand,
	thermometer: Thermometer,
	flame: Flame,
	zap: Zap,
	wind: Wind,
	'flower-2': Flower2,
	leaf: Leaf,
	sun: Sun,
	'cloud-rain': CloudRain,
	snowflake: Snowflake,
	moon: Moon,
	bed: Bed,
	droplet: Droplet,
	droplets: Droplets,
	candy: Candy,
	pill: Pill,
	syringe: Syringe,
	stethoscope: Stethoscope,
	bandage: Bandage,
	apple: Apple,
	utensils: Utensils,
	coffee: Coffee,
	wine: Wine,
	dumbbell: Dumbbell,
	footprints: Footprints,
	baby: Baby,
	sparkles: Sparkles,
	'notebook-pen': NotebookPen
};

/** Drawn icons of the built-in entry types (see icons/duo.ts). */
export const BUILT_IN_ICONS: Record<string, DuoIconName> = {
	'builtin-headache': 'headache',
	'builtin-stomachache': 'stomachache',
	'builtin-allergy': 'allergy',
	'builtin-hypo': 'low-blood-sugar',
	'builtin-period': 'period',
	'builtin-note': 'note'
};

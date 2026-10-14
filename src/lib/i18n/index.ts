/**
 * Translations for 15 languages. English is bundled; every other language is
 * a separate chunk loaded on demand, so only the active one is downloaded.
 * The language is ready before the first render (root +layout.ts), so t()
 * can stay synchronous.
 */
import { get, writable } from 'svelte/store';
import en from './locales/en.js';
import type { LanguageCode, MessageKey, Messages, Plural } from './types.js';

export type { LanguageCode, MessageKey, Messages } from './types.js';

export interface LanguageInfo {
	code: LanguageCode;
	/** Name in the language itself, for the picker. */
	name: string;
	/** BCP 47 locale for dates and numbers. */
	locale: string;
	dir: 'ltr' | 'rtl';
}

const ALL_LANGUAGES: LanguageInfo[] = [
	{ code: 'en', name: 'English', locale: 'en-US', dir: 'ltr' },
	{ code: 'zh', name: '简体中文', locale: 'zh-CN', dir: 'ltr' },
	{ code: 'hi', name: 'हिन्दी', locale: 'hi-IN', dir: 'ltr' },
	{ code: 'es', name: 'Español', locale: 'es-ES', dir: 'ltr' },
	{ code: 'ar', name: 'العربية', locale: 'ar', dir: 'rtl' },
	{ code: 'fr', name: 'Français', locale: 'fr-FR', dir: 'ltr' },
	{ code: 'bn', name: 'বাংলা', locale: 'bn-BD', dir: 'ltr' },
	{ code: 'pt', name: 'Português', locale: 'pt-BR', dir: 'ltr' },
	{ code: 'ru', name: 'Русский', locale: 'ru-RU', dir: 'ltr' },
	{ code: 'ur', name: 'اردو', locale: 'ur-PK', dir: 'rtl' },
	{ code: 'id', name: 'Bahasa Indonesia', locale: 'id-ID', dir: 'ltr' },
	{ code: 'de', name: 'Deutsch', locale: 'de-AT', dir: 'ltr' },
	{ code: 'ja', name: '日本語', locale: 'ja-JP', dir: 'ltr' },
	{ code: 'tr', name: 'Türkçe', locale: 'tr-TR', dir: 'ltr' },
	{ code: 'ko', name: '한국어', locale: 'ko-KR', dir: 'ltr' }
];

const loaders = import.meta.glob<{ default: Messages }>(['./locales/*.ts', '!./locales/en.ts']);
const loaded: Partial<Record<LanguageCode, Messages>> = { en };

/** Languages that have a translation file, in the order of ALL_LANGUAGES. */
export const LANGUAGES: LanguageInfo[] = ALL_LANGUAGES.filter(
	(l) => l.code === 'en' || `./locales/${l.code}.ts` in loaders
);

const STORAGE_KEY = 'language';

export const language = writable<LanguageCode>('en');

function info(code: LanguageCode): LanguageInfo {
	return LANGUAGES.find((l) => l.code === code) ?? LANGUAGES[0];
}

function messages(): Messages {
	return loaded[get(language)] ?? en;
}

async function load(code: LanguageCode): Promise<Messages> {
	if (!loaded[code]) {
		const loader = loaders[`./locales/${code}.ts`];
		loaded[code] = loader ? (await loader()).default : en;
	}
	return loaded[code]!;
}

/** The stored choice, else the first device language we have, else English. */
function preferredLanguage(): LanguageCode {
	const supported = new Set(LANGUAGES.map((l) => l.code));
	try {
		const stored = localStorage.getItem(STORAGE_KEY) as LanguageCode | null;
		if (stored && supported.has(stored)) return stored;
	} catch {
		// No storage: fall through to the device language.
	}
	for (const tag of navigator.languages ?? [navigator.language]) {
		const code = tag.toLowerCase().split('-')[0] as LanguageCode;
		if (supported.has(code)) return code;
	}
	return 'en';
}

function apply(code: LanguageCode) {
	const { dir } = info(code);
	document.documentElement.lang = code;
	document.documentElement.dir = dir;
}

/** Load the preferred language before the first render. */
export async function initLanguage(): Promise<void> {
	if (typeof window === 'undefined') return;
	const code = preferredLanguage();
	await load(code);
	language.set(code);
	apply(code);
}

/** Switch language (loads it first, so the whole UI changes at once). */
export async function setLanguage(code: LanguageCode): Promise<void> {
	await load(code);
	try {
		localStorage.setItem(STORAGE_KEY, code);
	} catch {
		// Not remembered without storage.
	}
	language.set(code);
	apply(code);
}

function fill(template: string, vars?: Record<string, string | number>): string {
	if (!vars) return template;
	return Object.entries(vars).reduce(
		(text: string, [name, value]) => text.replaceAll(`{${name}}`, typeof value === 'number' ? formatNumber(value) : value),
		template
	);
}

/** Translate a key. Plural messages pick their form from `vars.count`. */
export function t(key: MessageKey, vars?: Record<string, string | number>): string {
	const message = (messages()[key] ?? en[key]) as string | Plural;
	if (typeof message === 'string') return fill(message, vars);
	const count = Number(vars?.count ?? 0);
	const form = new Intl.PluralRules(dateLocale()).select(count) as keyof Plural;
	return fill(message[form] ?? message.other, vars);
}

/** Translate a built-in name, option or trigger stored in English; other text is returned as is. */
export function localizeBuiltInText(text: string | null | undefined): string {
	if (!text) return text ?? '';
	const table = messages().builtIn as Record<string, string>;
	return table[text] ?? text;
}

/**
 * BCP 47 locale for dates and numbers: the device's own locale when it is in
 * the app's language (so an English phone in Austria gets "14 Oct" and 24-hour
 * times), otherwise the language's default.
 */
export function dateLocale(code: LanguageCode = get(language)): string {
	if (typeof navigator !== 'undefined') {
		const device = (navigator.languages?.length ? navigator.languages : [navigator.language]).find(
			(tag) => tag?.split('-')[0].toLowerCase() === code
		);
		if (device && device.includes('-')) return device;
	}
	return info(code).locale;
}

export type TimeFormat = 'auto' | '24' | '12';
const TIME_FORMAT_KEY = 'time-format';

function storedTimeFormat(): TimeFormat {
	try {
		const value = localStorage.getItem(TIME_FORMAT_KEY);
		return value === '24' || value === '12' ? value : 'auto';
	} catch {
		return 'auto';
	}
}

/** 'auto' follows the locale above; '24' and '12' override it. */
export const timeFormat = writable<TimeFormat>(storedTimeFormat());

export function setTimeFormat(value: TimeFormat): void {
	try {
		localStorage.setItem(TIME_FORMAT_KEY, value);
	} catch {
		// Not remembered without storage.
	}
	timeFormat.set(value);
}

/** A clock time ("8:40", "08:40" or "8:40 AM") in the chosen format. */
export function formatTime(value: string | number | Date): string {
	const format = get(timeFormat);
	const options: Intl.DateTimeFormatOptions = { hour: 'numeric', minute: '2-digit' };
	if (format === '24') options.hourCycle = 'h23';
	if (format === '12') options.hourCycle = 'h12';
	return new Date(value).toLocaleTimeString(dateLocale(), options);
}

export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
	return new Intl.NumberFormat(dateLocale(), options).format(value);
}

export function isRtl(): boolean {
	return info(get(language)).dir === 'rtl';
}

/** Kept for the existing screens: current language and the setter. */
export const localeStore = {
	subscribe: language.subscribe,
	get current() {
		return get(language);
	},
	init: initLanguage,
	set: setLanguage
};

import type en from './locales/en.js';

/** Plural forms per Intl.PluralRules; "other" is always required. */
export type Plural = { other: string } & Partial<Record<'zero' | 'one' | 'two' | 'few' | 'many', string>>;

type Widen<T> = {
	[K in keyof T]: T[K] extends string ? string : T[K] extends { other: string } ? Plural : Widen<T[K]>;
};

/** Shape every language file must have: the keys of en.ts with any wording. */
export type Messages = Widen<typeof en>;

export type MessageKey = Exclude<keyof Messages, 'builtIn'>;

export type LanguageCode =
	| 'en'
	| 'zh'
	| 'hi'
	| 'es'
	| 'ar'
	| 'fr'
	| 'bn'
	| 'pt'
	| 'ru'
	| 'ur'
	| 'id'
	| 'de'
	| 'ja'
	| 'tr'
	| 'ko';

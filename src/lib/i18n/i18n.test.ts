import { describe, expect, it } from 'vitest';
import en from './locales/en.js';

const locales = import.meta.glob<{ default: Record<string, unknown> }>('./locales/*.ts', { eager: true });

const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
const forms = (value: unknown): string[] => (typeof value === 'string' ? [value] : Object.values(value as Record<string, string>));

describe.each(Object.entries(locales).filter(([path]) => !path.endsWith('/en.ts')))('%s', (_path, mod) => {
	const messages = mod.default;

	it('has exactly the keys of English', () => {
		expect(Object.keys(messages).sort()).toEqual(Object.keys(en).sort());
		expect(Object.keys(messages.builtIn as object).sort()).toEqual(Object.keys(en.builtIn).sort());
	});

	it('uses the same placeholders as English in every message and plural form', () => {
		for (const [key, value] of Object.entries(en)) {
			if (key === 'builtIn') continue;
			const expected = placeholders(forms(value)[0]);
			for (const form of forms(messages[key])) {
				expect(placeholders(form), `${key}: ${form}`).toEqual(expected);
			}
		}
	});

	it('gives every plural an "other" form and no empty texts', () => {
		for (const [key, value] of Object.entries(messages)) {
			if (key === 'builtIn') continue;
			if (typeof value !== 'string') expect(value, key).toHaveProperty('other');
			for (const form of forms(value)) expect(form.trim(), key).not.toBe('');
		}
	});
});

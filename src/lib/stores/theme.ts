import { browser } from '$app/environment';

type Theme = 'system' | 'light' | 'dark';

// Plain object — runes ($state) cannot be used in .ts files, only .svelte files.
// Theme state lives entirely in the browser (localStorage + classList), so no
// reactive store is needed; components read the DOM class directly.
let _current: Theme = 'system';

function apply(theme: Theme) {
	if (!browser) return;
	const html = document.documentElement;
	if (theme === 'dark') {
		html.classList.add('dark');
	} else if (theme === 'light') {
		html.classList.remove('dark');
	} else {
		const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
		html.classList.toggle('dark', prefersDark);
	}
}

function init() {
	if (!browser) return;
	const stored = (localStorage.getItem('theme') as Theme) ?? 'system';
	_current = stored;
	apply(stored);

	window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
		if (_current === 'system') apply('system');
	});
}

function set(theme: Theme) {
	_current = theme;
	if (browser) localStorage.setItem('theme', theme);
	apply(theme);
}

export const themeStore = {
	get current() { return _current; },
	init,
	set
};

import { execSync } from 'node:child_process';
import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { SvelteKitPWA } from '@vite-pwa/sveltekit';
import { defineConfig } from 'vitest/config';

// See svelte.config.js: the Android app is a static SPA with its own database.
const isMobile = process.env.CHRONICALS_TARGET === 'mobile';
const isTest = !!process.env.VITEST;

/** Release version from CI, otherwise "dev" plus the git commit when available. */
function appVersion(): string {
	if (process.env.CHRONICALS_VERSION_NAME) return process.env.CHRONICALS_VERSION_NAME;
	try {
		const commit = execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] });
		return `dev-${commit.toString().trim()}`;
	} catch {
		return 'dev';
	}
}

export default defineConfig({
	define: {
		__NATIVE_APP__: JSON.stringify(isMobile),
		__APP_VERSION__: JSON.stringify(appVersion())
	},
	plugins: [
		tailwindcss(),
		sveltekit(),
		!isMobile &&
			!isTest &&
			SvelteKitPWA({
				strategies: 'injectManifest',
				srcDir: 'src',
				filename: 'service-worker.ts',
				registerType: 'autoUpdate',
				manifest: {
					name: 'Chronicals',
					short_name: 'Chronicals',
					description: 'Symptom and trigger diary',
					theme_color: '#7462e8',
					background_color: '#fbf7f1',
					display: 'standalone',
					orientation: 'portrait',
					start_url: '/',
					icons: [
						{ src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
						{ src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
						{ src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
					]
				},
				injectManifest: {
					globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}']
				},
				devOptions: {
					enabled: false
				}
			})
	],
	test: {
		include: ['src/**/*.test.ts'],
		environment: 'node'
	}
});

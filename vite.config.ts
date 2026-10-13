import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { SvelteKitPWA } from '@vite-pwa/sveltekit';
import { defineConfig } from 'vitest/config';

// See svelte.config.js: the Android app is a static SPA with its own database.
const isMobile = process.env.CHRONICALS_TARGET === 'mobile';
const isTest = !!process.env.VITEST;

export default defineConfig({
	define: {
		__NATIVE_APP__: JSON.stringify(isMobile)
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
					description: 'Personal health diary & calendar',
					theme_color: '#0f0f0e',
					background_color: '#0f0f0e',
					display: 'standalone',
					orientation: 'portrait',
					start_url: '/calendar',
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

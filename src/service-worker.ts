/// <reference lib="webworker" />
/// <reference types="vite-plugin-pwa/vanillajs" />

import { cleanupOutdatedCaches, precacheAndRoute } from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';
import { CacheFirst, NetworkFirst } from 'workbox-strategies';

declare let self: ServiceWorkerGlobalScope;

// Injected by workbox-build at build time
precacheAndRoute(self.__WB_MANIFEST);

cleanupOutdatedCaches();

// API routes: network-first with 5s timeout. Backups are a copy of the whole
// diary and must not end up in the browser cache.
registerRoute(
	({ url }) => url.pathname.startsWith('/api/') && url.pathname !== '/api/backup',
	new NetworkFirst({
		networkTimeoutSeconds: 5,
		cacheName: 'chronicals-api'
	})
);

// Static assets: cache-first
registerRoute(
	({ request }) =>
		request.destination === 'script' ||
		request.destination === 'style' ||
		request.destination === 'font' ||
		request.destination === 'image',
	new CacheFirst({ cacheName: 'chronicals-assets' })
);

// Navigation: network-first for SSR pages
registerRoute(
	new NavigationRoute(
		new NetworkFirst({
			networkTimeoutSeconds: 5,
			cacheName: 'chronicals-pages'
		})
	)
);

self.addEventListener('message', (event) => {
	if (event.data && event.data.type === 'SKIP_WAITING') {
		self.skipWaiting();
	}
});

import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
	appId: 'io.github.stub1t.chronicals',
	appName: 'chronicals',
	webDir: 'build-mobile',
	backgroundColor: '#0f0f0e',
	plugins: {
		SystemBars: {
			// src/app.html sets viewport-fit=cover; the layout pads with env(safe-area-inset-*).
			initialViewportFitValueHint: 'cover'
		}
	}
};

export default config;

import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
	appId: 'io.github.stub1t.chronicals',
	appName: 'Chronicals',
	webDir: 'build-mobile',
	backgroundColor: '#fbf7f1',
	plugins: {
		SystemBars: {
			// src/app.html sets viewport-fit=cover; the layout pads with env(safe-area-inset-*).
			initialViewportFitValueHint: 'cover'
		}
	}
};

export default config;

import nodeAdapter from '@sveltejs/adapter-node';
import staticAdapter from '@sveltejs/adapter-static';

// CHRONICALS_TARGET=mobile builds the Android app: a static SPA (no server)
// that Capacitor bundles into the APK.
const isMobile = process.env.CHRONICALS_TARGET === 'mobile';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		adapter: isMobile
			? staticAdapter({ pages: 'build-mobile', assets: 'build-mobile', fallback: 'index.html' })
			: nodeAdapter(),
		serviceWorker: { register: !isMobile }
	}
};

export default config;

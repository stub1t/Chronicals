import { initLanguage } from '$lib/i18n';
import type { LayoutLoad } from './$types';

// Both the web app and the Android app render in the browser only: the
// language (and its right-to-left direction) is known before the first paint,
// and there is one code path. The server still serves the API and the files.
export const ssr = false;

export const load: LayoutLoad = async () => {
	await initLanguage();
};

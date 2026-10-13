import { redirect } from '@sveltejs/kit';
import type { PageLoad } from './$types';

// Analysis moved to Insights; old links and bookmarks keep working.
export const load: PageLoad = ({ params }) => {
	redirect(301, `/insights${params.rest ? `/${params.rest}` : ''}`);
};

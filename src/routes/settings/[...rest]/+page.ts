import { redirect } from '@sveltejs/kit';
import type { PageLoad } from './$types';

// Settings moved to Me; old links and bookmarks keep working.
export const load: PageLoad = ({ params }) => {
	redirect(301, `/me${params.rest ? `/${params.rest}` : ''}`);
};

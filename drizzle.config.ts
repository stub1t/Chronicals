import type { Config } from 'drizzle-kit';

export default {
	schema: './src/lib/db/schema.ts',
	out: './migrations',
	dialect: 'sqlite',
	dbCredentials: {
		url: process.env.DATABASE_PATH ?? './chronicals.db'
	}
} satisfies Config;

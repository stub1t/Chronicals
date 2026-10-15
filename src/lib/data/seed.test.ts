import { eq } from 'drizzle-orm';
import { describe, expect, it } from 'vitest';
import * as schema from '../db/schema.js';
import { useTestDatabase } from '$lib/server/testing.js';
import { seedBuiltIns } from './seed.js';

describe('seedBuiltIns', () => {
	it('turns a period\'s light, medium or heavy flow into the 1 to 5 scale', () => {
		const { db } = useTestDatabase();
		db.insert(schema.entries).values({ id: 'e1', date: '2026-09-20' }).run();
		const flows = { light: 2, medium: 3, heavy: 4 };
		for (const flow of Object.keys(flows)) {
			db.insert(schema.entryTypeLogs)
				.values({ id: flow, entryId: 'e1', entryTypeId: 'builtin-period', startTime: '2026-09-20T09:00:00.000Z', metricValues: JSON.stringify({ type: 'bleeding', flow }) })
				.run();
		}
		seedBuiltIns(db);
		seedBuiltIns(db);
		for (const [flow, level] of Object.entries(flows)) {
			const row = db.select().from(schema.entryTypeLogs).where(eq(schema.entryTypeLogs.id, flow)).get();
			expect(JSON.parse(row!.metricValues)).toEqual({ type: 'bleeding', flow: level });
		}
	});

	it('offers period flow as a scale and own period symptoms', () => {
		const { db } = useTestDatabase();
		const period = db.select().from(schema.entryTypes).where(eq(schema.entryTypes.id, 'builtin-period')).get();
		const fields = JSON.parse(period!.metricSchema).fields;
		expect(fields.find((f: { key: string }) => f.key === 'flow')).toMatchObject({ type: 'slider', config: { min: 1, max: 5 } });
		expect(fields.find((f: { key: string }) => f.key === 'symptoms').config.extendable).toBe(true);
	});
});

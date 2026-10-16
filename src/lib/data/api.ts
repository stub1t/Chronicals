/**
 * The `/api/*` surface as one route table.
 *
 * Served by `src/routes/api/[...path]/+server.ts` on the web, and called
 * in-process by `$lib/mobile/local-api` inside the Android app. Handlers must
 * stay platform-neutral: no Node APIs, and outbound HTTP only via `event.fetch`
 * or global `fetch`.
 */
import { error, json } from '@sveltejs/kit';
import {
	getEntry,
	getEntryByDate,
	getEntriesInRange,
	getMonthEntries,
	createEntry,
	updateEntry,
	deleteEntry,
	addEntryTrigger,
	removeEntryTrigger,
	countEntries
} from './queries/entries.js';
import { createEntryTypeLog, getEntryTypeLog, updateEntryTypeLog, deleteEntryTypeLog } from './queries/entryTypeLogs.js';
import { getAllEntryTypes, getEntryType, createEntryType, updateEntryType, deleteEntryType } from './queries/entryTypes.js';
import {
	getProfile,
	updateProfile,
	getMedications,
	getMedication,
	createMedication,
	updateMedication,
	deleteMedication,
	getMedicationChanges,
	createMedicationChange
} from './queries/profile.js';
import { getAllTriggers, getTrigger, getTriggersByCategory, createTrigger, updateTrigger, deleteTrigger } from './queries/triggers.js';
import { datesBetween, ensureEnvData, getEnvDataForDate, getEnvDataInRange, isWeatherEnabled } from './weather.js';
import { backup } from './db.js';
import { BACKUP_CONTENT_TYPE, MAX_BACKUP_BYTES, assertSqliteFile, backupFilename } from './backup.js';
import {
	assertBloodType,
	assertBoolean,
	optionalIdList,
	assertDayLocation,
	assertExtraOptions,
	assertDate,
	assertEndAfterStart,
	assertIdentifier,
	assertIsoDatetime,
	assertMedicationChangeType,
	assertMetricSchema,
	assertTriggerCategory,
	assertYearMonth,
	optionalColor,
	optionalEntryCategory,
	optionalMetricValues,
	optionalNumber,
	optionalReminderTimes,
	optionalText,
	optionalWellbeing,
	optionalTriggerCategory,
	parseJsonRequest,
	requiredText
} from './security.js';

export interface ApiEvent {
	request: Request;
	url: URL;
	fetch: typeof fetch;
}

interface RouteEvent extends ApiEvent {
	params: Record<string, string>;
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type Handler = (event: RouteEvent) => Response | Promise<Response>;

interface Route {
	pattern: RegExp;
	paramNames: string[];
	handlers: Partial<Record<Method, Handler>>;
}

/** `'/api/entries/:id'` → a route whose `:id` segment becomes `params.id`. */
function route(path: string, handlers: Route['handlers']): Route {
	const paramNames: string[] = [];
	const source = path
		.split('/')
		.map((segment) => {
			if (!segment.startsWith(':')) return segment.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
			paramNames.push(segment.slice(1));
			return '([^/]+)';
		})
		.join('/');
	return { pattern: new RegExp(`^${source}/?$`), paramNames, handlers };
}

function decodeSegment(segment: string): string {
	try {
		return decodeURIComponent(segment);
	} catch {
		error(400, 'Malformed path');
	}
}

const noContent = () => new Response(null, { status: 204 });
const notFound = () => json({ error: 'Not found' }, { status: 404 });

const routes: Route[] = [
	route('/api/health', {
		GET: () => json({ status: 'ok' })
	}),

	// How much is in the diary: decides whether a fresh device sees the introduction.
	route('/api/stats', {
		GET: () => json({ entries: countEntries() })
	}),

	// ─── Entries ──────────────────────────────────────────────────────────────

	route('/api/entries', {
		GET: ({ url }) => {
			const date = url.searchParams.get('date');
			if (date) return json(getEntryByDate(assertDate(date, 'date')));

			const from = url.searchParams.get('from');
			const to = url.searchParams.get('to');
			if (from || to) {
				const safeFrom = assertDate(from, 'from');
				const safeTo = assertDate(to, 'to');
				if (safeFrom > safeTo) error(400, 'from must not be after to');
				return json(getEntriesInRange(safeFrom, safeTo));
			}

			const month = url.searchParams.get('month');

			if (!month) return json({ error: 'month param required' }, { status: 400 });

			const safeMonth = assertYearMonth(month, 'month');
			return json(getMonthEntries(safeMonth));
		},
		POST: async (event) => {
			const body = await parseJsonRequest(event);
			const { entry, created } = createEntry(assertDate(body.date, 'date'), {
				...(body.note !== undefined && { note: optionalText(body.note) }),
				...(body.wellbeing !== undefined && { wellbeing: optionalWellbeing(body.wellbeing) }),
				...(body.location !== undefined && { location: assertDayLocation(body.location) })
			});
			return json(entry, { status: created ? 201 : 200 });
		}
	}),

	route('/api/entries/:id', {
		GET: ({ params }) => {
			const entry = getEntry(assertIdentifier(params.id, 'id'));
			if (!entry) return notFound();
			return json(entry);
		},
		PUT: async (event) => {
			const body = await parseJsonRequest(event);
			const entry = updateEntry(assertIdentifier(event.params.id, 'id'), {
				...(body.note !== undefined && { note: optionalText(body.note) }),
				...(body.wellbeing !== undefined && { wellbeing: optionalWellbeing(body.wellbeing) }),
				...(body.location !== undefined && { location: assertDayLocation(body.location) })
			});
			if (!entry) return notFound();
			return json(entry);
		},
		DELETE: ({ params }) => {
			deleteEntry(assertIdentifier(params.id, 'id'));
			return noContent();
		},
		PATCH: async (event) => {
			const body = await parseJsonRequest(event);
			const entryId = assertIdentifier(event.params.id, 'id');
			const action = requiredText(body.action, 'action', 32);
			if (!getEntry(entryId)) return notFound();

			if (action === 'add_trigger') {
				const triggerId = assertIdentifier(body.triggerId, 'triggerId');
				if (!getTrigger(triggerId)) return notFound();
				addEntryTrigger(entryId, triggerId, optionalText(body.note) ?? undefined);
			} else if (action === 'remove_trigger') {
				removeEntryTrigger(entryId, assertIdentifier(body.triggerId, 'triggerId'));
			} else {
				return json({ error: 'Invalid action' }, { status: 400 });
			}

			return json(getEntry(entryId));
		}
	}),

	// ─── Entry Type Logs ──────────────────────────────────────────────────────

	route('/api/entry-type-logs', {
		POST: async (event) => {
			const body = await parseJsonRequest(event);
			const entryId = assertIdentifier(body.entryId, 'entryId');
			const entryTypeId = assertIdentifier(body.entryTypeId, 'entryTypeId');
			if (!getEntry(entryId) || !getEntryType(entryTypeId)) return notFound();
			const startTime = assertIsoDatetime(body.startTime, 'startTime');
			const endTime = body.endTime == null ? null : assertIsoDatetime(body.endTime, 'endTime');
			assertEndAfterStart(startTime, endTime);
			const log = createEntryTypeLog({ entryId, entryTypeId, startTime, endTime, metricValues: optionalMetricValues(body.metricValues) ?? {} });
			return json(log, { status: 201 });
		}
	}),

	route('/api/entry-type-logs/:id', {
		PUT: async (event) => {
			const body = await parseJsonRequest(event);
			const id = assertIdentifier(event.params.id, 'id');
			const existing = getEntryTypeLog(id);
			if (!existing) return notFound();
			const startTime = body.startTime !== undefined ? assertIsoDatetime(body.startTime, 'startTime') : undefined;
			const endTime = body.endTime === undefined ? undefined : body.endTime === null ? null : assertIsoDatetime(body.endTime, 'endTime');
			assertEndAfterStart(startTime ?? existing.startTime, endTime === undefined ? existing.endTime : endTime);
			const log = updateEntryTypeLog(id, {
				...(startTime !== undefined && { startTime }),
				...(endTime !== undefined && { endTime }),
				...(body.metricValues !== undefined && { metricValues: optionalMetricValues(body.metricValues) ?? {} })
			});
			if (!log) return notFound();
			return json(log);
		},
		DELETE: ({ params }) => {
			deleteEntryTypeLog(assertIdentifier(params.id, 'id'));
			return noContent();
		}
	}),

	// ─── Entry Types ──────────────────────────────────────────────────────────

	route('/api/entry-types', {
		GET: () => json(getAllEntryTypes()),
		POST: async (event) => {
			const body = await parseJsonRequest(event);
			const type = createEntryType({
				name: requiredText(body.name, 'name', 120),
				icon: optionalText(body.icon, 32) ?? undefined,
				color: optionalColor(body.color) ?? undefined,
				metricSchema: assertMetricSchema(body.metricSchema),
				category: optionalEntryCategory(body.category)
			});
			if (type === 'exists') error(409, 'An entry type with this name exists already.');
			return json(type, { status: 201 });
		}
	}),

	route('/api/entry-types/:id', {
		PUT: async (event) => {
			const body = await parseJsonRequest(event);
			const type = updateEntryType(assertIdentifier(event.params.id, 'id'), {
				...(body.name !== undefined && { name: requiredText(body.name, 'name', 120) }),
				...(body.icon !== undefined && { icon: optionalText(body.icon, 32) }),
				...(body.color !== undefined && { color: optionalColor(body.color) ?? null }),
				...(body.metricSchema !== undefined && { metricSchema: assertMetricSchema(body.metricSchema) }),
				...(body.hidden !== undefined && { hidden: assertBoolean(body.hidden, 'hidden') }),
				...(body.extraOptions !== undefined && { extraOptions: assertExtraOptions(body.extraOptions) })
			});
			if (type === 'exists') error(409, 'An entry type with this name exists already.');
			if (!type) return notFound();
			return json(type);
		},
		DELETE: ({ params }) => {
			const result = deleteEntryType(assertIdentifier(params.id, 'id'));
			if (result === 'in-use') error(409, 'This entry type has entries. Hide it instead.');
			return noContent();
		}
	}),

	// ─── Triggers ─────────────────────────────────────────────────────────────

	route('/api/triggers', {
		GET: ({ url }) => {
			const category = url.searchParams.get('category');
			if (category) return json(getTriggersByCategory(assertTriggerCategory(category)));
			return json(getAllTriggers());
		},
		POST: async (event) => {
			const body = await parseJsonRequest(event);
			const trigger = createTrigger({
				name: requiredText(body.name, 'name', 120),
				category: optionalTriggerCategory(body.category)
			});
			if (trigger === 'exists') error(409, 'A trigger with this name exists already.');
			return json(trigger, { status: 201 });
		}
	}),

	route('/api/triggers/:id', {
		PUT: async (event) => {
			const body = await parseJsonRequest(event);
			const trigger = updateTrigger(assertIdentifier(event.params.id, 'id'), {
				...(body.name !== undefined && { name: requiredText(body.name, 'name', 120) }),
				...(body.category !== undefined && { category: optionalTriggerCategory(body.category) }),
				...(body.hidden !== undefined && { hidden: assertBoolean(body.hidden, 'hidden') }),
				...(body.extraOptions !== undefined && { extraOptions: assertExtraOptions(body.extraOptions) })
			});
			if (trigger === 'exists') error(409, 'A trigger with this name exists already.');
			if (!trigger) return notFound();
			return json(trigger);
		},
		DELETE: ({ params }) => {
			deleteTrigger(assertIdentifier(params.id, 'id'));
			return noContent();
		}
	}),

	// ─── Environment ──────────────────────────────────────────────────────────

	route('/api/environmental', {
		GET: async ({ url }) => {
			const from = url.searchParams.get('from');
			const to = url.searchParams.get('to');
			if (from || to) {
				const safeFrom = assertDate(from, 'from');
				const safeTo = assertDate(to, 'to');
				if (safeFrom > safeTo) error(400, 'from must not be after to');
				// fill=1: fetch what is missing first (the analysis and the report need every day).
				if (url.searchParams.get('fill') === '1') await ensureEnvData(datesBetween(safeFrom, safeTo));
				return json(getEnvDataInRange(safeFrom, safeTo));
			}

			const date = assertDate(url.searchParams.get('date'), 'date');
			if (url.searchParams.get('cached') !== '1') await ensureEnvData([date]);
			return json(getEnvDataForDate(date) ?? null);
		}
	}),

	// ─── Profile & Medications ────────────────────────────────────────────────

	route('/api/profile', {
		GET: () => json(getProfile()),
		PUT: async (event) => {
			const body = await parseJsonRequest(event);
			// Only the fields that are sent change.
			const profile = updateProfile({
				...(body.displayName !== undefined && { displayName: optionalText(body.displayName, 120) }),
				...(body.bloodType !== undefined && { bloodType: assertBloodType(body.bloodType) }),
				...(body.locationLat !== undefined && { locationLat: optionalNumber(body.locationLat, 'locationLat', -90, 90) }),
				...(body.locationLon !== undefined && { locationLon: optionalNumber(body.locationLon, 'locationLon', -180, 180) }),
				...(body.locationName !== undefined && { locationName: optionalText(body.locationName, 160) }),
				...(body.weatherEnabled !== undefined && { weatherEnabled: assertBoolean(body.weatherEnabled, 'weatherEnabled') })
			});
			return json(profile);
		}
	}),

	route('/api/profile/location-search', {
		GET: async ({ url, fetch }) => {
			// The search text goes to Open-Meteo, so only with weather turned on.
			if (!isWeatherEnabled()) return json({ message: 'Weather is turned off' }, { status: 403 });
			const query = requiredText(url.searchParams.get('q'), 'q', 120);
			const searchUrl = new URL('https://geocoding-api.open-meteo.com/v1/search');
			searchUrl.searchParams.set('name', query);
			searchUrl.searchParams.set('count', '5');
			// Place names in the app's language; Open-Meteo knows the two-letter codes.
			const lang = url.searchParams.get('lang');
			searchUrl.searchParams.set('language', lang && /^[a-z]{2}$/.test(lang) ? lang : 'en');
			searchUrl.searchParams.set('format', 'json');

			const response = await fetch(searchUrl, {
				signal: AbortSignal.timeout(5000)
			}).catch(() => null);

			if (!response?.ok) {
				return json({ results: [] }, { status: 200 });
			}

			const payload = await response.json().catch(() => ({ results: [] }));
			return json({ results: Array.isArray(payload?.results) ? payload.results : [] });
		}
	}),

	route('/api/medications', {
		GET: () => json(getMedications()),
		POST: async (event) => {
			const body = await parseJsonRequest(event);
			const med = createMedication({
				name: requiredText(body.name, 'name', 120),
				dosage: optionalText(body.dosage, 64) ?? undefined,
				unit: optionalText(body.unit, 32) ?? undefined,
				startDate: assertDate(body.startDate, 'startDate'),
				notes: optionalText(body.notes, 1000) ?? undefined,
				reminderTimes: optionalReminderTimes(body.reminderTimes),
				forTypes: optionalIdList(body.forTypes, 'forTypes')
			});
			return json(med, { status: 201 });
		}
	}),

	route('/api/medications/:id', {
		PUT: async (event) => {
			const body = await parseJsonRequest(event);
			const med = updateMedication(assertIdentifier(event.params.id, 'id'), {
				...(body.name !== undefined && { name: requiredText(body.name, 'name', 120) }),
				...(body.dosage !== undefined && { dosage: optionalText(body.dosage, 64) }),
				...(body.unit !== undefined && { unit: optionalText(body.unit, 32) }),
				...(body.startDate !== undefined && { startDate: assertDate(body.startDate, 'startDate') }),
				...(body.endDate !== undefined && {
					endDate: body.endDate === null ? null : assertDate(body.endDate, 'endDate')
				}),
				...(body.notes !== undefined && { notes: optionalText(body.notes, 1000) }),
				...(body.isActive !== undefined && { isActive: assertBoolean(body.isActive, 'isActive') }),
				...(body.reminderTimes !== undefined && { reminderTimes: optionalReminderTimes(body.reminderTimes) }),
				...(body.forTypes !== undefined && { forTypes: optionalIdList(body.forTypes, 'forTypes') })
			});
			if (!med) return notFound();
			return json(med);
		},
		DELETE: ({ params }) => {
			deleteMedication(assertIdentifier(params.id, 'id'));
			return noContent();
		}
	}),

	route('/api/medication-changes', {
		GET: ({ url }) => {
			const medicationId = url.searchParams.get('medicationId') ?? undefined;
			return json(getMedicationChanges(medicationId ? assertIdentifier(medicationId, 'medicationId') : undefined));
		},
		POST: async (event) => {
			const body = await parseJsonRequest(event);
			const medicationId = body.medicationId == null ? undefined : assertIdentifier(body.medicationId, 'medicationId');
			if (medicationId && !getMedication(medicationId)) return notFound();
			const change = createMedicationChange({
				medicationId,
				changeType: assertMedicationChangeType(body.changeType),
				oldDosage: optionalText(body.oldDosage, 64) ?? undefined,
				newDosage: optionalText(body.newDosage, 64) ?? undefined,
				changeDate: assertDate(body.changeDate, 'changeDate'),
				reason: optionalText(body.reason, 500) ?? undefined,
				notes: optionalText(body.notes, 1000) ?? undefined
			});
			return json(change, { status: 201 });
		}
	}),

	// ─── Backup ───────────────────────────────────────────────────────────────

	route('/api/backup', {
		GET: () =>
			new Response(backup.exportBytes() as Uint8Array<ArrayBuffer>, {
				headers: {
					'Content-Type': BACKUP_CONTENT_TYPE,
					'Content-Disposition': `attachment; filename="${backupFilename()}"`
				}
			}),
		// Replaces the whole database. The CSRF check in hooks.server.ts applies.
		POST: async ({ request }) => {
			const type = request.headers.get('content-type') ?? '';
			if (!type.startsWith(BACKUP_CONTENT_TYPE) && !type.startsWith('application/octet-stream')) {
				error(415, `${BACKUP_CONTENT_TYPE} required`);
			}
			if (Number(request.headers.get('content-length') ?? '0') > MAX_BACKUP_BYTES) {
				error(413, 'Backup file is larger than 50 MB');
			}
			const bytes = new Uint8Array(await request.arrayBuffer());
			assertSqliteFile(bytes);
			backup.restore(bytes);
			return json({ entries: countEntries() });
		}
	})
];

/**
 * Dispatch one `/api/*` request. Validation failures are thrown as SvelteKit
 * `HttpError`s, exactly as the per-file endpoints did before.
 */
export async function handleApiRequest(event: ApiEvent): Promise<Response> {
	const method = (event.request.method === 'HEAD' ? 'GET' : event.request.method) as Method;

	for (const { pattern, paramNames, handlers } of routes) {
		const match = pattern.exec(event.url.pathname);
		if (!match) continue;

		const handler = handlers[method];
		if (!handler) {
			return new Response(`${event.request.method} method not allowed`, {
				status: 405,
				headers: { Allow: Object.keys(handlers).join(', ') }
			});
		}

		const params = Object.fromEntries(paramNames.map((name, i) => [name, decodeSegment(match[i + 1])]));
		return handler({ ...event, params });
	}

	error(404, 'Not found');
}

/**
 * IndexedDB-backed mutation queue for offline support.
 * Mutations that fail when offline are queued and replayed on reconnect.
 */

import { api } from '$lib/api.js';

const DB_NAME = 'chronicals-offline';
const DB_VERSION = 1;
const STORE = 'mutations';

export interface QueuedMutation {
	id: string;
	url: string;
	method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
	body?: unknown;
	timestamp: number;
	retries: number;
}

let _dbPromise: Promise<IDBDatabase> | null = null;

function openDB(): Promise<IDBDatabase> {
	if (!_dbPromise) {
		_dbPromise = new Promise((resolve, reject) => {
			const req = indexedDB.open(DB_NAME, DB_VERSION);
			req.onupgradeneeded = (e) => {
				const db = (e.target as IDBOpenDBRequest).result;
				if (!db.objectStoreNames.contains(STORE)) {
					db.createObjectStore(STORE, { keyPath: 'id' });
				}
			};
			req.onsuccess = () => resolve(req.result);
			req.onerror = () => { _dbPromise = null; reject(req.error); };
		});
	}
	return _dbPromise;
}

export async function enqueue(mutation: Omit<QueuedMutation, 'id' | 'timestamp' | 'retries'>): Promise<void> {
	const db = await openDB();
	return new Promise((resolve, reject) => {
		const tx = db.transaction(STORE, 'readwrite');
		tx.objectStore(STORE).put({ ...mutation, id: crypto.randomUUID(), timestamp: Date.now(), retries: 0 });
		tx.oncomplete = () => resolve();
		tx.onerror = () => reject(tx.error);
	});
}

export async function getPending(): Promise<QueuedMutation[]> {
	const db = await openDB();
	return new Promise((resolve, reject) => {
		const tx = db.transaction(STORE, 'readonly');
		const req = tx.objectStore(STORE).getAll();
		req.onsuccess = () => resolve((req.result as QueuedMutation[]).sort((a, b) => a.timestamp - b.timestamp));
		req.onerror = () => reject(req.error);
	});
}

async function remove(id: string): Promise<void> {
	const db = await openDB();
	return new Promise((resolve, reject) => {
		const tx = db.transaction(STORE, 'readwrite');
		tx.objectStore(STORE).delete(id);
		tx.oncomplete = () => resolve();
		tx.onerror = () => reject(tx.error);
	});
}

async function incrementRetry(mutation: QueuedMutation): Promise<void> {
	const db = await openDB();
	return new Promise((resolve, reject) => {
		const tx = db.transaction(STORE, 'readwrite');
		tx.objectStore(STORE).put({ ...mutation, retries: mutation.retries + 1 });
		tx.oncomplete = () => resolve();
		tx.onerror = () => reject(tx.error);
	});
}

export async function flush(): Promise<{ succeeded: number; failed: number }> {
	const pending = await getPending();
	let succeeded = 0;
	let failed = 0;

	for (const mutation of pending) {
		try {
			const res = await api(mutation.url, {
				method: mutation.method,
				headers: mutation.body ? { 'Content-Type': 'application/json' } : {},
				body: mutation.body ? JSON.stringify(mutation.body) : undefined
			});
			if (res.ok) {
				await remove(mutation.id);
				succeeded++;
			} else if (res.status >= 400 && res.status < 500) {
				// Client error: don't retry, remove it
				await remove(mutation.id);
				failed++;
			} else {
				await incrementRetry(mutation);
				failed++;
			}
		} catch {
			// Network error: increment retry count, keep in queue
			if (mutation.retries >= 10) {
				await remove(mutation.id); // Give up after 10 retries
				failed++;
			} else {
				await incrementRetry(mutation);
				failed++;
			}
		}
	}

	return { succeeded, failed };
}

export async function pendingCount(): Promise<number> {
	const db = await openDB();
	return new Promise((resolve, reject) => {
		const tx = db.transaction(STORE, 'readonly');
		const req = tx.objectStore(STORE).count();
		req.onsuccess = () => resolve(req.result);
		req.onerror = () => reject(req.error);
	});
}

/**
 * Wrap a fetch call with offline queue support.
 * If the fetch fails due to network error, the mutation is queued.
 * Returns the response if online, or null if queued.
 */
export async function fetchOrQueue(
	url: string,
	options: { method: 'POST' | 'PUT' | 'PATCH' | 'DELETE'; body?: unknown }
): Promise<Response | null> {
	try {
		const res = await api(url, {
			method: options.method,
			headers: options.body ? { 'Content-Type': 'application/json' } : {},
			body: options.body ? JSON.stringify(options.body) : undefined,
			signal: AbortSignal.timeout(5000)
		});
		return res;
	} catch {
		// Network error or timeout: queue the mutation
		await enqueue({ url, method: options.method, body: options.body });
		return null;
	}
}

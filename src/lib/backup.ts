import { api } from '$lib/api.js';
import { saveFile } from '$lib/files.js';

/** Download (web) or share (Android) the whole database as an SQLite file. */
export async function exportBackup(): Promise<void> {
	const res = await api('/api/backup');
	if (!res.ok) throw new Error(res.statusText);
	const blob = await res.blob();
	const filename =
		/filename="([^"]+)"/.exec(res.headers.get('content-disposition') ?? '')?.[1] ?? 'chronicals.db';

	await saveFile(blob, filename);
}

/** Replace all data with a backup file. Returns the number of diary days restored. */
export async function importBackup(file: File): Promise<number> {
	const res = await api('/api/backup', {
		method: 'POST',
		headers: { 'Content-Type': 'application/vnd.sqlite3' },
		body: file
	});
	const body = await res.json().catch(() => null);
	if (!res.ok) throw new Error(body?.message ?? body?.error ?? res.statusText);
	return body.entries;
}

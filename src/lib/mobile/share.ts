import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { toBase64 } from './database.js';

/** Open the Android share sheet for a file. Cancelling the sheet is not an error. */
export async function shareFile(blob: Blob, filename: string): Promise<void> {
	const data = toBase64(new Uint8Array(await blob.arrayBuffer()));
	const { uri } = await Filesystem.writeFile({ path: filename, directory: Directory.Cache, data });
	try {
		await Share.share({ title: filename, files: [uri] });
	} catch (err) {
		if (!/cancel/i.test(String((err as Error)?.message ?? err))) throw err;
	}
}

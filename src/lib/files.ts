/** Save a file: a download on the web, the share sheet in the Android app. */
export async function saveFile(blob: Blob, filename: string): Promise<void> {
	if (__NATIVE_APP__) {
		// The WebView can't download files; the share sheet can save them to
		// Files or Drive or send them elsewhere.
		const { shareFile } = await import('$lib/mobile/share.js');
		await shareFile(blob, filename);
		return;
	}

	const url = URL.createObjectURL(blob);
	const link = document.createElement('a');
	link.href = url;
	link.download = filename;
	link.click();
	setTimeout(() => URL.revokeObjectURL(url), 1000);
}

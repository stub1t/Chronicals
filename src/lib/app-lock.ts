/** Whether the Android app's lock is on. Stored on the device only. */
const STORAGE_KEY = 'chronicals-app-lock';

export function isAppLockEnabled(): boolean {
	try {
		return localStorage.getItem(STORAGE_KEY) === 'on';
	} catch {
		return false;
	}
}

export function setAppLockEnabled(enabled: boolean): void {
	try {
		if (enabled) localStorage.setItem(STORAGE_KEY, 'on');
		else localStorage.removeItem(STORAGE_KEY);
	} catch {
		// Storage unavailable: the lock can't be remembered, so it stays off.
	}
}

/**
 * Optional app lock for the Android app: fingerprint, face or the phone's
 * screen lock, on start and after a minute in the background. While it is on,
 * the privacy screen hides the app in the recent-apps view and blocks
 * screenshots. This keeps someone holding the unlocked phone out of the
 * diary; it does not encrypt anything.
 */
import { App } from '@capacitor/app';
import { PrivacyScreen } from '@capacitor/privacy-screen';
import { BiometryType, NativeBiometric } from '@capgo/capacitor-native-biometric';
import { writable } from 'svelte/store';
import { isAppLockEnabled, setAppLockEnabled } from '$lib/app-lock.js';
import { t } from '$lib/stores/locale.js';

const RELOCK_AFTER_MS = 60_000;

export const locked = writable(isAppLockEnabled());

let started = false;

/** Lock now if the lock is on, and relock after a minute in the background. */
export function startAppLock(): void {
	if (started) return;
	started = true;
	if (isAppLockEnabled()) void PrivacyScreen.enable();

	let hiddenAt = 0;
	App.addListener('appStateChange', ({ isActive }) => {
		if (!isAppLockEnabled()) return;
		if (!isActive) {
			hiddenAt = Date.now();
		} else if (hiddenAt && Date.now() - hiddenAt > RELOCK_AFTER_MS) {
			locked.set(true);
		}
	});
}

/** Show the system prompt. Fingerprint, face and the screen lock PIN/pattern/password all work. */
export async function unlock(): Promise<boolean> {
	try {
		await NativeBiometric.verifyIdentity({
			title: t('app_lock_prompt'),
			allowedBiometryTypes: [
				BiometryType.FINGERPRINT,
				BiometryType.FACE_AUTHENTICATION,
				BiometryType.IRIS_AUTHENTICATION,
				BiometryType.DEVICE_CREDENTIAL
			],
			maxAttempts: 5
		});
		locked.set(false);
		return true;
	} catch {
		return false;
	}
}

/** Turn the lock on after one successful unlock, so nobody locks themselves out by accident. */
export async function enableAppLock(): Promise<'enabled' | 'unavailable' | 'cancelled'> {
	const { isAvailable } = await NativeBiometric.isAvailable({ useFallback: true });
	if (!isAvailable) return 'unavailable';
	if (!(await unlock())) return 'cancelled';
	setAppLockEnabled(true);
	await PrivacyScreen.enable();
	return 'enabled';
}

/** Turning the lock off also needs an unlock. */
export async function disableAppLock(): Promise<boolean> {
	if (!(await unlock())) return false;
	setAppLockEnabled(false);
	await PrivacyScreen.disable();
	return true;
}

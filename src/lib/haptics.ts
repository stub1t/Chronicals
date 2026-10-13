/** Small vibrations on Android for taps and saves. No-ops on the web. */
export function hapticTap(): void {
	if (__NATIVE_APP__) {
		import('@capacitor/haptics').then(({ Haptics, ImpactStyle }) => Haptics.impact({ style: ImpactStyle.Light })).catch(() => {});
	}
}

export function hapticSuccess(): void {
	if (__NATIVE_APP__) {
		import('@capacitor/haptics')
			.then(({ Haptics, NotificationType }) => Haptics.notification({ type: NotificationType.Success }))
			.catch(() => {});
	}
}

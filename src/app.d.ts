// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
declare global {
	/** True only in the Android (Capacitor) build; see vite.config.ts. */
	const __NATIVE_APP__: boolean;
	/** Version shown in Settings; see vite.config.ts. */
	const __APP_VERSION__: string;

	namespace App {
		// interface Error {}
		// interface Locals {}
		// interface PageData {}
		// interface PageState {}
		// interface Platform {}
	}
}

export {};

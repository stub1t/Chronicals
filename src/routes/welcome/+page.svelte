<script lang="ts">
	/** The introduction on a fresh install: a few short steps, each saved as you go, all skippable. */
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import ArrowLeft from '@lucide/svelte/icons/arrow-left';
	import { api } from '$lib/api.js';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import LocationSearch, { type Place } from '$lib/me/LocationSearch.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Switch from '$lib/ui/Switch.svelte';
	import Toggle from '$lib/ui/Toggle.svelte';
	import { LANGUAGES, localeStore, localizeBuiltInText, setLanguage, t, type LanguageCode } from '$lib/i18n';
	import { markOnboarded } from '$lib/onboarding.js';
	import { loadReminderSettings, saveReminderSettings, type ReminderSettings } from '$lib/reminders.js';
	import type { EntryType, UserProfile } from '$lib/types';

	let { data }: { data: { profile: UserProfile; entryTypes: EntryType[] } } = $props();

	const STEPS = __NATIVE_APP__ ? 5 : 4;
	let step = $state(0);
	let language = $state<LanguageCode>('en');
	// svelte-ignore state_referenced_locally
	let name = $state(data.profile.displayName ?? '');
	// Meals have their own tab, so they are not switched on or off here.
	// svelte-ignore state_referenced_locally
	let types = $state<EntryType[]>(data.entryTypes.filter((type) => type.isBuiltIn && type.category !== 'meal'));
	// svelte-ignore state_referenced_locally
	let place = $state<Place | null>(
		data.profile.locationLat != null && data.profile.locationLon != null
			? { lat: data.profile.locationLat, lon: data.profile.locationLon, name: data.profile.locationName ?? '' }
			: null
	);
	let reminders = $state<ReminderSettings>({ daily: false, time: '20:00', ongoingHours: 3 });
	let appLock = $state(false);
	let autoLocation = $state(false);
	// svelte-ignore state_referenced_locally
	let weatherEnabled = $state(data.profile.weatherEnabled);

	onMount(() => {
		language = localeStore.current;
		reminders = loadReminderSettings();
	});

	const put = (url: string, body: unknown) =>
		api(url, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

	async function setShown(type: EntryType, shown: boolean) {
		const res = await put(`/api/entry-types/${type.id}`, { hidden: !shown });
		if (res.ok) {
			const saved: EntryType = await res.json();
			types = types.map((item) => (item.id === saved.id ? saved : item));
		}
	}

	// Saved at once: the place search below only works with weather turned on.
	async function setWeather(on: boolean) {
		const res = await put('/api/profile', { weatherEnabled: on }).catch(() => null);
		weatherEnabled = res?.ok ? on : !on;
		if (!weatherEnabled) autoLocation = false;
	}

	async function next() {
		if (step === 1) await put('/api/profile', { displayName: name.trim() || null });
		if (step === 3 && weatherEnabled) {
			await put('/api/profile', { locationLat: place?.lat ?? null, locationLon: place?.lon ?? null, locationName: place?.name ?? null });
			if (__NATIVE_APP__ && autoLocation) {
				const { setAutoLocation } = await import('$lib/mobile/location.js');
				autoLocation = await setAutoLocation(true);
			}
		}
		if (step === 4) await saveDeviceSettings();
		if (step < STEPS - 1) step++;
		else finish();
	}

	async function saveDeviceSettings() {
		saveReminderSettings(reminders);
		if (!__NATIVE_APP__) return;
		const { requestReminderPermission, syncReminders } = await import('$lib/mobile/reminders.js');
		if (reminders.daily || reminders.ongoingHours) await requestReminderPermission();
		await syncReminders();
		if (appLock) {
			const { enableAppLock } = await import('$lib/mobile/app-lock.js');
			await enableAppLock();
		}
	}

	function finish() {
		markOnboarded();
		goto('/', { replaceState: true });
	}
</script>

<div class="welcome">
	<header class="top">
		{#if step > 0}
			<button type="button" class="back" onclick={() => step--} aria-label={t('welcome_back')}><ArrowLeft size={22} class="flip-rtl" /></button>
		{:else}
			<span></span>
		{/if}
		<ol class="dots" aria-label={t('welcome_progress', { step: step + 1, total: STEPS })}>
			{#each Array.from({ length: STEPS }, (_, i) => i) as i (i)}
				<li class:done={i <= step}></li>
			{/each}
		</ol>
		<button type="button" class="skip" onclick={finish}>{t('welcome_skip')}</button>
	</header>

	<section class="step">
		{#if step === 0}
			<img class="logo" src="/icon.svg" alt="" width="96" height="96" />
			<h1>{t('welcome_title')}</h1>
			<p class="lead">{t('welcome_text')}</p>
			<div class="languages" role="radiogroup" aria-label={t('settings_language')}>
				{#each LANGUAGES as lang (lang.code)}
					<button
						type="button"
						role="radio"
						aria-checked={language === lang.code}
						class="language"
						class:picked={language === lang.code}
						lang={lang.code}
						onclick={async () => {
							language = lang.code;
							await setLanguage(lang.code);
						}}>{lang.name}</button
					>
				{/each}
			</div>
		{:else if step === 1}
			<h1>{t('welcome_name_title')}</h1>
			<p class="lead">{t('profile_display_name_hint')}</p>
			<!-- svelte-ignore a11y_autofocus -->
			<input class="ui-input big" bind:value={name} placeholder={t('profile_display_name_placeholder')} aria-label={t('profile_display_name')} autocomplete="given-name" maxlength="120" autofocus />
		{:else if step === 2}
			<h1>{t('welcome_types_title')}</h1>
			<p class="lead">{t('welcome_types_text')}</p>
			<ul class="types">
				{#each types as type (type.id)}
					<li>
						<EntryTypeIcon entryType={type} size={40} badge />
						<span class="type-name">{localizeBuiltInText(type.name)}</span>
						<Switch checked={!type.hidden} color={type.color} label={t('entry_types_show', { name: localizeBuiltInText(type.name) })} onchange={(on) => setShown(type, on)} />
					</li>
				{/each}
			</ul>
			<p class="hint">{t('welcome_types_hint')}</p>
		{:else if step === 3}
			<h1>{t('welcome_place_title')}</h1>
			<p class="lead">{t('settings_weather_note')}</p>
			<Toggle
				label={t('settings_weather')}
				description={weatherEnabled ? t('settings_weather_on') : t('settings_weather_off')}
				checked={weatherEnabled}
				onchange={setWeather}
			/>
			{#if weatherEnabled}
				<label class="ui-label" for="welcome-place">{t('welcome_place_label')}</label>
				<LocationSearch bind:value={place} id="welcome-place" />
				{#if __NATIVE_APP__}
					<Toggle label={t('settings_auto_location')} description={t('settings_auto_location_note')} bind:checked={autoLocation} />
				{/if}
			{/if}
		{:else}
			<h1>{t('welcome_reminders_title')}</h1>
			<p class="lead">{t('welcome_reminders_text')}</p>
			<div class="toggles">
				<Toggle label={t('settings_reminder_daily')} description={t('me_reminder_at', { time: reminders.time })} bind:checked={reminders.daily} />
				<Toggle
					label={t('settings_reminder_ongoing')}
					description={t('settings_reminder_ongoing_after', { count: 3 })}
					checked={reminders.ongoingHours > 0}
					onchange={(on) => (reminders = { ...reminders, ongoingHours: on ? 3 : 0 })}
				/>
				<Toggle label={t('settings_app_lock')} description={t('settings_app_lock_toggle')} bind:checked={appLock} />
			</div>
		{/if}
	</section>

	<footer class="bottom">
		<Button block size="lg" onclick={next}>{step === STEPS - 1 ? t('welcome_done') : t('welcome_next')}</Button>
	</footer>
</div>

<style>
	.welcome {
		min-height: 100%;
		max-width: 560px;
		margin: 0 auto;
		padding: var(--space-4) var(--space-5) var(--space-6);
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
		box-sizing: border-box;
	}

	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
		min-height: 44px;
	}

	.back,
	.skip {
		min-height: 44px;
		border: none;
		background: none;
		color: var(--text-muted);
		font-size: var(--text-md);
		font-weight: 700;
		cursor: pointer;
	}

	.back {
		width: 44px;
		display: grid;
		place-items: center;
		color: var(--text);
	}

	.dots {
		display: flex;
		gap: 6px;
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.dots li {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--border);
		transition: width 0.3s var(--ease-spring);
	}

	.dots li.done {
		width: 20px;
		border-radius: var(--radius-pill);
		background: var(--accent);
	}

	.step {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.logo {
		align-self: center;
		margin-top: var(--space-6);
		filter: drop-shadow(0 12px 24px rgb(106 85 227 / 0.3));
	}

	h1 {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 900;
	}

	.lead {
		margin: 0;
		color: var(--text-muted);
		font-size: var(--text-md);
		line-height: 1.5;
	}

	.languages {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.language {
		min-height: 44px;
		padding: 0 16px;
		border: 1.5px solid var(--border);
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-md);
		font-weight: 700;
		cursor: pointer;
	}

	.language.picked {
		border-color: var(--accent);
		background: var(--accent-soft);
		color: var(--accent);
	}

	.big {
		font-size: var(--text-xl);
		min-height: 56px;
	}

	.types {
		list-style: none;
		margin: 0;
		padding: 0;
		background: var(--bg-card);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
	}

	.types li {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-4);
	}

	.types li + li {
		border-top: 1px solid var(--border);
	}

	.type-name {
		flex: 1;
		font-weight: 700;
	}

	.hint {
		margin: 0;
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.toggles {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-4);
		background: var(--bg-card);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
	}
</style>

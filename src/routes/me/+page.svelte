<script lang="ts">
	import { onMount } from 'svelte';
	import Pill from '@lucide/svelte/icons/pill';
	import Shapes from '@lucide/svelte/icons/shapes';
	import Zap from '@lucide/svelte/icons/zap';
	import Bell from '@lucide/svelte/icons/bell';
	import Fingerprint from '@lucide/svelte/icons/fingerprint';
	import Palette from '@lucide/svelte/icons/palette';
	import Languages from '@lucide/svelte/icons/languages';
	import Download from '@lucide/svelte/icons/download';
	import Upload from '@lucide/svelte/icons/upload';
	import Info from '@lucide/svelte/icons/info';
	import CodeXml from '@lucide/svelte/icons/code-xml';
	import User from '@lucide/svelte/icons/user';
	import Check from '@lucide/svelte/icons/check';
	import MapPin from '@lucide/svelte/icons/map-pin';
	import MenuGroup from '$lib/me/MenuGroup.svelte';
	import MenuRow from '$lib/me/MenuRow.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import Sheet from '$lib/ui/Sheet.svelte';
	import Toggle from '$lib/ui/Toggle.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { themeStore } from '$lib/stores/theme.js';
	import { LANGUAGES, localeStore, setLanguage, setTimeFormat, timeFormat, type LanguageCode, type TimeFormat, t } from '$lib/i18n';
	import Clock from '@lucide/svelte/icons/clock';
	import Sparkles from '@lucide/svelte/icons/sparkles';
	import { exportBackup, importBackup } from '$lib/backup.js';
	import { loadReminderSettings, ONGOING_HOURS, saveReminderSettings, type ReminderSettings } from '$lib/reminders.js';
	import Timer from '@lucide/svelte/icons/timer';
	import { isAppLockEnabled } from '$lib/app-lock.js';
	import type { EntryType, Medication, Trigger, UserProfile } from '$lib/types';

	interface Props {
		data: { profile: UserProfile; medications: Medication[]; entryTypes: EntryType[]; triggers: Trigger[] };
	}

	let { data }: Props = $props();

	type ThemeOption = 'system' | 'light' | 'dark';

	let theme = $state<ThemeOption>('system');
	let currentLanguage = $state<LanguageCode>('en');
	let languageOpen = $state(false);
	let version = $state(__APP_VERSION__);

	onMount(() => {
		theme = themeStore.current;
		currentLanguage = localeStore.current;
		if (__NATIVE_APP__) {
			reminders = loadReminderSettings();
			appLock = isAppLockEnabled();
			// The installed APK's version, which is what Obtainium and Android show too.
			import('@capacitor/app')
				.then(({ App }) => App.getInfo())
				.then((info) => (version = info.version))
				.catch(() => {}); // e.g. in a desktop browser: keep the build-time version
		}
	});

	const name = $derived(data.profile.displayName?.trim() ?? '');
	const activeMeds = $derived(data.medications.filter((m) => m.isActive).length);
	const hiddenTypes = $derived(data.entryTypes.filter((type) => type.hidden).length);

	async function chooseLanguage(code: LanguageCode) {
		currentLanguage = code;
		languageOpen = false;
		await setLanguage(code);
	}

	// ── Reminders (Android app) ───────────────────────────────────────────────
	let reminders = $state<ReminderSettings>({ daily: false, time: '20:00', ongoingHours: 3 });
	let reminderDenied = $state(false);

	async function updateReminders(next: ReminderSettings) {
		reminders = next;
		saveReminderSettings(next);
		if (__NATIVE_APP__) {
			const { requestReminderPermission, syncReminders } = await import('$lib/mobile/reminders.js');
			reminderDenied = next.daily && !(await requestReminderPermission());
			await syncReminders();
		}
	}

	// ── App lock (Android app) ────────────────────────────────────────────────
	let appLock = $state(false);
	let appLockMessage = $state<string | null>(null);

	async function toggleAppLock(on: boolean) {
		appLockMessage = null;
		if (__NATIVE_APP__) {
			const { enableAppLock, disableAppLock } = await import('$lib/mobile/app-lock.js');
			if (on) {
				const result = await enableAppLock();
				if (result === 'unavailable') appLockMessage = t('settings_app_lock_unavailable');
			} else {
				await disableAppLock();
			}
		}
		// Reflect what actually happened (a cancelled prompt leaves it unchanged).
		appLock = isAppLockEnabled();
	}

	// ── Backup ────────────────────────────────────────────────────────────────
	let fileInput: HTMLInputElement;
	let dataBusy = $state(false);

	async function handleExport() {
		dataBusy = true;
		try {
			await exportBackup();
		} catch (err) {
			toasts.show(t('settings_export_failed', { message: (err as Error).message }), 'error');
		} finally {
			dataBusy = false;
		}
	}

	async function handleImport() {
		const file = fileInput.files?.[0];
		fileInput.value = '';
		if (!file || !confirm(t('settings_import_confirm', { name: file.name }))) return;

		dataBusy = true;
		try {
			const count = await importBackup(file);
			toasts.show(t('settings_import_done', { count }), 'success');
			setTimeout(() => location.reload(), 1500);
		} catch (err) {
			toasts.show(t('settings_import_failed', { message: (err as Error).message }), 'error');
			dataBusy = false;
		}
	}
</script>

<div class="page">
	<a class="profile" href="/me/profile">
		<span class="avatar" aria-hidden="true">{#if name}{name[0].toUpperCase()}{:else}<User size={30} />{/if}</span>
		<span class="who">
			<span class="name">{name || t('me_add_name')}</span>
			<span class="where">
				{#if data.profile.locationName}
					<MapPin size={14} />
					{data.profile.locationName.split(',')[0]}
				{:else}
					{t('settings_profile_desc')}
				{/if}
			</span>
		</span>
		<span class="edit">{t('entry_types_edit')}</span>
	</a>

	<MenuGroup title={t('me_health')}>
		<MenuRow
			icon={Pill}
			color="#e0679a"
			href="/me/medications"
			label={t('settings_meds')}
			detail={activeMeds ? t('me_meds_active', { count: activeMeds }) : t('me_meds_none')}
		/>
		<MenuRow
			icon={Shapes}
			color="#5b8def"
			href="/me/entry-types"
			label={t('settings_entry_types')}
			detail={t('me_types_count', { count: data.entryTypes.length }) +
				(hiddenTypes ? ` · ${t('me_types_hidden', { count: hiddenTypes })}` : '')}
		/>
		<MenuRow
			icon={Zap}
			color="#e3a13b"
			href="/me/triggers"
			label={t('settings_triggers')}
			detail={t('me_triggers_count', { count: data.triggers.length })}
		/>
	</MenuGroup>

	<MenuGroup title={t('me_app')}>
		{#if __NATIVE_APP__}
			<div>
				<Toggle
					icon={Bell}
					color="#7462e8"
					label={t('settings_reminder_daily')}
					description={reminders.daily ? t('me_reminder_at', { time: reminders.time }) : null}
					checked={reminders.daily}
					onchange={(on) => updateReminders({ ...reminders, daily: on })}
				/>
				{#if reminders.daily}
					<div class="sub">
						<label class="ui-label" for="reminder-time">{t('settings_reminder_time')}</label>
						<input
							id="reminder-time"
							class="ui-input time"
							type="time"
							value={reminders.time}
							onchange={(e) => e.currentTarget.value && updateReminders({ ...reminders, time: e.currentTarget.value })}
						/>
						<p class="ui-hint">{t('settings_reminder_daily_note')}</p>
						{#if reminderDenied}<p class="ui-error" role="alert">{t('settings_reminder_denied')}</p>{/if}
					</div>
				{/if}
			</div>
			<div>
				<Toggle
					icon={Timer}
					color="#e3a13b"
					label={t('settings_reminder_ongoing')}
					description={reminders.ongoingHours ? t('settings_reminder_ongoing_after', { count: reminders.ongoingHours }) : null}
					checked={reminders.ongoingHours > 0}
					onchange={(on) => updateReminders({ ...reminders, ongoingHours: on ? 3 : 0 })}
				/>
				{#if reminders.ongoingHours > 0}
					<div class="sub">
						<Segmented
							label={t('settings_reminder_ongoing')}
							value={reminders.ongoingHours}
							onchange={(hours: number) => updateReminders({ ...reminders, ongoingHours: hours })}
							options={ONGOING_HOURS.map((hours) => ({ value: hours, label: t('settings_hours_short', { count: hours }) }))}
						/>
						<p class="ui-hint">{t('settings_reminder_ongoing_note')}</p>
					</div>
				{/if}
			</div>
			<div>
				<Toggle
					icon={Fingerprint}
					color="#2f9e6e"
					label={t('settings_app_lock')}
					description={t('settings_app_lock_toggle')}
					bind:checked={appLock}
					onchange={toggleAppLock}
				/>
				{#if appLock || appLockMessage}
					<div class="sub">
						{#if appLockMessage}<p class="ui-error" role="alert">{appLockMessage}</p>{/if}
						<p class="ui-hint">{t('settings_app_lock_note')}</p>
					</div>
				{/if}
			</div>
		{/if}
		<div>
			<MenuRow icon={Palette} color="#d9822b" label={t('settings_theme')} />
			<div class="sub">
				<Segmented
					label={t('settings_theme')}
					bind:value={theme}
					onchange={(value) => themeStore.set(value)}
					options={[
						{ value: 'system', label: t('settings_theme_system') },
						{ value: 'light', label: t('settings_theme_light') },
						{ value: 'dark', label: t('settings_theme_dark') }
					]}
				/>
			</div>
		</div>
		<div>
			<MenuRow icon={Clock} color="#5b8def" label={t('settings_time_format')} />
			<div class="sub">
				<Segmented
					label={t('settings_time_format')}
					value={$timeFormat}
					onchange={(value: TimeFormat) => setTimeFormat(value)}
					options={[
						{ value: 'auto', label: t('settings_time_auto') },
						{ value: '24', label: t('settings_time_24') },
						{ value: '12', label: t('settings_time_12') }
					]}
				/>
			</div>
		</div>
		<MenuRow
			icon={Languages}
			color="#3aa6b9"
			label={t('settings_language')}
			detail={LANGUAGES.find((l) => l.code === currentLanguage)?.name}
			onclick={() => (languageOpen = true)}
		/>
	</MenuGroup>

	<MenuGroup title={t('settings_data')}>
		<MenuRow icon={Download} color="#5b8def" label={t('settings_export')} onclick={dataBusy ? undefined : handleExport} />
		<MenuRow icon={Upload} color="#d6455d" label={t('settings_import')} onclick={dataBusy ? undefined : () => fileInput.click()} />
	</MenuGroup>
	<p class="ui-hint note">{t('settings_data_note')}</p>
	<!-- No accept filter in the app: Android's file picker has no type for .db and would hide the file. -->
	<input type="file" hidden accept={__NATIVE_APP__ ? undefined : '.db,.sqlite,.sqlite3'} bind:this={fileInput} onchange={handleImport} />

	<MenuGroup title={t('settings_about')}>
		<MenuRow icon={Info} color="var(--text-muted)" label="Chronicals {version}" detail={t('settings_license')} />
		<MenuRow icon={CodeXml} color="var(--text-muted)" label={t('settings_source')} href="https://github.com/stub1t/Chronicals" external />
		<MenuRow icon={Sparkles} color="var(--accent)" label={t('me_show_intro')} href="/welcome" />
	</MenuGroup>
</div>

<Sheet bind:open={languageOpen} title={t('settings_language')} closeLabel={t('close')}>
	<ul class="languages" role="radiogroup" aria-label={t('settings_language')}>
		{#each LANGUAGES as lang (lang.code)}
			<li>
				<button
					type="button"
					role="radio"
					aria-checked={currentLanguage === lang.code}
					class="language"
					class:current={currentLanguage === lang.code}
					lang={lang.code}
					dir={lang.dir}
					onclick={() => chooseLanguage(lang.code)}
				>
					<span>{lang.name}</span>
					{#if currentLanguage === lang.code}<Check size={20} />{/if}
				</button>
			</li>
		{/each}
	</ul>
</Sheet>

<style>
	.page {
		max-width: 640px;
		margin: 0 auto;
		padding: var(--space-5) var(--space-4) var(--space-8);
		display: flex;
		flex-direction: column;
		gap: var(--space-6);
	}

	.profile {
		display: flex;
		align-items: center;
		gap: var(--space-4);
		padding: var(--space-4);
		border-radius: var(--radius-lg);
		background: linear-gradient(135deg, var(--accent-soft), color-mix(in srgb, #f2ac72 20%, var(--bg-card)));
		color: var(--text);
		text-decoration: none;
	}

	.avatar {
		width: 64px;
		height: 64px;
		flex-shrink: 0;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: var(--accent);
		color: var(--on-accent);
		font-size: 1.75rem;
		font-weight: 800;
		box-shadow: 0 6px 18px color-mix(in srgb, var(--accent) 35%, transparent);
	}

	.who {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.name {
		font-size: var(--text-xl);
		font-weight: 800;
	}

	.where {
		display: flex;
		align-items: center;
		gap: 4px;
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.edit {
		padding: 6px 14px;
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		font-size: var(--text-sm);
		font-weight: 700;
	}

	.sub {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: 0 var(--space-4) var(--space-4);
		/* Line up with the text next to the icon bubble. */
		padding-inline-start: calc(var(--space-4) + 40px + var(--space-3));
	}

	.time {
		max-width: 160px;
	}

	.note {
		margin-top: calc(var(--space-6) * -1 + var(--space-2));
		padding: 0 var(--space-1);
	}

	.languages {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.language {
		width: 100%;
		min-height: 52px;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0 var(--space-4);
		border: none;
		border-radius: var(--radius-md);
		background: none;
		color: var(--text);
		font-size: var(--text-md);
		font-weight: 600;
		text-align: start;
		cursor: pointer;
	}

	.language:hover {
		background: var(--bg-subtle);
	}

	.language.current {
		background: var(--accent-soft);
		color: var(--accent);
		font-weight: 800;
	}
</style>

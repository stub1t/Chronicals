<script lang="ts">
	import { themeStore } from '$lib/stores/theme.js';
	import { LANGUAGES, localeStore, setLanguage, type LanguageCode, t } from '$lib/i18n';
	import { onMount } from 'svelte';
	import { exportBackup, importBackup } from '$lib/backup.js';
	import { loadReminderSettings, saveReminderSettings, type ReminderSettings } from '$lib/reminders.js';
	import { isAppLockEnabled } from '$lib/app-lock.js';

	type ThemeOption = 'system' | 'light' | 'dark';

	let currentTheme = $state<ThemeOption>('system');
	let currentLanguage = $state<LanguageCode>('en');

	onMount(() => {
		currentTheme = themeStore.current;
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

	function setTheme(t: ThemeOption) {
		currentTheme = t;
		themeStore.set(t);
	}

	async function chooseLanguage(code: LanguageCode) {
		currentLanguage = code;
		await setLanguage(code);
	}

	// ── Reminders (Android app) ───────────────────────────────────────────────
	let reminders = $state<ReminderSettings>({ daily: false, time: '20:00' });
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

	let version = $state(__APP_VERSION__);

	// ── App lock (Android app) ────────────────────────────────────────────────
	let appLock = $state(false);
	let appLockMessage = $state<string | null>(null);

	async function toggleAppLock(event: Event & { currentTarget: HTMLInputElement }) {
		const checkbox = event.currentTarget;
		appLockMessage = null;
		if (__NATIVE_APP__) {
			const { enableAppLock, disableAppLock } = await import('$lib/mobile/app-lock.js');
			if (checkbox.checked) {
				const result = await enableAppLock();
				if (result === 'unavailable') appLockMessage = t('settings_app_lock_unavailable');
			} else {
				await disableAppLock();
			}
		}
		// Reflect what actually happened (a cancelled prompt leaves it unchanged).
		appLock = isAppLockEnabled();
		checkbox.checked = appLock;
	}

	// ── Backup ────────────────────────────────────────────────────────────────
	let fileInput: HTMLInputElement;
	let dataBusy = $state(false);
	let dataMessage = $state<string | null>(null);
	let dataError = $state(false);

	async function handleExport() {
		dataBusy = true;
		dataMessage = null;
		try {
			await exportBackup();
		} catch (err) {
			dataError = true;
			dataMessage = t('settings_export_failed', { message: (err as Error).message });
		} finally {
			dataBusy = false;
		}
	}

	async function handleImport() {
		const file = fileInput.files?.[0];
		fileInput.value = '';
		if (!file || !confirm(t('settings_import_confirm', { name: file.name }))) return;

		dataBusy = true;
		dataMessage = null;
		try {
			const count = await importBackup(file);
			dataError = false;
			dataMessage = t('settings_import_done', { count });
			setTimeout(() => location.reload(), 1500);
		} catch (err) {
			dataError = true;
			dataMessage = t('settings_import_failed', { message: (err as Error).message });
			dataBusy = false;
		}
	}
</script>

<div class="page">
	<header class="page-header">
		<h1 class="page-title">{t('settings_title')}</h1>
	</header>

	<!-- Nav cards -->
	<nav class="nav-list" aria-label={t('settings_sections')}>
		<a href="/me/profile" class="nav-card">
			<span class="nav-icon" aria-hidden="true">
				<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
					<circle cx="10" cy="7" r="3"/>
					<path d="M3 18c0-3.87 3.13-7 7-7s7 3.13 7 7"/>
				</svg>
			</span>
			<div class="nav-text">
				<span class="nav-label">{t('settings_profile')}</span>
				<span class="nav-desc">{t('settings_profile_desc')}</span>
			</div>
			<span class="nav-arrow" aria-hidden="true">›</span>
		</a>
		<a href="/me/medications" class="nav-card">
			<span class="nav-icon" aria-hidden="true">
				<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
					<rect x="4" y="2" width="12" height="16" rx="2"/>
					<path d="M8 7h4M8 11h4M8 15h2"/>
				</svg>
			</span>
			<div class="nav-text">
				<span class="nav-label">{t('settings_meds')}</span>
				<span class="nav-desc">{t('settings_meds_desc')}</span>
			</div>
			<span class="nav-arrow" aria-hidden="true">›</span>
		</a>
		<a href="/me/entry-types" class="nav-card">
			<span class="nav-icon" aria-hidden="true">
				<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
					<circle cx="10" cy="10" r="3"/>
					<circle cx="10" cy="10" r="8"/>
				</svg>
			</span>
			<div class="nav-text">
				<span class="nav-label">{t('settings_entry_types')}</span>
				<span class="nav-desc">{t('settings_entry_types_desc')}</span>
			</div>
			<span class="nav-arrow" aria-hidden="true">›</span>
		</a>
		<a href="/me/triggers" class="nav-card">
			<span class="nav-icon" aria-hidden="true">
				<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
					<path d="M3 6h14M5 10h10M7 14h6"/>
				</svg>
			</span>
			<div class="nav-text">
				<span class="nav-label">{t('settings_triggers')}</span>
				<span class="nav-desc">{t('settings_triggers_desc')}</span>
			</div>
			<span class="nav-arrow" aria-hidden="true">›</span>
		</a>
	</nav>

	<!-- Theme toggle -->
	<section class="theme-section" aria-label={t('settings_theme')}>
		<p class="theme-title">{t('settings_theme')}</p>
		<div class="theme-pills" role="group" aria-label={t('settings_choose_theme')}>
			{#each ([
				{ value: 'system', label: t('settings_theme_system') },
				{ value: 'light', label: t('settings_theme_light') },
				{ value: 'dark', label: t('settings_theme_dark') }
			] as const) as opt (opt.value)}
				<button
					class="theme-pill"
					class:active={currentTheme === opt.value}
					onclick={() => setTheme(opt.value)}
					aria-pressed={currentTheme === opt.value}
				>
					{opt.label}
				</button>
			{/each}
		</div>
	</section>

	<section class="theme-section" aria-label={t('settings_language')}>
		<p class="theme-title">{t('settings_language')}</p>
		<select
			class="language-select"
			aria-label={t('settings_choose_language')}
			value={currentLanguage}
			onchange={(e) => chooseLanguage(e.currentTarget.value as LanguageCode)}
		>
			{#each LANGUAGES as lang (lang.code)}
				<option value={lang.code} lang={lang.code} dir={lang.dir}>{lang.name}</option>
			{/each}
		</select>
	</section>

	{#if __NATIVE_APP__}
		<section class="theme-section" aria-label={t('settings_reminders')}>
			<p class="theme-title">{t('settings_reminders')}</p>
			<div class="reminder-row">
				<label class="switch-label">
					<input
						type="checkbox"
						checked={reminders.daily}
						onchange={(e) => updateReminders({ ...reminders, daily: e.currentTarget.checked })}
					/>
					{t('settings_reminder_daily')}
				</label>
				<input
					class="time-input"
					type="time"
					value={reminders.time}
					disabled={!reminders.daily}
					aria-label={t('settings_reminder_time')}
					onchange={(e) => e.currentTarget.value && updateReminders({ ...reminders, time: e.currentTarget.value })}
				/>
			</div>
			<p class="data-note">{t('settings_reminder_daily_note')}</p>
			{#if reminderDenied}
				<p class="data-message error" role="alert">{t('settings_reminder_denied')}</p>
			{/if}
		</section>
	{/if}

	{#if __NATIVE_APP__}
		<section class="theme-section" aria-label={t('settings_app_lock')}>
			<p class="theme-title">{t('settings_app_lock')}</p>
			<div class="reminder-row">
				<label class="switch-label">
					<input type="checkbox" checked={appLock} onchange={toggleAppLock} />
					{t('settings_app_lock_toggle')}
				</label>
			</div>
			<p class="data-note">{t('settings_app_lock_note')}</p>
			{#if appLockMessage}
				<p class="data-message error" role="alert">{appLockMessage}</p>
			{/if}
		</section>
	{/if}

	<section class="theme-section" aria-label={t('settings_data')}>
		<p class="theme-title">{t('settings_data')}</p>
		<p class="data-note">{t('settings_data_note')}</p>
		<div class="theme-pills">
			<button class="theme-pill" onclick={handleExport} disabled={dataBusy}>{t('settings_export')}</button>
			<button class="theme-pill" onclick={() => fileInput.click()} disabled={dataBusy}>{t('settings_import')}</button>
		</div>
		<!-- No accept filter in the app: Android's file picker has no type for .db and would hide the file. -->
		<input
			type="file"
			hidden
			accept={__NATIVE_APP__ ? undefined : '.db,.sqlite,.sqlite3'}
			bind:this={fileInput}
			onchange={handleImport}
		/>
		{#if dataBusy && !dataMessage}
			<p class="data-note">{t('settings_working')}</p>
		{/if}
		{#if dataMessage}
			<p class="data-message" class:error={dataError} role="status">{dataMessage}</p>
		{/if}
	</section>

	<section class="theme-section" aria-label={t('settings_about')}>
		<p class="theme-title">{t('settings_about')}</p>
		<div class="about">
			<p><strong>Chronicals</strong> {version}</p>
			<p>{t('settings_license')}</p>
			<a href="https://github.com/stub1t/Chronicals" target="_blank" rel="noopener noreferrer">{t('settings_source')}</a>
		</div>
	</section>
</div>

<style>
	.page {
		max-width: 560px;
		margin: 0 auto;
		padding: 24px 16px 80px;
		display: flex;
		flex-direction: column;
		gap: 24px;
	}

	.page-header {
		margin-bottom: 4px;
	}

	.page-title {
		font-size: 1.6rem;
		font-weight: 800;
		color: var(--text);
		margin: 0;
	}

	/* Nav cards */
	.nav-list {
		display: flex;
		flex-direction: column;
		gap: 1px;
		background: var(--border);
		border: 1px solid var(--border);
		border-radius: 16px;
		overflow: hidden;
	}

	.nav-card {
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 16px;
		background: var(--bg-card);
		text-decoration: none;
		color: var(--text);
		transition: background 0.15s;
	}

	.nav-card:hover {
		background: var(--bg-subtle);
	}

	.nav-card:first-child {
		border-radius: 15px 15px 0 0;
	}

	.nav-card:last-child {
		border-radius: 0 0 15px 15px;
	}

	.nav-icon {
		width: 36px;
		height: 36px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--bg-subtle);
		border-radius: 10px;
		flex-shrink: 0;
		color: var(--text-muted);
	}

	.nav-icon svg {
		width: 18px;
		height: 18px;
	}

	.nav-text {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.nav-label {
		font-size: 0.95rem;
		font-weight: 600;
	}

	.nav-desc {
		font-size: 0.78rem;
		color: var(--text-muted);
	}

	.nav-arrow {
		font-size: 1.3rem;
		color: var(--text-subtle);
	}

	/* Theme toggle */
	.theme-section {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.theme-title {
		font-size: 0.82rem;
		font-weight: 700;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.05em;
		margin: 0;
	}

	.theme-pills {
		display: flex;
		gap: 8px;
	}

	.theme-pill {
		flex: 1;
		padding: 10px 0;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 20px;
		font-family: inherit;
		font-size: 0.88rem;
		font-weight: 500;
		color: var(--text-muted);
		cursor: pointer;
		transition: all 0.15s;
	}

	.theme-pill:hover {
		border-color: var(--text-muted);
		color: var(--text);
	}

	.language-select {
		min-height: 46px;
		padding: 0 var(--space-4);
		border: 1.5px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-md);
	}

	.theme-pill:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.reminder-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		padding: 10px 14px;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 14px;
	}

	.switch-label {
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 0.92rem;
		color: var(--text);
	}

	.switch-label input {
		width: 20px;
		height: 20px;
		accent-color: var(--text);
	}

	.time-input {
		font-family: inherit;
		font-size: 0.92rem;
		padding: 6px 8px;
		border: 1px solid var(--border);
		border-radius: 8px;
		background: var(--bg);
		color: var(--text);
	}

	.time-input:disabled {
		opacity: 0.5;
	}

	.about {
		display: flex;
		flex-direction: column;
		gap: 4px;
		font-size: 0.85rem;
		color: var(--text-muted);
	}

	.about p {
		margin: 0;
	}

	.about strong {
		color: var(--text);
	}

	.about a {
		color: var(--text);
	}

	.data-note {
		font-size: 0.82rem;
		color: var(--text-muted);
		margin: 0;
	}

	.data-message {
		font-size: 0.85rem;
		color: var(--text);
		margin: 0;
	}

	.data-message.error {
		color: #dc2626;
	}

	.theme-pill.active {
		background: var(--text);
		color: var(--bg);
		border-color: var(--text);
		font-weight: 700;
	}
</style>

<script lang="ts">
	import { browser } from '$app/environment';
	import type { Entry, EntryType, Trigger, EnvironmentalData, EntryTypeLog } from '$lib/types';
	import { localizeBuiltInText, t } from '$lib/stores/locale.js';
	import { formatTime, formatDuration, formatFullDate } from '$lib/utils/date';
	import EnvironmentalCard from '$lib/components/entry/EnvironmentalCard.svelte';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';
	import EntryTypePicker from '$lib/components/entry/EntryTypePicker.svelte';
	import { fetchOrQueue } from '$lib/utils/offline-queue';
	import MetricForm from '$lib/components/symptom/MetricForm.svelte';

	interface Props {
		date: string;
		entryTypes: EntryType[];
		allTriggers: Trigger[];
		onclose: () => void;
	}

	let { date, entryTypes, allTriggers: allTriggersProp, onclose }: Props = $props();
	let allTriggers = $state(allTriggersProp);

	let entry = $state<Entry | null>(null);
	let showTypePicker = $state(false);
	let activeTab = $state<'logs' | 'triggers' | 'note'>('logs');
	let loading = $state(true);
	let envLoading = $state(true);
	let envData = $state<EnvironmentalData | null>(null);
	let noteValue = $state('');
	let noteDebounceTimer: ReturnType<typeof setTimeout> | null = null;
	let envExpanded = $state(false);
	let drawerVisible = $state(false);

	// Log editing modal
	let editingLog = $state<EntryTypeLog | null>(null);
	let editValues = $state<Record<string, unknown>>({});
	let editStartTime = $state('');
	let editEndTime = $state('');

	// Trigger slide-in animation on mount
	$effect(() => {
		if (browser) {
			requestAnimationFrame(() => {
				drawerVisible = true;
			});
		}
	});

	// Fetch entry and env data whenever date changes
	$effect(() => {
		if (!browser || !date) return;
		loadEntry();
		loadEnvData();
	});

	async function loadEntry() {
		loading = true;
		try {
			const month = date.slice(0, 7);
			const res = await fetch(`/api/entries?month=${month}`);
			if (res.ok) {
				const entries: Entry[] = await res.json();
				const found = entries.find((e) => e.date === date) ?? null;
				entry = found;
				noteValue = found?.note ?? '';
			}
		} catch (e) {
			console.error('Failed to load entry', e);
		} finally {
			loading = false;
		}
	}

	async function loadEnvData() {
		envLoading = true;
		try {
			const res = await fetch(`/api/environmental?date=${date}`);
			if (res.ok) {
				envData = await res.json();
			} else {
				envData = null;
			}
		} catch {
			envData = null;
		} finally {
			envLoading = false;
		}
	}

	async function ensureEntry(): Promise<Entry> {
		if (entry) return entry;
		const res = await fetch('/api/entries', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ date })
		});
		if (!res.ok) throw new Error('Failed to create entry');
		const created: Entry = await res.json();
		entry = { ...created, logs: [], triggers: [] };
		noteValue = created.note ?? '';
		return entry;
	}

	async function handleTypeSelected(entryType: EntryType) {
		showTypePicker = false;
		try {
			const e = await ensureEntry();
			const now = new Date().toISOString();
			const res = await fetch('/api/entry-type-logs', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					entryId: e.id,
					entryTypeId: entryType.id,
					startTime: now,
					metricValues: {}
				})
			});
			if (res.ok) {
				const newLog = await res.json();
				const fullLog = { ...newLog, entryType };
				if (entry) {
					entry = { ...entry, logs: [...(entry.logs ?? []), fullLog] };
				}
				openLogEditor(fullLog);
			}
		} catch (err) {
			console.error('Failed to create log', err);
		}
	}

	async function deleteLog(logId: string) {
		try {
			const res = await fetchOrQueue(`/api/entry-type-logs/${logId}`, { method: 'DELETE' });
			if (res === null || res.ok) {
				// null = queued for offline replay; optimistically update UI
				if (entry) {
					entry = { ...entry, logs: entry.logs?.filter((l) => l.id !== logId) ?? [] };
				}
			}
		} catch (err) {
			console.error('Failed to delete log', err);
		}
	}

	function isTriggerActive(triggerId: string): boolean {
		return entry?.triggers?.some((t) => t.triggerId === triggerId) ?? false;
	}

	let newFoodInput = $state('');
	let newCustomInput = $state('');

	async function createAndToggleTrigger(name: string, category: string) {
		const trimmed = name.trim();
		if (!trimmed) return;
		try {
			const res = await fetch('/api/triggers', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ name: trimmed, category })
			});
			if (!res.ok) return;
			const trigger = await res.json();
			allTriggers = [...allTriggers, trigger];
			await toggleTrigger(trigger.id);
		} catch (err) {
			console.error('Failed to create trigger', err);
		}
	}

	async function toggleTrigger(triggerId: string) {
		if (!entry) {
			try {
				await ensureEntry();
			} catch {
				return;
			}
		}
		if (!entry) return;

		const isActive = isTriggerActive(triggerId);
		const action = isActive ? 'remove_trigger' : 'add_trigger';

		try {
			const res = await fetchOrQueue(`/api/entries/${entry.id}`, {
				method: 'PATCH',
				body: { action, triggerId }
			});
			if (res === null || res.ok) {
				await loadEntry();
			}
		} catch (err) {
			console.error('Failed to toggle trigger', err);
		}
	}

	function handleNoteInput(e: Event) {
		const target = e.target as HTMLTextAreaElement;
		noteValue = target.value;
		if (noteDebounceTimer) clearTimeout(noteDebounceTimer);
		noteDebounceTimer = setTimeout(() => saveNote(), 800);
	}

	async function saveNote() {
		if (!entry) {
			if (!noteValue.trim()) return;
			try {
				await ensureEntry();
			} catch {
				return;
			}
		}
		if (!entry) return;
		try {
			await fetchOrQueue(`/api/entries/${entry.id}`, {
				method: 'PUT',
				body: { note: noteValue }
			});
		} catch (err) {
			console.error('Failed to save note', err);
		}
	}

	function openLogEditor(log: EntryTypeLog) {
		editingLog = log;
		editValues = { ...(log.metricValues ?? {}) };
		// Format ISO datetime to datetime-local string (YYYY-MM-DDTHH:MM)
		editStartTime = log.startTime ? log.startTime.slice(0, 16) : '';
		editEndTime = log.endTime ? log.endTime.slice(0, 16) : '';
	}

	function closeLogEditor() {
		editingLog = null;
	}

	async function saveLogEdit() {
		if (!editingLog) return;
		const body: Record<string, unknown> = {
			metricValues: editValues,
			startTime: editStartTime ? new Date(editStartTime).toISOString() : editingLog.startTime,
			endTime: editEndTime ? new Date(editEndTime).toISOString() : null
		};
		try {
			const res = await fetch(`/api/entry-type-logs/${editingLog.id}`, {
				method: 'PUT',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify(body)
			});
			if (res.ok) {
				closeLogEditor();
				await loadEntry();
			}
		} catch (err) {
			console.error('Failed to save log', err);
		}
	}

	function getEntryTypeForLog(log: EntryTypeLog): EntryType | undefined {
		return log.entryType ?? entryTypes.find((et) => et.id === log.entryTypeId);
	}

	let ongoingLogs = $derived((entry?.logs ?? []).filter((log) => !log.endTime));

	function formatMetricSummary(log: EntryTypeLog): string {
		const parts: string[] = [];
		const et = getEntryTypeForLog(log);
		if (!et) return '';
		for (const field of et.metricSchema.fields) {
			const val = log.metricValues[field.key];
			if (val !== undefined && val !== null && val !== '') {
				if (field.type === 'slider') {
					parts.push(`${localizeBuiltInText(field.label)}: ${val}`);
				} else if (field.type === 'select' || field.type === 'text') {
					parts.push(localizeBuiltInText(String(val)));
				} else if (field.type === 'checklist' && Array.isArray(val)) {
					parts.push(val.map((item) => localizeBuiltInText(String(item))).join(', '));
				}
			}
		}
		if (log.intensityCache !== null) {
			parts.unshift(t('drawer_strength', { value: log.intensityCache }));
		}
		return parts.slice(0, 2).join(' · ');
	}

	// Group triggers by category
	let triggersByCategory = $derived.by(() => {
		const map = new Map<string, Trigger[]>();
		for (const t of allTriggers) {
			const list = map.get(t.category) ?? [];
			list.push(t);
			map.set(t.category, list);
		}
		return map;
	});

	const TRIGGER_CATEGORY_LABELS: Record<string, string> = {
		food: t('trigger_food'),
		activity: t('trigger_activity'),
		custom: t('trigger_custom')
	};

	const TRIGGER_CATEGORY_ORDER = ['food', 'activity', 'custom'];

	function handleBackdropClick(e: MouseEvent) {
		if ((e.target as HTMLElement).classList.contains('drawer-backdrop')) {
			handleClose();
		}
	}

	function handleClose() {
		drawerVisible = false;
		setTimeout(() => onclose(), 280);
	}
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="drawer-backdrop"
	class:visible={drawerVisible}
	onclick={handleBackdropClick}
	role="presentation"
>
	<div
		class="drawer"
		class:visible={drawerVisible}
		role="dialog"
		aria-modal="true"
		aria-label={t('drawer_for_date', { date })}
	>
		<!-- Drag handle -->
		<div class="drag-handle" aria-hidden="true">
			<div class="handle-pill"></div>
		</div>

		<!-- Date header -->
		<div class="drawer-header">
			<div class="header-top">
				<h2 class="date-heading">{formatFullDate(date)}</h2>
				<button class="close-btn" onclick={handleClose} aria-label={t('drawer_close')}>
					<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
						<path d="M3 3l10 10M13 3L3 13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
					</svg>
				</button>
			</div>

			<!-- Environmental card (collapsed by default) -->
			<div class="env-section">
				<button
					class="env-toggle"
					onclick={() => (envExpanded = !envExpanded)}
					aria-expanded={envExpanded}
					aria-label={t('drawer_toggle_environment')}
				>
					<span class="env-toggle-label">{t('drawer_weather')}</span>
					<svg
						class="env-chevron"
						class:rotated={envExpanded}
						width="14"
						height="14"
						viewBox="0 0 14 14"
						fill="none"
						aria-hidden="true"
					>
						<path d="M3 5l4 4 4-4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
					</svg>
				</button>
				{#if envExpanded}
					<div class="env-card-wrap">
						<EnvironmentalCard data={envData} loading={envLoading} />
					</div>
				{/if}
			</div>
		</div>

		<!-- Tabs -->
		<div class="tabs" role="tablist">
			<button
				class="tab"
				class:active={activeTab === 'logs'}
				role="tab"
				aria-selected={activeTab === 'logs'}
				onclick={() => (activeTab = 'logs')}
			>
				{t('drawer_logs')}
			</button>
			<button
				class="tab"
				class:active={activeTab === 'triggers'}
				role="tab"
				aria-selected={activeTab === 'triggers'}
				onclick={() => (activeTab = 'triggers')}
			>
				{t('drawer_triggers')}
			</button>
			<button
				class="tab"
				class:active={activeTab === 'note'}
				role="tab"
				aria-selected={activeTab === 'note'}
				onclick={() => (activeTab = 'note')}
			>
				{t('drawer_note')}
			</button>
		</div>

		<!-- Tab content -->
		<div class="tab-content">
			<!-- LOGS TAB -->
			{#if activeTab === 'logs'}
				<div class="logs-panel" role="tabpanel">
					{#if ongoingLogs.length > 0}
						<div class="ongoing-reminder">
							{ongoingLogs.length === 1 ? t('drawer_ongoing_one') : t('drawer_ongoing_many', { count: ongoingLogs.length })}
						</div>
					{/if}
					{#if loading}
						<div class="skeleton-list">
							{#each [1, 2] as i (i)}
								<div class="skeleton-log-card"></div>
							{/each}
						</div>
					{:else if !entry?.logs || entry.logs.length === 0}
						<div class="empty-state">
							<p class="empty-text">{t('drawer_empty_logs')}</p>
							<p class="empty-subtext">{t('drawer_empty_logs_hint')}</p>
						</div>
					{:else}
						<ul class="logs-list" role="list">
							{#each entry.logs as log (log.id)}
								{@const et = getEntryTypeForLog(log)}
								{@const summary = formatMetricSummary(log)}
								<li class="log-card">
									<button class="log-card-btn" onclick={() => openLogEditor(log)} aria-label={t('drawer_edit_entry', { name: et ? localizeBuiltInText(et.name) : t('drawer_unknown') })}>
										<div
											class="log-color-bar"
											style={et?.color ? `background: ${et.color}` : 'background: var(--border)'}
										></div>
										<div class="log-body">
											<div class="log-title-row">
												<EntryTypeIcon entryType={et ?? null} size={18} class="log-icon" />
												<span class="log-name">{et ? localizeBuiltInText(et.name) : t('drawer_unknown')}</span>
												<span class="log-time">{formatTime(log.startTime)}</span>
												{#if log.endTime}
													<span class="log-duration">
														· {formatDuration(log.startTime, log.endTime)}
													</span>
												{/if}
											</div>
											{#if summary}
												<p class="log-metrics">{summary}</p>
											{/if}
										</div>
									</button>
									<button
										class="log-delete-btn"
										onclick={() => deleteLog(log.id)}
										aria-label={t('drawer_delete_entry', { name: et ? localizeBuiltInText(et.name) : t('drawer_unknown') })}
									>
										<svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
											<path d="M2 4h10M5 4V2.5h4V4M6 6.5v4M8 6.5v4M3 4l.7 7.5h6.6L11 4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
										</svg>
									</button>
								</li>
							{/each}
						</ul>
					{/if}

					<!-- Add log button inside tab -->
					<button class="add-log-btn" onclick={() => (showTypePicker = true)} aria-label={t('drawer_add_log')}>
						<svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
							<path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
						</svg>
						{t('drawer_add_log')}
					</button>
				</div>
			{/if}

			<!-- TRIGGERS TAB -->
			{#if activeTab === 'triggers'}
				<div class="triggers-panel" role="tabpanel">
					{#each TRIGGER_CATEGORY_ORDER as cat (cat)}
						{@const catTriggers = triggersByCategory.get(cat) ?? []}
						{#if catTriggers.length > 0 || cat === 'food' || cat === 'custom'}
							<div class="trigger-group">
								<h3 class="trigger-group-label">{TRIGGER_CATEGORY_LABELS[cat]}</h3>
								{#if catTriggers.length > 0}
									<div class="trigger-chips">
										{#each catTriggers as trigger (trigger.id)}
											{@const active = isTriggerActive(trigger.id)}
											<button
												class="trigger-chip"
												class:active
												onclick={() => toggleTrigger(trigger.id)}
												aria-pressed={active}
											>
												{localizeBuiltInText(trigger.name)}
											</button>
										{/each}
									</div>
								{/if}
								{#if cat === 'food' || cat === 'custom'}
									{@const inputValue = cat === 'food' ? newFoodInput : newCustomInput}
									<div class="trigger-add-row">
										<input
											type="text"
											class="trigger-add-input"
											placeholder={cat === 'food' ? t('drawer_add_food') : t('drawer_add_custom_trigger')}
											value={inputValue}
											oninput={(e) => { if (cat === 'food') newFoodInput = (e.target as HTMLInputElement).value; else newCustomInput = (e.target as HTMLInputElement).value; }}
											onkeydown={(e) => { if (e.key === 'Enter') { e.preventDefault(); if (cat === 'food') { createAndToggleTrigger(newFoodInput, 'food'); newFoodInput = ''; } else { createAndToggleTrigger(newCustomInput, 'custom'); newCustomInput = ''; } } }}
											maxlength={64}
										/>
										<button
											type="button"
											class="trigger-add-btn"
											disabled={!inputValue.trim()}
											onclick={() => { if (cat === 'food') { createAndToggleTrigger(newFoodInput, 'food'); newFoodInput = ''; } else { createAndToggleTrigger(newCustomInput, 'custom'); newCustomInput = ''; } }}
											aria-label={t('drawer_add_trigger')}
										>+</button>
									</div>
								{/if}
							</div>
						{/if}
					{/each}
				</div>
			{/if}

			<!-- NOTE TAB -->
			{#if activeTab === 'note'}
				<div class="note-panel" role="tabpanel">
					<textarea
						class="note-textarea"
						placeholder={t('drawer_note_placeholder')}
						value={noteValue}
						oninput={handleNoteInput}
						rows="6"
						aria-label={t('drawer_note')}
					></textarea>
					<p class="note-hint">{t('drawer_note_saved')}</p>
				</div>
			{/if}
		</div>

		<!-- Floating action button -->
		<button
			class="fab"
			onclick={() => (showTypePicker = true)}
			aria-label={t('drawer_add_log')}
		>
			<svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
				<path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>
			</svg>
		</button>
	</div>
</div>

<!-- Entry type picker overlay -->
{#if showTypePicker}
	<EntryTypePicker
		{entryTypes}
		onselect={handleTypeSelected}
		onclose={() => (showTypePicker = false)}
	/>
{/if}

<!-- Log edit modal -->
{#if editingLog}
	{@const et = getEntryTypeForLog(editingLog)}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div class="modal-backdrop" onclick={(e) => { if ((e.target as HTMLElement).classList.contains('modal-backdrop')) closeLogEditor(); }} role="presentation">
		<div class="modal" role="dialog" aria-modal="true" aria-label={t('drawer_edit_entry', { name: et ? localizeBuiltInText(et.name) : t('drawer_unknown') })}>
			<div class="modal-header">
				<EntryTypeIcon entryType={et ?? null} size={20} class="modal-icon" />
				<h3 class="modal-title">{et ? localizeBuiltInText(et.name) : t('drawer_unknown')}</h3>
				<button class="close-btn" onclick={closeLogEditor} aria-label={t('close')}>
					<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
						<path d="M3 3l10 10M13 3L3 13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
					</svg>
				</button>
			</div>

			<div class="modal-body">
				<!-- Time pickers -->
				<div class="time-row">
					<div class="time-field">
						<label class="time-label" for="edit-start-time">{t('drawer_start')}</label>
						<input
							id="edit-start-time"
							class="time-input"
							type="datetime-local"
							bind:value={editStartTime}
						/>
					</div>
					<div class="time-field">
						<label class="time-label" for="edit-end-time">{t('drawer_end_optional')}</label>
						<input
							id="edit-end-time"
							class="time-input"
							type="datetime-local"
							bind:value={editEndTime}
						/>
					</div>
				</div>

				<!-- Dynamic metric fields -->
				{#if et?.metricSchema?.fields?.length}
					<MetricForm
						schema={et.metricSchema}
						values={editValues}
						onchange={(key, val) => { editValues = { ...editValues, [key]: val }; }}
					/>
				{/if}
			</div>

			<div class="modal-footer">
				<button class="btn-cancel" onclick={closeLogEditor}>{t('cancel')}</button>
				<button class="btn-save" onclick={saveLogEdit}>{t('save')}</button>
			</div>
		</div>
	</div>
{/if}

<style>
	.drawer-backdrop {
		position: fixed;
		inset: 0;
		background: rgba(0, 0, 0, 0);
		z-index: 50;
		transition: background 0.28s ease;
		pointer-events: none;
	}

	.drawer-backdrop.visible {
		background: rgba(0, 0, 0, 0.4);
		pointer-events: auto;
	}

	.drawer {
		position: fixed;
		bottom: 0;
		left: 0;
		right: 0;
		background: var(--bg-card);
		border-radius: 20px 20px 0 0;
		max-height: 85dvh;
		display: flex;
		flex-direction: column;
		box-shadow: 0 -4px 32px rgba(0, 0, 0, 0.18);
		transform: translateY(100%);
		transition: transform 0.28s cubic-bezier(0.32, 0.72, 0, 1);
		will-change: transform;
		overflow: hidden;
	}

	.drawer.visible {
		transform: translateY(0);
	}

	.drag-handle {
		display: flex;
		justify-content: center;
		padding: 10px 0 4px;
		flex-shrink: 0;
	}

	.handle-pill {
		width: 40px;
		height: 4px;
		background: var(--border);
		border-radius: 2px;
	}

	.drawer-header {
		padding: 4px 20px 12px;
		flex-shrink: 0;
		border-bottom: 1px solid var(--border);
	}

	.header-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 10px;
	}

	.date-heading {
		font-size: 1.1rem;
		font-weight: 700;
		color: var(--text);
		margin: 0;
	}

	.close-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 32px;
		height: 32px;
		border-radius: 50%;
		background: var(--bg-subtle);
		border: none;
		color: var(--text-muted);
		cursor: pointer;
		transition: background 0.15s;
		flex-shrink: 0;
	}

	.close-btn:hover {
		background: var(--border);
	}

	.env-section {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.env-toggle {
		display: flex;
		align-items: center;
		justify-content: space-between;
		background: var(--bg-subtle);
		border: 1px solid var(--border);
		border-radius: 8px;
		padding: 7px 12px;
		cursor: pointer;
		font-family: inherit;
		font-size: 0.82rem;
		color: var(--text-muted);
		transition: background 0.15s;
		width: 100%;
	}

	.env-toggle:hover {
		background: var(--border);
	}

	.env-toggle-label {
		display: flex;
		align-items: center;
		gap: 6px;
		font-weight: 500;
	}

	.env-chevron {
		transition: transform 0.2s ease;
		flex-shrink: 0;
	}

	.env-chevron.rotated {
		transform: rotate(180deg);
	}

	.env-card-wrap {
		animation: slideDown 0.18s ease;
	}

	@keyframes slideDown {
		from { opacity: 0; transform: translateY(-6px); }
		to { opacity: 1; transform: translateY(0); }
	}

	/* Tabs */
	.tabs {
		display: flex;
		flex-shrink: 0;
		border-bottom: 1px solid var(--border);
		padding: 0 12px;
	}

	.tab {
		flex: 1;
		padding: 11px 8px;
		background: transparent;
		border: none;
		border-bottom: 2px solid transparent;
		font-family: inherit;
		font-size: 0.82rem;
		font-weight: 500;
		color: var(--text-muted);
		cursor: pointer;
		transition: color 0.15s, border-color 0.15s;
		white-space: nowrap;
	}

	.tab.active {
		color: var(--text);
		border-bottom-color: var(--text);
		font-weight: 600;
	}

	.tab:hover:not(.active) {
		color: var(--text);
	}

	/* Tab content */
	.tab-content {
		flex: 1;
		overflow-y: auto;
		overscroll-behavior: contain;
		padding-bottom: 80px;
	}

	/* LOGS panel */
	.logs-panel {
		padding: 16px 16px 8px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.ongoing-reminder {
		padding: 10px 12px;
		border-radius: 12px;
		background: #fff7ed;
		border: 1px solid #fdba74;
		color: #9a3412;
		font-size: 0.82rem;
		line-height: 1.4;
	}

	.skeleton-list {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.skeleton-log-card {
		height: 64px;
		background: var(--bg-subtle);
		border-radius: 10px;
		animation: shimmer 1.4s ease-in-out infinite;
	}

	@keyframes shimmer {
		0% { opacity: 0.4; }
		50% { opacity: 0.8; }
		100% { opacity: 0.4; }
	}

	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 6px;
		padding: 32px 16px;
		text-align: center;
	}

	.empty-icon {
		font-size: 2.5rem;
		line-height: 1;
		margin-bottom: 4px;
		opacity: 0.6;
	}

	.empty-text {
		font-size: 0.9rem;
		font-weight: 600;
		color: var(--text-muted);
		margin: 0;
	}

	.empty-subtext {
		font-size: 0.8rem;
		color: var(--text-subtle);
		margin: 0;
	}

	.logs-list {
		list-style: none;
		padding: 0;
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.log-card {
		display: flex;
		align-items: stretch;
		background: var(--bg);
		border: 1px solid var(--border);
		border-radius: 10px;
		overflow: hidden;
		transition: background 0.15s;
	}

	.log-card:hover {
		background: var(--bg-subtle);
	}

	.log-card-btn {
		display: flex;
		align-items: stretch;
		flex: 1;
		background: none;
		border: none;
		padding: 0;
		cursor: pointer;
		text-align: left;
		min-width: 0;
	}

	.log-color-bar {
		width: 4px;
		flex-shrink: 0;
	}

	.log-body {
		flex: 1;
		padding: 10px 12px;
		min-width: 0;
	}

	.log-title-row {
		display: flex;
		align-items: center;
		gap: 6px;
		flex-wrap: wrap;
	}

	.log-icon {
		flex-shrink: 0;
	}

	.log-name {
		font-size: 0.88rem;
		font-weight: 600;
		color: var(--text);
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.log-time {
		font-size: 0.75rem;
		color: var(--text-muted);
		flex-shrink: 0;
	}

	.log-duration {
		font-size: 0.75rem;
		color: var(--text-subtle);
	}

	.log-metrics {
		font-size: 0.75rem;
		color: var(--text-muted);
		margin: 4px 0 0;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.log-delete-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 40px;
		background: transparent;
		border: none;
		border-left: 1px solid var(--border);
		color: var(--text-subtle);
		cursor: pointer;
		flex-shrink: 0;
		transition: background 0.15s, color 0.15s;
	}

	.log-delete-btn:hover {
		background: rgba(239, 68, 68, 0.1);
		color: #ef4444;
	}

	.add-log-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		width: 100%;
		padding: 12px;
		background: var(--bg-subtle);
		border: 1.5px dashed var(--border);
		border-radius: 10px;
		font-family: inherit;
		font-size: 0.85rem;
		font-weight: 500;
		color: var(--text-muted);
		cursor: pointer;
		transition: background 0.15s, color 0.15s, border-color 0.15s;
	}

	.add-log-btn:hover {
		background: var(--bg-card);
		color: var(--text);
		border-color: var(--text-subtle);
	}

	/* TRIGGERS panel */
	.triggers-panel {
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	.trigger-group {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.trigger-group-label {
		font-size: 0.7rem;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		color: var(--text-subtle);
		margin: 0;
	}

	.trigger-chips {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}

	.trigger-chip {
		padding: 6px 14px;
		background: var(--bg);
		border: 1.5px solid var(--border);
		border-radius: 20px;
		font-family: inherit;
		font-size: 0.82rem;
		color: var(--text-muted);
		cursor: pointer;
		transition: all 0.15s;
	}

	.trigger-chip:hover {
		border-color: var(--text-subtle);
		color: var(--text);
	}

	.trigger-chip.active {
		background: var(--text);
		border-color: var(--text);
		color: var(--bg);
		font-weight: 600;
	}

	.trigger-add-row {
		display: flex;
		gap: 6px;
		margin-top: 8px;
	}

	.trigger-add-input {
		flex: 1;
		padding: 6px 10px;
		border: 1px solid var(--border);
		border-radius: 8px;
		background: var(--bg);
		color: var(--text);
		font-family: inherit;
		font-size: 0.85rem;
		min-width: 0;
	}

	.trigger-add-input::placeholder {
		color: var(--text-subtle);
	}

	.trigger-add-input:focus {
		outline: none;
		border-color: var(--text-muted);
	}

	.trigger-add-btn {
		width: 32px;
		height: 32px;
		border-radius: 8px;
		border: 1px solid var(--border);
		background: var(--bg-subtle);
		color: var(--text);
		font-size: 1.2rem;
		line-height: 1;
		cursor: pointer;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.trigger-add-btn:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}

	/* NOTE panel */
	.note-panel {
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.note-textarea {
		width: 100%;
		min-height: 160px;
		padding: 12px 14px;
		background: var(--bg);
		border: 1px solid var(--border);
		border-radius: 10px;
		font-family: inherit;
		font-size: 0.9rem;
		line-height: 1.6;
		color: var(--text);
		resize: vertical;
		box-sizing: border-box;
		transition: border-color 0.15s;
	}

	.note-textarea::placeholder {
		color: var(--text-subtle);
	}

	.note-textarea:focus {
		outline: none;
		border-color: var(--text-muted);
	}

	.note-hint {
		font-size: 0.72rem;
		color: var(--text-subtle);
		margin: 0;
		text-align: right;
	}

	/* FAB */
	.fab {
		position: absolute;
		bottom: 20px;
		right: 20px;
		width: 52px;
		height: 52px;
		border-radius: 50%;
		background: var(--text);
		color: var(--bg);
		border: none;
		display: flex;
		align-items: center;
		justify-content: center;
		cursor: pointer;
		box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
		transition: transform 0.15s, box-shadow 0.15s;
		z-index: 10;
	}

	.fab:hover {
		transform: scale(1.06);
		box-shadow: 0 6px 20px rgba(0, 0, 0, 0.28);
	}

	.fab:active {
		transform: scale(0.96);
	}

	/* Log edit modal */
	.modal-backdrop {
		position: fixed;
		inset: 0;
		background: rgba(0, 0, 0, 0.5);
		z-index: 100;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		padding: 0 0 env(safe-area-inset-bottom);
	}

	.modal {
		background: var(--bg-card);
		border-radius: 20px 20px 0 0;
		width: 100%;
		max-height: 90dvh;
		display: flex;
		flex-direction: column;
		overflow: hidden;
	}

	.modal-header {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 16px 16px 12px;
		border-bottom: 1px solid var(--border);
		flex-shrink: 0;
	}

	.modal-icon {
		flex-shrink: 0;
	}

	.modal-title {
		flex: 1;
		font-size: 1rem;
		font-weight: 700;
		color: var(--text);
		margin: 0;
	}

	.modal-body {
		flex: 1;
		overflow-y: auto;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}

	.time-row {
		display: flex;
		gap: 12px;
		flex-wrap: wrap;
	}

	.time-field {
		display: flex;
		flex-direction: column;
		gap: 4px;
		flex: 1;
		min-width: 140px;
	}

	.time-label {
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.time-input {
		background: var(--bg-subtle);
		border: 1px solid var(--border);
		border-radius: 8px;
		padding: 8px 10px;
		font-family: inherit;
		font-size: 0.88rem;
		color: var(--text);
		width: 100%;
	}

	.modal-footer {
		display: flex;
		gap: 10px;
		padding: 12px 16px;
		border-top: 1px solid var(--border);
		flex-shrink: 0;
	}

	.btn-cancel {
		flex: 1;
		background: var(--bg-subtle);
		color: var(--text-muted);
		border: none;
		border-radius: 12px;
		padding: 12px;
		font-family: inherit;
		font-size: 0.9rem;
		font-weight: 600;
		cursor: pointer;
	}

	.btn-save {
		flex: 2;
		background: var(--text);
		color: var(--bg);
		border: none;
		border-radius: 12px;
		padding: 12px;
		font-family: inherit;
		font-size: 0.9rem;
		font-weight: 700;
		cursor: pointer;
	}
</style>

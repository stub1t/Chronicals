<script lang="ts">
	import Plus from '@lucide/svelte/icons/plus';
	import Pill from '@lucide/svelte/icons/pill';
	import Bell from '@lucide/svelte/icons/bell';
	import X from '@lucide/svelte/icons/x';
	import ChevronDown from '@lucide/svelte/icons/chevron-down';
	import ArrowRight from '@lucide/svelte/icons/arrow-right';
	import { api } from '$lib/api.js';
	import SubPage from '$lib/me/SubPage.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Sheet from '$lib/ui/Sheet.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { hapticSuccess } from '$lib/haptics.js';
	import { toDateString } from '$lib/utils/date';
	import { dateLocale, localizeBuiltInText, t } from '$lib/i18n';
	import type { EntryType, Medication, MedicationChange } from '$lib/types';
	import EntryTypeIcon from '$lib/components/entry/EntryTypeIcon.svelte';

	interface Props {
		data: { medications: Medication[]; changes: MedicationChange[]; entryTypes: EntryType[] };
	}

	let { data }: Props = $props();

	// svelte-ignore state_referenced_locally
	let medications = $state<Medication[]>([...data.medications]);
	// svelte-ignore state_referenced_locally
	let changes = $state<MedicationChange[]>([...data.changes]);

	let activeMeds = $derived(medications.filter((m) => m.isActive));
	let pastMeds = $derived(medications.filter((m) => !m.isActive));
	let sortedChanges = $derived([...changes].sort((a, b) => b.changeDate.localeCompare(a.changeDate)));
	let showPast = $state(false);

	const dose = (med: Medication) => (med.dosage ? `${med.dosage}${med.unit ? ` ${med.unit}` : ''}` : '');

	async function reload() {
		const [medsRes, changesRes] = await Promise.all([api('/api/medications'), api('/api/medication-changes')]);
		if (medsRes.ok) medications = await medsRes.json();
		if (changesRes.ok) changes = await changesRes.json();
	}

	async function send(url: string, method: string, body?: unknown): Promise<Response | null> {
		try {
			const res = await api(url, {
				method,
				headers: body ? { 'Content-Type': 'application/json' } : {},
				body: body ? JSON.stringify(body) : undefined
			});
			if (!res.ok) {
				const err = await res.json().catch(() => ({}));
				toasts.show(err.message ?? t('medications_update_failed'), 'error');
				return null;
			}
			return res;
		} catch {
			toasts.show(t('network_error'), 'error');
			return null;
		}
	}

	// ── Add or edit ───────────────────────────────────────────────────────────
	let editOpen = $state(false);
	let editing = $state<Medication | null>(null);
	let medName = $state('');
	let medDosage = $state('');
	let medUnit = $state('');
	let medStart = $state(toDateString(new Date()));
	let medReminders = $state<string[]>([]);
	let medFor = $state<string[]>([]);
	const symptomTypes = $derived(data.entryTypes.filter((type) => type.category === 'symptom' || type.category === 'event'));
	let saving = $state(false);

	function openAdd() {
		editing = null;
		medName = '';
		medDosage = '';
		medUnit = 'mg';
		medStart = toDateString(new Date());
		medReminders = [];
		medFor = [];
		editOpen = true;
	}

	function openEdit(med: Medication) {
		editing = med;
		medName = med.name;
		medDosage = med.dosage ?? '';
		medUnit = med.unit ?? '';
		medStart = med.startDate;
		medReminders = [...(med.reminderTimes ?? [])];
		medFor = [...(med.forTypes ?? [])];
		editOpen = true;
	}

	async function saveMed() {
		if (!medName.trim()) {
			toasts.show(t('medications_name_required'), 'error');
			return;
		}
		saving = true;
		const res = await send(editing ? `/api/medications/${editing.id}` : '/api/medications', editing ? 'PUT' : 'POST', {
			name: medName.trim(),
			dosage: medDosage.trim() || null,
			unit: medUnit.trim() || null,
			startDate: medStart,
			forTypes: medFor,
			// Reminder times can only be edited in the Android app, where they ring.
			...(__NATIVE_APP__ && { reminderTimes: medReminders.filter(Boolean) })
		});
		saving = false;
		if (!res) return;
		const med: Medication = await res.json();
		medications = editing ? medications.map((m) => (m.id === med.id ? med : m)) : [...medications, med];
		editOpen = false;
		hapticSuccess();
		toasts.show(t('log_saved', { name: med.name }), 'success');
		if (__NATIVE_APP__ && med.reminderTimes.length > 0) {
			const { requestReminderPermission } = await import('$lib/mobile/reminders.js');
			await requestReminderPermission();
		}
		if (!editing) await reload()
	}

	async function deleteMed(med: Medication) {
		if (!confirm(t('medications_delete_confirm', { name: med.name }))) return;
		if (await send(`/api/medications/${med.id}`, 'DELETE')) {
			editOpen = false;
			await reload();
		}
	}

	async function setActive(med: Medication, active: boolean) {
		const res = await send('/api/medication-changes', 'POST', {
			medicationId: med.id,
			changeType: active ? 'started' : 'stopped',
			changeDate: toDateString(new Date())
		});
		if (res) {
			hapticSuccess();
			await reload();
		}
	}

	// ── Dose change ───────────────────────────────────────────────────────────
	let doseOpen = $state(false);
	let doseMed = $state<Medication | null>(null);
	let newDose = $state('');
	let doseDate = $state(toDateString(new Date()));
	let doseReason = $state('');

	function openDose(med: Medication) {
		doseMed = med;
		newDose = '';
		doseDate = toDateString(new Date());
		doseReason = '';
		doseOpen = true;
	}

	async function saveDose() {
		const med = doseMed;
		if (!med || !newDose.trim()) return;
		saving = true;
		const unit = med.unit ? ` ${med.unit}` : '';
		const logged = await send('/api/medication-changes', 'POST', {
			medicationId: med.id,
			changeType: 'dosage_change',
			oldDosage: med.dosage ? `${med.dosage}${unit}` : null,
			newDosage: `${newDose.trim()}${unit}`,
			changeDate: doseDate,
			reason: doseReason.trim() || null
		});
		// The medication shows its current dose from now on.
		if (logged) await send(`/api/medications/${med.id}`, 'PUT', { dosage: newDose.trim() });
		saving = false;
		if (!logged) return;
		doseOpen = false;
		hapticSuccess();
		toasts.show(t('medications_dose_saved', { name: med.name, dose: `${newDose.trim()}${unit}` }), 'success');
		await reload();
	}

	function formatDate(date: string): string {
		return new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' });
	}

	function changeLabel(kind: string): string {
		if (kind === 'started') return t('medications_change_started');
		if (kind === 'stopped') return t('medications_change_stopped');
		if (kind === 'dosage_change') return t('medications_change_dosage');
		return kind;
	}

	const CHANGE_COLORS: Record<string, string> = {
		started: 'var(--positive)',
		stopped: 'var(--danger)',
		dosage_change: 'var(--accent)'
	};
</script>

<SubPage title={t('medications_title')}>
	{#snippet action()}
		<Button size="sm" onclick={openAdd}><Plus size={18} />{t('add_new')}</Button>
	{/snippet}

	{#if activeMeds.length === 0}
		<EmptyState icon={Pill} color="#e0679a" title={t('medications_empty_active')} text={t('medications_empty_hint')} />
	{:else}
		<ul class="meds" aria-label={t('medications_active')}>
			{#each activeMeds as med (med.id)}
				<li class="med-item">
					<Card>
						<div class="med-head">
							<span class="bubble" aria-hidden="true"><Pill size={22} /></span>
							<span class="med-text">
								<span class="med-name">{med.name}</span>
								<span class="med-meta">{t('medications_since', { date: formatDate(med.startDate) })}</span>
							</span>
							{#if med.dosage}<span class="dose">{dose(med)}</span>{/if}
						</div>
						{#if __NATIVE_APP__ && med.reminderTimes?.length}
							<p class="reminders"><Bell size={14} />{med.reminderTimes.join(' · ')}</p>
						{/if}
						<div class="med-actions">
							<Button size="sm" variant="secondary" onclick={() => openDose(med)}>{t('medications_change_dose')}</Button>
							<Button size="sm" variant="secondary" onclick={() => openEdit(med)}>{t('medications_edit')}</Button>
							<Button size="sm" variant="ghost" onclick={() => setActive(med, false)}>{t('medications_stop')}</Button>
						</div>
					</Card>
				</li>
			{/each}
		</ul>
	{/if}

	{#if pastMeds.length > 0}
		<section class="section">
			<button class="past-toggle" onclick={() => (showPast = !showPast)} aria-expanded={showPast}>
				<span>{t('medications_past', { count: pastMeds.length })}</span>
				<ChevronDown size={20} class={showPast ? 'turned' : ''} />
			</button>
			{#if showPast}
				<ul class="past">
					{#each pastMeds as med (med.id)}
						<li class="past-item">
							<span class="med-text">
								<span class="med-name">{med.name} {#if med.dosage}<span class="muted">{dose(med)}</span>{/if}</span>
								<span class="med-meta">
									{formatDate(med.startDate)}
									{#if med.endDate}<ArrowRight size={12} class="flip-rtl" /> {formatDate(med.endDate)}{/if}
								</span>
							</span>
							<Button size="sm" variant="secondary" onclick={() => setActive(med, true)}>{t('medications_restart')}</Button>
							<Button size="sm" variant="ghost" onclick={() => deleteMed(med)} aria-label={t('triggers_delete', { name: med.name })}>
								<X size={16} />
							</Button>
						</li>
					{/each}
				</ul>
			{/if}
		</section>
	{/if}

	{#if sortedChanges.length > 0}
		<section class="section">
			<h2 class="section-title">{t('medications_history')}</h2>
			<Card>
				<ol class="timeline">
					{#each sortedChanges as change (change.id)}
						{@const med = medications.find((m) => m.id === change.medicationId)}
						<li class="event" style="--dot: {CHANGE_COLORS[change.changeType] ?? 'var(--text-muted)'}">
							<span class="dot" aria-hidden="true"></span>
							<span class="event-text">
								<span class="event-title">
									<strong>{med ? med.name : t('medications_deleted')}</strong>
									· {changeLabel(change.changeType)}
								</span>
								{#if change.oldDosage || change.newDosage}
									<span class="event-dose">
										{change.oldDosage ?? ''}
										{#if change.oldDosage && change.newDosage}<ArrowRight size={12} class="flip-rtl" />{/if}
										{change.newDosage ?? ''}
									</span>
								{/if}
								{#if change.reason}<span class="event-reason">{change.reason}</span>{/if}
							</span>
							<span class="event-date">{formatDate(change.changeDate)}</span>
						</li>
					{/each}
				</ol>
			</Card>
		</section>
	{/if}
</SubPage>

<Sheet bind:open={editOpen} title={editing ? t('medications_edit_title') : t('medications_add_title')} closeLabel={t('close')} tint="#e0679a">
	<form class="form" onsubmit={(e) => { e.preventDefault(); saveMed(); }}>
		<div class="ui-field">
			<label class="ui-label" for="new-med-name">{t('medications_name')}</label>
			<input id="new-med-name" class="ui-input" bind:value={medName} placeholder={t('medications_name_placeholder')} maxlength="120" />
		</div>
		<div class="pair">
			<div class="ui-field">
				<label class="ui-label" for="new-med-dosage">{t('medications_dosage')}</label>
				<input id="new-med-dosage" class="ui-input" inputmode="decimal" bind:value={medDosage} placeholder={t('medications_dosage_placeholder')} maxlength="64" />
			</div>
			<div class="ui-field">
				<label class="ui-label" for="new-med-unit">{t('medications_unit')}</label>
				<input id="new-med-unit" class="ui-input" bind:value={medUnit} placeholder={t('medications_unit_placeholder')} maxlength="32" />
			</div>
		</div>
		{#if symptomTypes.length}
			<div class="ui-field">
				<span class="ui-label" id="med-for">{t('medications_for')}</span>
				<div class="for" role="group" aria-labelledby="med-for">
					{#each symptomTypes as type (type.id)}
						<button
							type="button"
							class="for-chip"
							class:picked={medFor.includes(type.id)}
							aria-pressed={medFor.includes(type.id)}
							style="--type: {type.color ?? 'var(--accent)'}"
							onclick={() => (medFor = medFor.includes(type.id) ? medFor.filter((id) => id !== type.id) : [...medFor, type.id])}
						>
							<EntryTypeIcon entryType={type} size={18} />
							{localizeBuiltInText(type.name)}
						</button>
					{/each}
				</div>
				<p class="ui-hint">{t('medications_for_hint')}</p>
			</div>
		{/if}
		<div class="ui-field">
			<label class="ui-label" for="new-med-start">{t('medications_start_date')}</label>
			<input id="new-med-start" class="ui-input" type="date" bind:value={medStart} />
		</div>
		{#if __NATIVE_APP__}
			<div class="ui-field">
				<span class="ui-label">{t('medications_reminders')}</span>
				<div class="times">
					{#each medReminders as _, i (i)}
						<span class="time">
							<input type="time" class="ui-input" bind:value={medReminders[i]} aria-label={t('settings_reminder_time')} />
							<button
								type="button"
								class="remove"
								aria-label={t('medications_remove_reminder')}
								onclick={() => (medReminders = medReminders.filter((_, j) => j !== i))}
							>
								<X size={16} />
							</button>
						</span>
					{/each}
					{#if medReminders.length < 6}
						<Button size="sm" variant="secondary" onclick={() => (medReminders = [...medReminders, '08:00'])}>
							<Bell size={16} />{t('medications_add_reminder')}
						</Button>
					{/if}
				</div>
			</div>
		{/if}
	</form>

	{#snippet footer()}
		<div class="actions">
			{#if editing}
				<Button variant="danger" onclick={() => editing && deleteMed(editing)}>{t('delete')}</Button>
			{/if}
			<Button block loading={saving} onclick={saveMed}>{editing ? t('save') : t('medications_add_title')}</Button>
		</div>
	{/snippet}
</Sheet>

<Sheet bind:open={doseOpen} title={t('medications_dose_title', { name: doseMed?.name ?? '' })} closeLabel={t('close')} tint="#7462e8">
	<form class="form" onsubmit={(e) => { e.preventDefault(); saveDose(); }}>
		{#if doseMed?.dosage}
			<p class="current">{t('medications_dose_current', { dose: doseMed ? dose(doseMed) : '' })}</p>
		{/if}
		<div class="ui-field">
			<label class="ui-label" for="new-dose">{t('medications_new_dosage')}</label>
			<span class="with-unit">
				<input id="new-dose" class="ui-input" inputmode="decimal" bind:value={newDose} placeholder={doseMed?.dosage ?? ''} maxlength="64" />
				{#if doseMed?.unit}<span class="unit">{doseMed.unit}</span>{/if}
			</span>
		</div>
		<div class="ui-field">
			<label class="ui-label" for="dose-date">{t('medications_dose_date')}</label>
			<input id="dose-date" class="ui-input" type="date" bind:value={doseDate} />
		</div>
		<div class="ui-field">
			<label class="ui-label" for="change-reason">{t('medications_reason')}</label>
			<input id="change-reason" class="ui-input" bind:value={doseReason} placeholder={t('medications_reason_placeholder')} maxlength="1000" />
		</div>
	</form>

	{#snippet footer()}
		<Button block loading={saving} disabled={!newDose.trim()} onclick={saveDose}>{t('save')}</Button>
	{/snippet}
</Sheet>

<style>
	.meds,
	.past,
	.timeline {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.meds {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.med-head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.bubble {
		width: 44px;
		height: 44px;
		flex-shrink: 0;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: color-mix(in srgb, #e0679a 15%, var(--bg-card));
		color: #e0679a;
	}

	.med-text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.med-name {
		font-weight: 800;
		font-size: var(--text-lg);
	}

	.med-meta {
		display: flex;
		align-items: center;
		gap: 4px;
		font-size: var(--text-sm);
		color: var(--text-muted);
	}

	.dose {
		padding: 4px 12px;
		border-radius: var(--radius-pill);
		background: var(--accent-soft);
		color: var(--accent);
		font-weight: 800;
		white-space: nowrap;
	}

	.reminders {
		display: flex;
		align-items: center;
		gap: 6px;
		margin: var(--space-3) 0 0;
		font-size: var(--text-sm);
		font-weight: 700;
		color: var(--text-muted);
	}

	.med-actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		margin-top: var(--space-4);
	}

	.section {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.section-title {
		margin: 0 var(--space-1);
		font-size: var(--text-lg);
		font-weight: 800;
	}

	.past-toggle {
		display: flex;
		align-items: center;
		justify-content: space-between;
		min-height: 48px;
		padding: 0 var(--space-1);
		border: none;
		background: none;
		color: var(--text);
		font-size: var(--text-lg);
		font-weight: 800;
		cursor: pointer;
	}

	.past-toggle :global(svg) {
		transition: transform 0.3s var(--ease-spring);
	}

	.past-toggle :global(.turned) {
		transform: rotate(180deg);
	}

	.past {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.past-item {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: var(--bg-subtle);
	}

	.past-item .med-name {
		font-size: var(--text-md);
	}

	.muted {
		color: var(--text-muted);
		font-weight: 600;
	}

	.timeline {
		display: flex;
		flex-direction: column;
	}

	.event {
		position: relative;
		display: flex;
		gap: var(--space-3);
		padding-bottom: var(--space-4);
	}

	.event:last-child {
		padding-bottom: 0;
	}

	/* The line joining the dots. */
	.event:not(:last-child)::before {
		content: '';
		position: absolute;
		inset-inline-start: 5px;
		top: 14px;
		bottom: 0;
		width: 2px;
		background: var(--border);
	}

	.dot {
		width: 12px;
		height: 12px;
		margin-top: 4px;
		flex-shrink: 0;
		border-radius: 50%;
		background: var(--dot);
	}

	.event-text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
		font-size: var(--text-sm);
	}

	.event-dose {
		display: flex;
		align-items: center;
		gap: 4px;
		font-weight: 700;
	}

	.event-reason,
	.event-date {
		color: var(--text-muted);
	}

	.event-date {
		font-size: var(--text-sm);
		white-space: nowrap;
	}

	.form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.pair {
		display: grid;
		grid-template-columns: 2fr 1fr;
		gap: var(--space-3);
	}

	.times {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		align-items: center;
	}

	.time {
		display: flex;
		align-items: center;
		gap: 4px;
	}

	.time .ui-input {
		width: 120px;
	}

	.remove {
		width: 36px;
		height: 36px;
		display: grid;
		place-items: center;
		border: none;
		border-radius: 50%;
		background: var(--bg-subtle);
		color: var(--text-muted);
		cursor: pointer;
	}

	.for {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.for-chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 36px;
		padding: 0 12px 0 6px;
		border: 1.5px solid var(--border);
		border-radius: var(--radius-pill);
		background: var(--bg-card);
		color: var(--text);
		font-size: var(--text-sm);
		font-weight: 600;
		cursor: pointer;
	}

	.for-chip.picked {
		border-color: var(--type);
		background: color-mix(in srgb, var(--type) 14%, var(--bg-card));
	}

	.current {
		margin: 0;
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: var(--accent-soft);
		font-weight: 700;
	}

	.with-unit {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.unit {
		font-weight: 800;
		color: var(--text-muted);
	}

	.actions {
		display: flex;
		gap: var(--space-2);
	}
</style>

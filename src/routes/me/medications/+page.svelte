<script lang="ts">
	import { api } from '$lib/api.js';
	import { toDateString } from '$lib/utils/date';
	import { dateLocale, t } from '$lib/i18n';
	import type { Medication, MedicationChange, MedicationChangeType } from '$lib/types';

	interface Props {
		data: { medications: Medication[]; changes: MedicationChange[] };
	}

	let { data }: Props = $props();

	// svelte-ignore state_referenced_locally
	let medications = $state<Medication[]>([...data.medications]);
	// svelte-ignore state_referenced_locally
	let changes = $state<MedicationChange[]>([...data.changes]);

	// ── Add medication modal ──────────────────────────────────────────────────
	let showAddMed = $state(false);
	let newMedName = $state('');
	let newMedDosage = $state('');
	let newMedUnit = $state('');
	let newMedStartDate = $state(toDateString(new Date()));
	let addMedSaving = $state(false);
	let addMedError = $state<string | null>(null);
	let editingMedId = $state<string | null>(null);
	let newMedReminders = $state<string[]>([]);
	let actionError = $state<string | null>(null);

	// ── Log change modal ──────────────────────────────────────────────────────
	let changeMedId = $state<string | null>(null);
	let changeType = $state<MedicationChangeType>('dosage_change');
	let changeOldDosage = $state('');
	let changeNewDosage = $state('');
	let changeReason = $state('');
	let changeSaving = $state(false);
	let changeError = $state<string | null>(null);

	// ── Collapse past meds ────────────────────────────────────────────────────
	let showPast = $state(false);

	let activeMeds = $derived(medications.filter((m) => m.isActive));
	let pastMeds = $derived(medications.filter((m) => !m.isActive));

	let sortedChanges = $derived(
		[...changes].sort((a, b) => b.changeDate.localeCompare(a.changeDate))
	);

	function medForChange(id: string | null): Medication | undefined {
		return medications.find((m) => m.id === id);
	}

	// ── Add medication ────────────────────────────────────────────────────────
	function openAddMed() {
		editingMedId = null;
		newMedReminders = [];
		newMedName = '';
		newMedDosage = '';
		newMedUnit = '';
		newMedStartDate = toDateString(new Date());
		addMedError = null;
		showAddMed = true;
	}

	function openEditMed(med: Medication) {
		editingMedId = med.id;
		newMedName = med.name;
		newMedDosage = med.dosage ?? '';
		newMedUnit = med.unit ?? '';
		newMedStartDate = med.startDate;
		newMedReminders = [...(med.reminderTimes ?? [])];
		addMedError = null;
		showAddMed = true;
	}

	async function submitAddMed() {
		if (!newMedName.trim()) { addMedError = t('medications_name_required'); return; }
		addMedSaving = true;
		addMedError = null;
		try {
			const res = await api(editingMedId ? `/api/medications/${editingMedId}` : '/api/medications', {
				method: editingMedId ? 'PUT' : 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					name: newMedName.trim(),
					dosage: newMedDosage || null,
					unit: newMedUnit || null,
					startDate: newMedStartDate,
					// Reminder times can only be edited in the Android app, where they ring.
					...(__NATIVE_APP__ && { reminderTimes: newMedReminders.filter(Boolean) })
				})
			});
			if (!res.ok) {
				const err = await res.json().catch(() => ({}));
				addMedError = err.message ?? t(editingMedId ? 'medications_update_failed' : 'medications_add_failed');
			} else {
				const med: Medication = await res.json();
				medications = editingMedId
					? medications.map((m) => (m.id === med.id ? med : m))
					: [...medications, med];
				showAddMed = false;
				if (__NATIVE_APP__ && med.reminderTimes.length > 0) {
					const { requestReminderPermission } = await import('$lib/mobile/reminders.js');
					await requestReminderPermission();
				}
			}
		} catch {
			addMedError = t('network_error');
		} finally {
			addMedSaving = false;
		}
	}

	// ── Log change ────────────────────────────────────────────────────────────
	function openChangeModal(medId: string) {
		changeMedId = medId;
		changeType = 'dosage_change';
		changeOldDosage = '';
		changeNewDosage = '';
		changeReason = '';
		changeError = null;
	}

	function closeChangeModal() {
		changeMedId = null;
	}

	async function submitChange() {
		if (!changeMedId) return;
		changeSaving = true;
		changeError = null;
		try {
			const res = await api('/api/medication-changes', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					medicationId: changeMedId,
					changeType,
					oldDosage: changeOldDosage || null,
					newDosage: changeNewDosage || null,
					changeDate: toDateString(new Date()),
					reason: changeReason || null
				})
			});
			if (!res.ok) {
				const err = await res.json().catch(() => ({}));
				changeError = err.message ?? t('medications_log_failed');
			} else {
				changeMedId = null;
				await reload();
			}
		} catch {
			changeError = t('network_error');
		} finally {
			changeSaving = false;
		}
	}

	// ── Stop, restart, delete ─────────────────────────────────────────────────
	async function reload() {
		const [medsRes, changesRes] = await Promise.all([
			api('/api/medications'),
			api('/api/medication-changes')
		]);
		if (medsRes.ok) medications = await medsRes.json();
		if (changesRes.ok) changes = await changesRes.json();
	}

	async function setActive(med: Medication, active: boolean) {
		actionError = null;
		try {
			const res = await api('/api/medication-changes', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					medicationId: med.id,
					changeType: active ? 'started' : 'stopped',
					changeDate: toDateString(new Date())
				})
			});
			if (!res.ok) actionError = t('medications_update_failed');
			await reload();
		} catch {
			actionError = t('network_error');
		}
	}

	async function deleteMed(med: Medication) {
		if (!confirm(t('medications_delete_confirm', { name: med.name }))) return;
		actionError = null;
		try {
			const res = await api(`/api/medications/${med.id}`, { method: 'DELETE' });
			if (!res.ok) actionError = t('medications_update_failed');
			await reload();
		} catch {
			actionError = t('network_error');
		}
	}

	function formatDate(dateStr: string): string {
		const d = new Date(dateStr + 'T00:00:00');
		return d.toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' });
	}

	function changeTypeLabel(changeKind: string): string {
		if (changeKind === 'started') return t('medications_change_started');
		if (changeKind === 'stopped') return t('medications_change_stopped');
		if (changeKind === 'dosage_change') return t('medications_change_dosage');
		return changeKind;
	}
</script>

<div class="page">
	<header class="page-header">
		<a href="/me" class="back-link">← {t('settings_title')}</a>
		<h1 class="page-title">{t('medications_title')}</h1>
	</header>

	<!-- ── Active medications ─────────────────────────────────────────────── -->
	<section class="section">
		<div class="section-head">
			<h2 class="section-title">{t('medications_active')}</h2>
			<button class="add-btn" onclick={openAddMed}>+ {t('medications_add')}</button>
		</div>

		{#if actionError}
			<p class="form-error">{actionError}</p>
		{/if}

		{#if activeMeds.length === 0}
			<p class="empty-text">{t('medications_empty_active')}</p>
		{:else}
			<ul class="med-list">
				{#each activeMeds as med (med.id)}
					<li class="med-item">
						<div class="med-info">
							<span class="med-name">{med.name}</span>
							{#if med.dosage}
								<span class="med-dosage">{med.dosage}{med.unit ? ' ' + med.unit : ''}</span>
							{/if}
							<span class="med-date">{t('medications_since', { date: formatDate(med.startDate) })}</span>
							{#if __NATIVE_APP__ && med.reminderTimes?.length}
								<span class="med-date">{t('medications_reminder_at', { times: med.reminderTimes.join(', ') })}</span>
							{/if}
						</div>
						<div class="med-actions">
							<button class="change-btn" onclick={() => openChangeModal(med.id)}>{t('medications_log_change')}</button>
							<button class="change-btn" onclick={() => openEditMed(med)}>{t('medications_edit')}</button>
							<button class="change-btn" onclick={() => setActive(med, false)}>{t('medications_stop')}</button>
							<button class="change-btn danger" onclick={() => deleteMed(med)}>{t('medications_delete')}</button>
						</div>
					</li>
				{/each}
			</ul>
		{/if}
	</section>

	<!-- ── Past medications ───────────────────────────────────────────────── -->
	{#if pastMeds.length > 0}
		<section class="section">
			<button
				class="collapse-btn"
				onclick={() => { showPast = !showPast; }}
				aria-expanded={showPast}
			>
				<span>{t('medications_past', { count: pastMeds.length })}</span>
				<span class="collapse-arrow" class:open={showPast}>›</span>
			</button>

			{#if showPast}
				<ul class="med-list past">
					{#each pastMeds as med (med.id)}
						<li class="med-item">
							<div class="med-info">
								<span class="med-name">{med.name}</span>
								{#if med.dosage}
									<span class="med-dosage">{med.dosage}{med.unit ? ' ' + med.unit : ''}</span>
								{/if}
								<span class="med-date">
									{formatDate(med.startDate)}
									{#if med.endDate} → {formatDate(med.endDate)}{/if}
								</span>
							</div>
							<div class="med-actions">
								<button class="change-btn" onclick={() => setActive(med, true)}>{t('medications_restart')}</button>
								<button class="change-btn danger" onclick={() => deleteMed(med)}>{t('medications_delete')}</button>
							</div>
						</li>
					{/each}
				</ul>
			{/if}
		</section>
	{/if}

	<!-- ── Medication change timeline ────────────────────────────────────── -->
	{#if sortedChanges.length > 0}
		<section class="section">
			<h2 class="section-title">{t('medications_history')}</h2>
			<ul class="timeline">
				{#each sortedChanges as change (change.id)}
					{@const med = medForChange(change.medicationId)}
					<li class="timeline-item">
						<div class="timeline-dot"></div>
						<div class="timeline-content">
							<div class="timeline-header">
								<span class="timeline-type">{changeTypeLabel(change.changeType)}</span>
								<span class="timeline-med">{med ? med.name : t('medications_deleted')}</span>
								<span class="timeline-date">{formatDate(change.changeDate)}</span>
							</div>
							{#if change.oldDosage || change.newDosage}
								<p class="timeline-dosage">
									{#if change.oldDosage}{change.oldDosage}{/if}
									{#if change.oldDosage && change.newDosage} → {/if}
									{#if change.newDosage}{change.newDosage}{/if}
								</p>
							{/if}
							{#if change.reason}
								<p class="timeline-reason">{change.reason}</p>
							{/if}
						</div>
					</li>
				{/each}
			</ul>
		</section>
	{/if}
</div>

<!-- ── Add medication modal ───────────────────────────────────────────────── -->
{#if showAddMed}
	<!-- svelte-ignore a11y_click_events_have_key_events -->
	<div class="modal-backdrop" role="presentation" onclick={(e) => { if (e.target === e.currentTarget) showAddMed = false; }}>
		<div class="modal" role="dialog" aria-modal="true" aria-label={t(editingMedId ? 'medications_edit_title' : 'medications_add_title')}>
			<div class="modal-header">
				<h3 class="modal-title">{t(editingMedId ? 'medications_edit_title' : 'medications_add_title')}</h3>
				<button class="modal-close" onclick={() => { showAddMed = false; }} aria-label={t('close')}>×</button>
			</div>

			<form class="modal-form" onsubmit={(e) => { e.preventDefault(); submitAddMed(); }}>
				<div class="field">
					<label class="field-label" for="new-med-name">{t('medications_name')} *</label>
					<input id="new-med-name" type="text" class="input" bind:value={newMedName} placeholder={t('medications_name_placeholder')} required />
				</div>
				<div class="field-row">
					<div class="field">
						<label class="field-label" for="new-med-dosage">{t('medications_dosage')}</label>
						<input id="new-med-dosage" type="text" class="input" bind:value={newMedDosage} placeholder={t('medications_dosage_placeholder')} />
					</div>
					<div class="field">
						<label class="field-label" for="new-med-unit">{t('medications_unit')}</label>
						<input id="new-med-unit" type="text" class="input" bind:value={newMedUnit} placeholder={t('medications_unit_placeholder')} />
					</div>
				</div>
				<div class="field">
					<label class="field-label" for="new-med-start">{t('medications_start_date')}</label>
					<input id="new-med-start" type="date" class="input" bind:value={newMedStartDate} />
				</div>
				{#if __NATIVE_APP__}
					<div class="field">
						<span class="field-label">{t('medications_reminders')}</span>
						<div class="reminder-times">
							{#each newMedReminders as _, i (i)}
								<span class="reminder-time">
									<input type="time" class="input" bind:value={newMedReminders[i]} />
									<button
										type="button"
										class="change-btn"
										aria-label={t('medications_remove_reminder')}
										onclick={() => (newMedReminders = newMedReminders.filter((_, j) => j !== i))}>×</button
									>
								</span>
							{/each}
							{#if newMedReminders.length < 6}
								<button type="button" class="change-btn" onclick={() => (newMedReminders = [...newMedReminders, '08:00'])}
									>{t('medications_add_reminder')}</button
								>
							{/if}
						</div>
					</div>
				{/if}

				{#if addMedError}
					<p class="form-error">{addMedError}</p>
				{/if}

				<div class="modal-actions">
					<button type="button" class="btn-ghost" onclick={() => { showAddMed = false; }}>{t('cancel')}</button>
					<button type="submit" class="btn-primary" disabled={addMedSaving}>
						{#if editingMedId}
							{addMedSaving ? t('medications_logging') : t('save')}
						{:else}
							{addMedSaving ? t('medications_adding') : t('medications_add_title')}
						{/if}
					</button>
				</div>
			</form>
		</div>
	</div>
{/if}

<!-- ── Log change modal ───────────────────────────────────────────────────── -->
{#if changeMedId !== null}
	{@const med = medForChange(changeMedId)}
	<!-- svelte-ignore a11y_click_events_have_key_events -->
	<div class="modal-backdrop" role="presentation" onclick={(e) => { if (e.target === e.currentTarget) closeChangeModal(); }}>
		<div class="modal" role="dialog" aria-modal="true" aria-label={t('medications_log_title', { name: med?.name ?? '' })}>
			<div class="modal-header">
				<h3 class="modal-title">{t('medications_log_title', { name: med?.name ?? '' })}</h3>
				<button class="modal-close" onclick={closeChangeModal} aria-label={t('close')}>×</button>
			</div>

			<form class="modal-form" onsubmit={(e) => { e.preventDefault(); submitChange(); }}>
				<div class="field">
					<label class="field-label" for="change-type">{t('medications_change_type')}</label>
					<select id="change-type" class="input select" bind:value={changeType}>
						<option value="dosage_change">{t('medications_change_dosage')}</option>
						<option value="started">{t('medications_change_started')}</option>
						<option value="stopped">{t('medications_change_stopped')}</option>
					</select>
				</div>
				<div class="field-row">
					<div class="field">
						<label class="field-label" for="change-old-dosage">{t('medications_old_dosage')}</label>
						<input id="change-old-dosage" type="text" class="input" bind:value={changeOldDosage} placeholder={t('medications_old_dosage_placeholder')} />
					</div>
					<div class="field">
						<label class="field-label" for="change-new-dosage">{t('medications_new_dosage')}</label>
						<input id="change-new-dosage" type="text" class="input" bind:value={changeNewDosage} placeholder={t('medications_new_dosage_placeholder')} />
					</div>
				</div>
				<div class="field">
					<label class="field-label" for="change-reason">{t('medications_reason')}</label>
					<input id="change-reason" type="text" class="input" bind:value={changeReason} placeholder={t('medications_reason_placeholder')} />
				</div>

				{#if changeError}
					<p class="form-error">{changeError}</p>
				{/if}

				<div class="modal-actions">
					<button type="button" class="btn-ghost" onclick={closeChangeModal}>{t('cancel')}</button>
					<button type="submit" class="btn-primary" disabled={changeSaving}>
						{changeSaving ? t('medications_logging') : t('medications_log_change')}
					</button>
				</div>
			</form>
		</div>
	</div>
{/if}

<style>
	.page {
		max-width: 560px;
		margin: 0 auto;
		padding: 24px 16px 80px;
		display: flex;
		flex-direction: column;
		gap: 28px;
	}

	.page-header {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.back-link {
		font-size: 0.82rem;
		color: var(--text-muted);
		text-decoration: none;
		align-self: flex-start;
	}

	.back-link:hover {
		color: var(--text);
	}

	.page-title {
		font-size: 1.6rem;
		font-weight: 800;
		color: var(--text);
		margin: 0;
	}

	/* Section */
	.section {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.section-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.section-title {
		font-size: 0.88rem;
		font-weight: 700;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
		margin: 0;
	}

	.add-btn {
		background: var(--text);
		color: var(--bg);
		border: none;
		border-radius: 14px;
		padding: 6px 14px;
		font-family: inherit;
		font-size: 0.82rem;
		font-weight: 700;
		cursor: pointer;
		transition: opacity 0.15s;
	}

	.add-btn:hover {
		opacity: 0.85;
	}

	/* Medication list */
	.med-list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
		background: var(--border);
		border: 1px solid var(--border);
		border-radius: 14px;
		overflow: hidden;
	}

	.med-list.past .med-item {
		opacity: 0.7;
	}

	.med-item {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 12px;
		padding: 14px 16px;
		background: var(--bg-card);
	}

	.med-info {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.med-name {
		font-size: 0.95rem;
		font-weight: 600;
		color: var(--text);
	}

	.med-dosage {
		font-size: 0.82rem;
		color: var(--text-muted);
	}

	.med-date {
		font-size: 0.75rem;
		color: var(--text-subtle);
	}

	.change-btn {
		background: none;
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 6px 12px;
		font-family: inherit;
		font-size: 0.78rem;
		font-weight: 600;
		color: var(--text-muted);
		cursor: pointer;
		white-space: nowrap;
		transition: all 0.15s;
	}

	.change-btn:hover {
		border-color: var(--text-muted);
		color: var(--text);
	}

	.reminder-times {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
		align-items: center;
	}

	.reminder-time {
		display: flex;
		align-items: center;
		gap: 4px;
	}

	.reminder-time .input {
		width: auto;
	}

	.med-actions {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		width: 100%;
	}

	.change-btn.danger {
		color: #dc2626;
	}

	.change-btn.danger:hover {
		border-color: #dc2626;
	}

	.empty-text {
		font-size: 0.88rem;
		color: var(--text-muted);
		margin: 0;
	}

	/* Collapse button */
	.collapse-btn {
		display: flex;
		align-items: center;
		justify-content: space-between;
		width: 100%;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 12px;
		padding: 12px 16px;
		font-family: inherit;
		font-size: 0.88rem;
		font-weight: 600;
		color: var(--text-muted);
		cursor: pointer;
		transition: background 0.15s;
	}

	.collapse-btn:hover {
		background: var(--bg-subtle);
	}

	.collapse-arrow {
		font-size: 1.2rem;
		transition: transform 0.2s;
		display: inline-block;
	}

	.collapse-arrow.open {
		transform: rotate(90deg);
	}

	/* Timeline */
	.timeline {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 0;
		position: relative;
		padding-left: 20px;
		border-left: 2px solid var(--border);
	}

	.timeline-item {
		position: relative;
		padding: 0 0 20px 16px;
	}

	.timeline-item:last-child {
		padding-bottom: 0;
	}

	.timeline-dot {
		position: absolute;
		left: -27px;
		top: 4px;
		width: 10px;
		height: 10px;
		border-radius: 50%;
		background: var(--text-muted);
		border: 2px solid var(--bg);
	}

	.timeline-content {
		display: flex;
		flex-direction: column;
		gap: 3px;
	}

	.timeline-header {
		display: flex;
		align-items: center;
		gap: 8px;
		flex-wrap: wrap;
	}

	.timeline-type {
		font-size: 0.85rem;
		font-weight: 700;
		color: var(--text);
	}

	.timeline-med {
		font-size: 0.82rem;
		color: var(--text-muted);
	}

	.timeline-date {
		font-size: 0.75rem;
		color: var(--text-subtle);
		margin-left: auto;
	}

	.timeline-dosage {
		font-size: 0.82rem;
		color: var(--text-muted);
		margin: 0;
	}

	.timeline-reason {
		font-size: 0.78rem;
		color: var(--text-subtle);
		font-style: italic;
		margin: 0;
	}

	/* Modal */
	.modal-backdrop {
		position: fixed;
		inset: 0;
		background: rgba(0, 0, 0, 0.4);
		display: flex;
		align-items: flex-end;
		justify-content: center;
		z-index: 100;
		padding: 0;
	}

	@media (min-width: 480px) {
		.modal-backdrop {
			align-items: center;
			padding: 16px;
		}
	}

	.modal {
		background: var(--bg-card);
		border-radius: 20px 20px 0 0;
		padding: 20px;
		width: 100%;
		max-width: 480px;
		max-height: 90vh;
		overflow-y: auto;
	}

	@media (min-width: 480px) {
		.modal {
			border-radius: 20px;
		}
	}

	.modal-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 18px;
	}

	.modal-title {
		font-size: 1rem;
		font-weight: 700;
		color: var(--text);
		margin: 0;
	}

	.modal-close {
		background: none;
		border: none;
		font-size: 1.4rem;
		color: var(--text-muted);
		cursor: pointer;
		padding: 0;
		line-height: 1;
	}

	.modal-close:hover {
		color: var(--text);
	}

	.modal-form {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}

	.field-row {
		display: flex;
		gap: 12px;
	}

	.field-row .field {
		flex: 1;
	}

	.field-label {
		font-size: 0.75rem;
		font-weight: 700;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.input {
		background: var(--bg-subtle);
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 10px 14px;
		font-family: inherit;
		font-size: 0.9rem;
		color: var(--text);
		width: 100%;
	}

	.input:focus {
		outline: none;
		border-color: var(--text-muted);
	}

	.select {
		appearance: none;
		background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%23888'/%3E%3C/svg%3E");
		background-repeat: no-repeat;
		background-position: right 12px center;
		cursor: pointer;
	}

	.form-error {
		font-size: 0.82rem;
		color: #dc2626;
		margin: 0;
	}

	.modal-actions {
		display: flex;
		gap: 8px;
		justify-content: flex-end;
		padding-top: 4px;
	}

	.btn-ghost {
		background: none;
		border: 1px solid var(--border);
		border-radius: 12px;
		padding: 10px 18px;
		font-family: inherit;
		font-size: 0.88rem;
		font-weight: 600;
		color: var(--text-muted);
		cursor: pointer;
		transition: all 0.15s;
	}

	.btn-ghost:hover {
		border-color: var(--text-muted);
		color: var(--text);
	}

	.btn-primary {
		background: var(--text);
		color: var(--bg);
		border: none;
		border-radius: 12px;
		padding: 10px 20px;
		font-family: inherit;
		font-size: 0.88rem;
		font-weight: 700;
		cursor: pointer;
		transition: opacity 0.15s;
	}

	.btn-primary:hover:not(:disabled) {
		opacity: 0.85;
	}

	.btn-primary:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}
</style>

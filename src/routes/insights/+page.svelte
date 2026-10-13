<script lang="ts">
	import { api } from '$lib/api.js';
	import type {
		EntryType,
		MonthlyStats,
		CorrelationResult,
		EnvironmentalCorrelation
	} from '$lib/types';
	import { toYearMonth, formatMonthLabel, getDaysInMonth, intensityColor } from '$lib/utils/date';
	import { localizeBuiltInText, t } from '$lib/i18n';
	import PressureDeltaChart from '$lib/components/analysis/PressureDeltaChart.svelte';

	interface CorrelationData {
		triggers: CorrelationResult[];
		environmental: EnvironmentalCorrelation[];
		medicationFlags: Array<{ changeDate: string; description: string }>;
	}

	interface Props {
		data: { entryTypes: EntryType[] };
	}

	let { data }: Props = $props();

	const today = new Date();
	let selectedTypeId = $state<string | null>(null);
	let selectedMonth = $state(toYearMonth(today));
	let selectedRange = $state<1 | 3 | 12>(1);
	let stats = $state<MonthlyStats | null>(null);
	let correlations = $state<CorrelationData | null>(null);
	let pressureData = $state<Array<{ date: string; delta: number | null; hasSymptom: boolean }>>([]);
	let loading = $state(false);

	// ── Derived helpers ───────────────────────────────────────────────────────

	let selectedType = $derived(
		data.entryTypes.find((et) => et.id === selectedTypeId) ?? null
	);

	let monthLabel = $derived(
		selectedRange === 1
			? formatMonthLabel(selectedMonth)
			: selectedRange === 3
				? t('analysis_last_three_months')
				: t('analysis_last_year')
	);

	let daysInMonth = $derived(getDaysInMonth(selectedMonth));

	let dateRange = $derived.by(() => {
		if (selectedRange === 1) {
			const [y, m] = selectedMonth.split('-').map(Number);
			const lastDay = new Date(y, m, 0).getDate();
			return {
				startDate: `${selectedMonth}-01`,
				endDate: `${selectedMonth}-${String(lastDay).padStart(2, '0')}`
			};
		}
		const end = new Date(today.getFullYear(), today.getMonth() + 1, 0);
		const start = new Date(today.getFullYear(), today.getMonth() - (selectedRange - 1), 1);
		return {
			startDate: `${toYearMonth(start)}-01`,
			endDate: `${toYearMonth(end)}-${String(end.getDate()).padStart(2, '0')}`
		};
	});

	let sortedTriggers = $derived.by(() => {
		if (!correlations) return [];
		return [...correlations.triggers]
			.filter((t) => t.strength !== 'none' || t.coOccurrences > 0)
			.sort((a, b) => b.phi - a.phi);
	});

	let significantEnv = $derived.by(() => {
		if (!correlations) return [];
		return correlations.environmental.filter((e) => e.strength !== 'none');
	});

	// ── Navigation ────────────────────────────────────────────────────────────

	function prevMonth() {
		const [y, m] = selectedMonth.split('-').map(Number);
		const d = new Date(y, m - 2, 1);
		selectedMonth = toYearMonth(d);
	}

	function nextMonth() {
		const [y, m] = selectedMonth.split('-').map(Number);
		const d = new Date(y, m, 1);
		const next = toYearMonth(d);
		if (next <= toYearMonth(today)) selectedMonth = next;
	}

	let canGoNext = $derived(selectedMonth < toYearMonth(today));

	// ── Fetch ─────────────────────────────────────────────────────────────────

	async function analyse() {
		if (!selectedTypeId) return;
		loading = true;
		stats = null;
		correlations = null;
		pressureData = [];
		try {
			const { startDate, endDate } = dateRange;
			const statsParam =
				selectedRange === 1
					? `month=${selectedMonth}`
					: `startDate=${startDate}&endDate=${endDate}`;

			const [statsRes, corrRes, pressureRes] = await Promise.all([
				api(`/api/analysis/stats?entryTypeId=${selectedTypeId}&${statsParam}`),
				api(`/api/analysis/correlations?entryTypeId=${selectedTypeId}&startDate=${startDate}&endDate=${endDate}`),
				api(`/api/analysis/pressure?startDate=${startDate}&endDate=${endDate}&entryTypeId=${selectedTypeId}`)
			]);
			if (statsRes.ok) stats = await statsRes.json();
			if (corrRes.ok) correlations = await corrRes.json();
			if (pressureRes.ok) pressureData = await pressureRes.json();
		} catch (e) {
			console.error('Failed to load analysis', e);
		} finally {
			loading = false;
		}
	}

	// ── Formatting ────────────────────────────────────────────────────────────

	function formatDurationMinutes(mins: number | null): string {
		if (mins === null) return '-';
		const h = Math.floor(mins / 60);
		const m = Math.round(mins % 60);
		if (h === 0) return `${m}m`;
		return m ? `${h}h ${m}m` : `${h}h`;
	}

	function heatmapColor(day: number): string {
		if (!stats || !selectedType) return 'var(--bg-subtle)';
		const dateStr = `${selectedMonth}-${String(day).padStart(2, '0')}`;
		if (!stats.occurrenceDates.includes(dateStr)) return 'var(--bg-subtle)';
		return intensityColor(selectedType.color ?? '#888888', stats.avgIntensity ?? 3);
	}

	function strengthBadgeClass(strength: 'none' | 'possible' | 'strong'): string {
		if (strength === 'strong') return 'badge-strong';
		if (strength === 'possible') return 'badge-possible';
		return 'badge-none';
	}

	function phiBarWidth(phi: number): string {
		return `${Math.min(Math.abs(phi) * 100, 100)}%`;
	}
</script>

<div class="page">
	<!-- ── Header ─────────────────────────────────────────────────────────── -->
	<header class="page-header">
		<div class="title-row">
			<h1 class="page-title">{t('analysis_title')}</h1>
			<a href="/insights/report" class="report-link">{t('report_open')}</a>
		</div>
		<p class="page-subtitle">{t('analysis_subtitle')}</p>
	</header>

	<!-- ── Section 1: Selectors ───────────────────────────────────────────── -->
	<section class="section">
		<div class="selectors">
			<!-- Entry type dropdown -->
			<div class="field">
				<label class="field-label" for="entry-type-select">{t('analysis_entry_type')}</label>
				<div class="select-wrap">
					<select
						id="entry-type-select"
						class="pill-select"
						bind:value={selectedTypeId}
					>
						<option value={null} disabled>{t('analysis_choose_type')}</option>
						{#each data.entryTypes as et (et.id)}
							<option value={et.id}>{localizeBuiltInText(et.name)}</option>
						{/each}
					</select>
				</div>
			</div>

			<!-- Range selector -->
			<div class="field">
				<span class="field-label">{t('analysis_range')}</span>
				<div class="range-tabs">
					{#each ([1, 3, 12] as const) as r}
						<button
							class="range-tab"
							class:active={selectedRange === r}
							onclick={() => { selectedRange = r; }}
						>{r === 1 ? '1M' : r === 3 ? '3M' : '1Y'}</button>
					{/each}
				</div>
			</div>

			<!-- Month picker (only for single-month range) -->
			{#if selectedRange === 1}
				<div class="field">
					<span class="field-label" id="month-picker-label">{t('analysis_month')}</span>
					<div class="month-picker" aria-labelledby="month-picker-label">
						<button class="arrow-btn" onclick={prevMonth} aria-label={t('prev_month')}>‹</button>
						<span class="month-display">{formatMonthLabel(selectedMonth)}</span>
						<button class="arrow-btn" onclick={nextMonth} disabled={!canGoNext} aria-label={t('next_month')}>›</button>
					</div>
				</div>
			{/if}
		</div>

		<button
			class="analyse-btn"
			onclick={analyse}
			disabled={!selectedTypeId || loading}
		>
			{#if loading}{t('analysis_loading')}{:else}{t('analysis_start')}{/if}
		</button>
	</section>

	<!-- ── Section 2: Stats ───────────────────────────────────────────────── -->
	{#if stats}
		{@const color = selectedType?.color ?? '#888888'}
		<section class="section card">
			<h2 class="section-title">{t('analysis_overview')}</h2>

			<div class="stat-row">
				<div class="stat-big">
					<span class="stat-number">{stats.frequency}</span>
					<span class="stat-label">{t('analysis_entries_in', { label: monthLabel })}</span>
				</div>

				{#if stats.avgDurationMinutes !== null}
					<div class="stat-chip">
						<span class="stat-chip-value">{formatDurationMinutes(stats.avgDurationMinutes)}</span>
						<span class="stat-chip-label">{t('analysis_avg_duration')}</span>
					</div>
				{/if}
			</div>

			{#if stats.avgIntensity !== null}
				<div class="intensity-row">
					<span class="intensity-label">{t('analysis_avg_intensity')}</span>
					<div class="intensity-track">
						<div
							class="intensity-fill"
							style={`width: ${(stats.avgIntensity / 5) * 100}%; background: ${color};`}
						></div>
					</div>
					<span class="intensity-value">{stats.avgIntensity.toFixed(1)}/5</span>
				</div>
			{/if}

			<!-- Day heatmap (single month only) -->
			{#if selectedRange === 1}
				<div class="heatmap-wrap">
					<div class="heatmap-legend">{t('analysis_days_in_month')}</div>
					<div class="heatmap">
						{#each Array.from({ length: daysInMonth }, (_, i) => i + 1) as day}
							<div
								class="heatmap-cell"
								style={`background: ${heatmapColor(day)};`}
								title={`${selectedMonth}-${String(day).padStart(2, '0')}`}
								aria-label={`Tag ${day}`}
							>{day}</div>
						{/each}
					</div>
				</div>
			{/if}
		</section>
	{/if}

	<!-- ── Section 3: Trigger correlations ───────────────────────────────── -->
	{#if sortedTriggers.length > 0}
		<section class="section card">
			<h2 class="section-title">{t('analysis_trigger_corr')}</h2>
			<p class="section-hint">{t('analysis_trigger_hint')}</p>
			<div class="table-scroll">
				<table class="corr-table">
					<thead>
						<tr>
							<th>{t('analysis_trigger')}</th>
							<th class="num-col">{t('analysis_shared_days')}</th>
							<th class="num-col">{t('analysis_with_trigger')}</th>
							<th class="num-col">{t('analysis_without_trigger')}</th>
							<th class="badge-col">{t('analysis_strength')}</th>
						</tr>
					</thead>
					<tbody>
						{#each sortedTriggers as trigger (trigger.triggerId)}
							<tr>
								<td>
									<span class="trigger-name">{localizeBuiltInText(trigger.triggerName)}</span>
									<span class="trigger-cat">{localizeBuiltInText(trigger.triggerCategory)}</span>
								</td>
								<td class="num-col">{trigger.coOccurrences}</td>
								<td class="num-col">{trigger.avgIntensityWith !== null ? trigger.avgIntensityWith.toFixed(1) : '-'}</td>
								<td class="num-col">{trigger.avgIntensityWithout !== null ? trigger.avgIntensityWithout.toFixed(1) : '-'}</td>
								<td class="badge-col">
									<span class={`badge ${strengthBadgeClass(trigger.strength)}`}>
										{trigger.strength === 'strong' ? t('badge_strong') : trigger.strength === 'possible' ? t('badge_possible') : t('badge_none')}
									</span>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</section>
	{/if}

	<!-- ── Section 4: Pressure chart ────────────────────────────────────── -->
	{#if pressureData.length > 0}
		<section class="section">
			<h2 class="section-title">{t('analysis_pressure')}</h2>
			<p class="section-hint">{t('analysis_pressure_hint')}</p>
			<PressureDeltaChart data={pressureData} color={selectedType?.color ?? '#3b82f6'} />
		</section>
	{/if}

	<!-- ── Section 5: Environmental correlations ─────────────────────────── -->
	{#if significantEnv.length > 0}
		<section class="section">
			<h2 class="section-title">{t('analysis_environment')}</h2>
			<div class="env-cards">
				{#each significantEnv as env (env.variable)}
					<div class="env-card">
						<div class="env-header">
							<span class="env-label">{env.label}</span>
							<span class={`badge ${strengthBadgeClass(env.strength)}`}>
								{env.strength === 'strong' ? t('badge_strong') : t('badge_possible')}
							</span>
						</div>
						<div class="phi-track">
							<div
								class="phi-fill"
								style={`width: ${phiBarWidth(env.phi)};`}
							></div>
						</div>
						{#if env.avgIntensityHigh !== null && env.avgIntensityLow !== null}
							<p class="env-detail">
								{t('analysis_high_days', { label: env.label.toLowerCase() })}
								<strong>{env.avgIntensityHigh.toFixed(1)}</strong>
								{t('analysis_vs_low')} <strong>{env.avgIntensityLow.toFixed(1)}</strong>
							</p>
						{/if}
					</div>
				{/each}
			</div>
		</section>
	{/if}

	<!-- ── Section 6: Medication flags ───────────────────────────────────── -->
	{#if correlations?.medicationFlags && correlations.medicationFlags.length > 0}
		<section class="section">
			<div class="med-banner">
				<div>
					<p class="med-banner-title">{t('analysis_med_flags')}</p>
					{#each correlations.medicationFlags as flag}
						<p class="med-banner-item">{flag.changeDate}: {flag.description}</p>
					{/each}
				</div>
			</div>
		</section>
	{/if}

	<!-- ── Empty state ────────────────────────────────────────────────────── -->
	{#if !loading && !stats && selectedTypeId}
		<div class="empty-state">
			<p>{t('analysis_no_data')}</p>
		</div>
	{/if}

	{#if !selectedTypeId && !loading}
		<div class="empty-state">
			<p>{t('analysis_select_type')}</p>
		</div>
	{/if}
</div>

<style>
	.page {
		max-width: 680px;
		margin: 0 auto;
		padding: 24px 16px 80px;
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	/* Header */
	.page-header {
		margin-bottom: 4px;
	}

	.title-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}

	.report-link {
		font-size: 0.8rem;
		font-weight: 600;
		padding: 6px 12px;
		border: 1px solid var(--border);
		border-radius: 14px;
		color: var(--text);
		text-decoration: none;
		white-space: nowrap;
	}

	.page-title {
		font-size: 1.6rem;
		font-weight: 800;
		color: var(--text);
		margin: 0 0 4px;
	}

	.page-subtitle {
		font-size: 0.9rem;
		color: var(--text-muted);
		margin: 0;
	}

	/* Section */
	.section {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}

	.section.card {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 14px;
		padding: 18px;
	}

	.section-title {
		font-size: 0.95rem;
		font-weight: 700;
		color: var(--text);
		margin: 0;
	}

	.section-hint {
		font-size: 0.82rem;
		color: var(--text-muted);
		margin: -4px 0 0;
		line-height: 1.45;
	}

	/* Selectors */
	.selectors {
		display: flex;
		gap: 12px;
		flex-wrap: wrap;
		align-items: flex-end;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}

	.field-label {
		font-size: 0.78rem;
		font-weight: 600;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.select-wrap {
		position: relative;
	}

	.pill-select {
		appearance: none;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 20px;
		padding: 8px 32px 8px 14px;
		font-family: inherit;
		font-size: 0.88rem;
		color: var(--text);
		cursor: pointer;
		background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%23888'/%3E%3C/svg%3E");
		background-repeat: no-repeat;
		background-position: right 12px center;
		min-width: 200px;
	}

	.pill-select:focus {
		outline: 2px solid var(--text);
		outline-offset: 2px;
	}

	.range-tabs {
		display: flex;
		gap: 4px;
	}

	.range-tab {
		font-size: 0.82rem;
		font-weight: 500;
		padding: 6px 14px;
		border-radius: 20px;
		border: 1px solid var(--border);
		background: transparent;
		color: var(--text-muted);
		cursor: pointer;
		font-family: inherit;
		transition: background 0.12s, color 0.12s;
	}

	.range-tab.active {
		background: var(--text);
		color: var(--bg);
		border-color: var(--text);
	}

	.range-tab:not(.active):hover {
		background: var(--bg-subtle);
		color: var(--text);
	}

	.month-picker {
		display: flex;
		align-items: center;
		gap: 8px;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 20px;
		padding: 6px 12px;
	}

	.arrow-btn {
		background: none;
		border: none;
		font-size: 1.2rem;
		color: var(--text-muted);
		cursor: pointer;
		padding: 0 2px;
		line-height: 1;
		transition: color 0.15s;
	}

	.arrow-btn:hover:not(:disabled) {
		color: var(--text);
	}

	.arrow-btn:disabled {
		opacity: 0.3;
		cursor: not-allowed;
	}

	.month-display {
		font-size: 0.88rem;
		font-weight: 600;
		color: var(--text);
		min-width: 72px;
		text-align: center;
	}

	.analyse-btn {
		align-self: flex-start;
		background: var(--text);
		color: var(--bg);
		border: none;
		border-radius: 20px;
		padding: 10px 28px;
		font-family: inherit;
		font-size: 0.9rem;
		font-weight: 700;
		cursor: pointer;
		transition: opacity 0.15s;
	}

	.analyse-btn:hover:not(:disabled) {
		opacity: 0.85;
	}

	.analyse-btn:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}

	/* Stats */
	.stat-row {
		display: flex;
		align-items: baseline;
		gap: 20px;
		flex-wrap: wrap;
	}

	.stat-big {
		display: flex;
		align-items: baseline;
		gap: 8px;
	}

	.stat-number {
		font-size: 3rem;
		font-weight: 800;
		color: var(--text);
		line-height: 1;
	}

	.stat-label {
		font-size: 0.9rem;
		color: var(--text-muted);
	}

	.stat-chip {
		display: flex;
		flex-direction: column;
		background: var(--bg-subtle);
		border-radius: 10px;
		padding: 8px 14px;
	}

	.stat-chip-value {
		font-size: 1.1rem;
		font-weight: 700;
		color: var(--text);
	}

	.stat-chip-label {
		font-size: 0.72rem;
		color: var(--text-muted);
	}

	/* Intensity bar */
	.intensity-row {
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.intensity-label {
		font-size: 0.82rem;
		color: var(--text-muted);
		min-width: 80px;
	}

	.intensity-track {
		flex: 1;
		height: 8px;
		background: var(--bg-subtle);
		border-radius: 4px;
		overflow: hidden;
	}

	.intensity-fill {
		height: 100%;
		border-radius: 4px;
		transition: width 0.4s ease;
	}

	.intensity-value {
		font-size: 0.82rem;
		font-weight: 600;
		color: var(--text);
		min-width: 32px;
	}

	/* Heatmap */
	.heatmap-wrap {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.heatmap-legend {
		font-size: 0.78rem;
		color: var(--text-muted);
	}

	.heatmap {
		display: flex;
		flex-wrap: nowrap;
		gap: 3px;
		overflow-x: auto;
	}

	.heatmap-cell {
		flex-shrink: 0;
		width: 24px;
		height: 24px;
		border-radius: 3px;
		transition: background 0.2s;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 0.7rem;
		color: var(--text-muted);
	}

	/* Correlation table */
	.table-scroll {
		overflow-x: auto;
		-webkit-overflow-scrolling: touch;
	}

	.corr-table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.85rem;
	}

	.corr-table th {
		text-align: left;
		padding: 6px 8px;
		font-size: 0.72rem;
		font-weight: 700;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
		border-bottom: 1px solid var(--border);
	}

	.corr-table td {
		padding: 9px 8px;
		border-bottom: 1px solid var(--bg-subtle);
		color: var(--text);
		vertical-align: middle;
	}

	.corr-table tr:last-child td {
		border-bottom: none;
	}

	.num-col {
		text-align: right;
		white-space: nowrap;
	}

	.badge-col {
		text-align: right;
		white-space: nowrap;
	}

	.trigger-name {
		display: block;
		font-weight: 500;
	}

	.trigger-cat {
		display: block;
		font-size: 0.72rem;
		color: var(--text-subtle);
		text-transform: capitalize;
	}

	/* Badges */
	.badge {
		display: inline-block;
		padding: 2px 8px;
		border-radius: 10px;
		font-size: 0.72rem;
		font-weight: 700;
	}

	.badge-strong {
		background: #fee2e2;
		color: #991b1b;
	}

	:global(.dark) .badge-strong {
		background: #450a0a;
		color: #fca5a5;
	}

	.badge-possible {
		background: #fef3c7;
		color: #92400e;
	}

	:global(.dark) .badge-possible {
		background: #451a03;
		color: #fcd34d;
	}

	.badge-none {
		background: var(--bg-subtle);
		color: var(--text-muted);
	}

	/* Environmental cards */
	.env-cards {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.env-card {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 12px;
		padding: 14px;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.env-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.env-label {
		font-size: 0.88rem;
		font-weight: 600;
		color: var(--text);
	}

	.phi-track {
		height: 6px;
		background: var(--bg-subtle);
		border-radius: 3px;
		overflow: hidden;
	}

	.phi-fill {
		height: 100%;
		background: var(--text-muted);
		border-radius: 3px;
		transition: width 0.4s ease;
	}

	.env-detail {
		font-size: 0.82rem;
		color: var(--text-muted);
		margin: 0;
	}

	.env-detail strong {
		color: var(--text);
	}

	/* Medication banner */
	.med-banner {
		display: flex;
		gap: 12px;
		background: #fef9c3;
		border: 1px solid #fde047;
		border-radius: 12px;
		padding: 14px;
	}

	:global(.dark) .med-banner {
		background: #422006;
		border-color: #a16207;
	}

	.med-banner-icon {
		font-size: 1.2rem;
		flex-shrink: 0;
	}

	.med-banner-title {
		font-size: 0.88rem;
		font-weight: 700;
		color: #92400e;
		margin: 0 0 4px;
	}

	:global(.dark) .med-banner-title {
		color: #fcd34d;
	}

	.med-banner-item {
		font-size: 0.82rem;
		color: #78350f;
		margin: 2px 0 0;
	}

	:global(.dark) .med-banner-item {
		color: #fbbf24;
	}

	/* Empty state */
	.empty-state {
		text-align: center;
		padding: 48px 16px;
		color: var(--text-muted);
		font-size: 0.9rem;
	}
</style>

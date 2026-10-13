<script lang="ts">
	import { api } from '$lib/api.js';
	import { t } from '$lib/stores/locale.js';
	import type { UserProfile } from '$lib/types';

	interface GeoResult {
		id: number;
		name: string;
		country: string;
		admin1?: string;
		latitude: number;
		longitude: number;
	}

	interface Props {
		data: { profile: UserProfile };
	}

	let { data }: Props = $props();

	// Form state mirroring profile
	// svelte-ignore state_referenced_locally
	let displayName = $state(String(data.profile.displayName ?? ''));
	// svelte-ignore state_referenced_locally
	let bloodType = $state<string>(String(data.profile.bloodType ?? ''));
	// svelte-ignore state_referenced_locally
	let locationName = $state(String(data.profile.locationName ?? ''));
	// svelte-ignore state_referenced_locally
	let locationLat = $state<number | null>(data.profile.locationLat ?? null);
	// svelte-ignore state_referenced_locally
	let locationLon = $state<number | null>(data.profile.locationLon ?? null);

	let citySearch = $state('');
	let geoResults = $state<GeoResult[]>([]);
	let geoLoading = $state(false);
	let saving = $state(false);
	let saveSuccess = $state(false);
	let saveError = $state<string | null>(null);

	const bloodTypes = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

	async function searchLocation() {
		const query = citySearch.trim();
		if (!query) return;
		geoLoading = true;
		geoResults = [];
		try {
			const res = await api(`/api/profile/location-search?q=${encodeURIComponent(query)}`);
			if (res.ok) {
				const json = await res.json();
				geoResults = json.results ?? [];
			}
		} catch (e) {
		} finally {
			geoLoading = false;
		}
	}

	function selectLocation(result: GeoResult) {
		locationLat = result.latitude;
		locationLon = result.longitude;
		locationName = [result.name, result.admin1, result.country].filter(Boolean).join(', ');
		geoResults = [];
		citySearch = '';
	}

	function clearLocation() {
		locationLat = null;
		locationLon = null;
		locationName = '';
	}

	async function save() {
		saving = true;
		saveSuccess = false;
		saveError = null;
		try {
			const res = await api('/api/profile', {
				method: 'PUT',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					displayName: displayName || null,
					bloodType: bloodType || null,
					locationLat,
					locationLon,
					locationName: locationName || null
				})
			});
			if (!res.ok) {
				const err = await res.json().catch(() => ({}));
				saveError = err.message ?? t('profile_save_failed');
			} else {
				saveSuccess = true;
				setTimeout(() => { saveSuccess = false; }, 3000);
			}
		} catch (e) {
			saveError = t('network_error');
		} finally {
			saving = false;
		}
	}
</script>

<div class="page">
	<header class="page-header">
		<a href="/settings" class="back-link">← {t('settings_title')}</a>
		<h1 class="page-title">{t('profile_title')}</h1>
	</header>

	<form class="form" onsubmit={(e) => { e.preventDefault(); save(); }}>
		<!-- Display name -->
		<div class="field">
			<label class="field-label" for="display-name">{t('profile_display_name')}</label>
			<input
				id="display-name"
				type="text"
				class="input"
				placeholder={t('profile_display_name_placeholder')}
				bind:value={displayName}
				autocomplete="name"
			/>
		</div>

		<!-- Blood type -->
		<div class="field">
			<label class="field-label" for="blood-type">{t('profile_blood_type')}</label>
			<div class="select-wrap">
				<select id="blood-type" class="input select" bind:value={bloodType}>
					<option value="">{t('profile_blood_type_unset')}</option>
					{#each bloodTypes as bt (bt)}
						<option value={bt}>{bt}</option>
					{/each}
				</select>
			</div>
		</div>

		<!-- Location -->
		<div class="field">
			<label class="field-label" for="city-search">{t('profile_location')}</label>

			{#if locationName}
				<div class="location-selected">
					<span class="location-icon" aria-hidden="true">📍</span>
					<span class="location-name">{locationName}</span>
					{#if locationLat !== null && locationLon !== null}
						<span class="location-coords">{locationLat.toFixed(3)}, {locationLon.toFixed(3)}</span>
					{/if}
					<button
						type="button"
						class="location-clear"
						onclick={clearLocation}
						aria-label={t('profile_location_clear')}
					>×</button>
				</div>
			{:else}
				<div class="location-search-row">
					<input
						id="city-search"
						type="text"
						class="input"
						placeholder={t('profile_location_search')}
						bind:value={citySearch}
						onkeydown={(e) => { if (e.key === 'Enter') { e.preventDefault(); searchLocation(); } }}
					/>
					<button
						type="button"
						class="search-btn"
						onclick={searchLocation}
						disabled={geoLoading || !citySearch.trim()}
					>
						{geoLoading ? '…' : t('profile_location_search_button')}
					</button>
				</div>
			{/if}

			<!-- Geo results dropdown -->
			{#if geoResults.length > 0}
				<ul class="geo-results" role="listbox" aria-label={t('profile_location_suggestions')}>
					{#each geoResults as result (result.id)}
						<li
							class="geo-result"
							role="option"
							aria-selected="false"
							tabindex="0"
							onclick={() => selectLocation(result)}
							onkeydown={(e) => { if (e.key === 'Enter' || e.key === ' ') selectLocation(result); }}
						>
							<span class="geo-name">{result.name}</span>
							<span class="geo-meta">{[result.admin1, result.country].filter(Boolean).join(', ')}</span>
							<span class="geo-coords">{result.latitude.toFixed(3)}, {result.longitude.toFixed(3)}</span>
						</li>
					{/each}
				</ul>
			{/if}
		</div>

		<!-- Save -->
		<div class="save-row">
			<button type="submit" class="save-btn" disabled={saving}>
				{saving ? t('entry_types_saving') : t('save')}
			</button>
			{#if saveSuccess}
				<span class="save-feedback success">{t('profile_save_success')} ✓</span>
			{/if}
			{#if saveError}
				<span class="save-feedback error">{saveError}</span>
			{/if}
		</div>
	</form>
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

	/* Form */
	.form {
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.field-label {
		font-size: 0.78rem;
		font-weight: 700;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.input {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 10px 14px;
		font-family: inherit;
		font-size: 0.9rem;
		color: var(--text);
		transition: border-color 0.15s;
		width: 100%;
	}

	.input:focus {
		outline: none;
		border-color: var(--text-muted);
	}

	.select-wrap {
		position: relative;
	}

	.select {
		appearance: none;
		background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%23888'/%3E%3C/svg%3E");
		background-repeat: no-repeat;
		background-position: right 12px center;
		cursor: pointer;
	}

	/* Location */
	.location-selected {
		display: flex;
		align-items: center;
		gap: 8px;
		background: var(--bg-subtle);
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 10px 14px;
		flex-wrap: wrap;
	}

	.location-icon {
		font-size: 1rem;
		flex-shrink: 0;
	}

	.location-name {
		font-size: 0.9rem;
		font-weight: 500;
		color: var(--text);
		flex: 1;
	}

	.location-coords {
		font-size: 0.72rem;
		color: var(--text-subtle);
		font-family: monospace;
	}

	.location-clear {
		background: none;
		border: none;
		font-size: 1.1rem;
		color: var(--text-muted);
		cursor: pointer;
		padding: 0;
		line-height: 1;
		margin-left: auto;
	}

	.location-clear:hover {
		color: var(--text);
	}

	.location-search-row {
		display: flex;
		gap: 8px;
	}

	.location-search-row .input {
		flex: 1;
	}

	.search-btn {
		background: var(--text);
		color: var(--bg);
		border: none;
		border-radius: 10px;
		padding: 10px 16px;
		font-family: inherit;
		font-size: 0.88rem;
		font-weight: 600;
		cursor: pointer;
		white-space: nowrap;
		transition: opacity 0.15s;
	}

	.search-btn:hover:not(:disabled) {
		opacity: 0.85;
	}

	.search-btn:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}

	/* Geo results */
	.geo-results {
		list-style: none;
		margin: 0;
		padding: 0;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 10px;
		overflow: hidden;
	}

	.geo-result {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 11px 14px;
		cursor: pointer;
		transition: background 0.12s;
		border-bottom: 1px solid var(--bg-subtle);
	}

	.geo-result:last-child {
		border-bottom: none;
	}

	.geo-result:hover,
	.geo-result:focus {
		background: var(--bg-subtle);
		outline: none;
	}

	.geo-name {
		font-size: 0.9rem;
		font-weight: 600;
		color: var(--text);
	}

	.geo-meta {
		font-size: 0.8rem;
		color: var(--text-muted);
		flex: 1;
	}

	.geo-coords {
		font-size: 0.72rem;
		color: var(--text-subtle);
		font-family: monospace;
	}

	/* Save row */
	.save-row {
		display: flex;
		align-items: center;
		gap: 12px;
		padding-top: 4px;
	}

	.save-btn {
		background: var(--text);
		color: var(--bg);
		border: none;
		border-radius: 20px;
		padding: 11px 32px;
		font-family: inherit;
		font-size: 0.92rem;
		font-weight: 700;
		cursor: pointer;
		transition: opacity 0.15s;
	}

	.save-btn:hover:not(:disabled) {
		opacity: 0.85;
	}

	.save-btn:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}

	.save-feedback {
		font-size: 0.85rem;
		font-weight: 600;
	}

	.save-feedback.success {
		color: #16a34a;
	}

	.save-feedback.error {
		color: #dc2626;
	}
</style>

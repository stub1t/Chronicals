<script lang="ts">
	import MapPin from '@lucide/svelte/icons/map-pin';
	import Search from '@lucide/svelte/icons/search';
	import X from '@lucide/svelte/icons/x';
	import { api } from '$lib/api.js';
	import SubPage from '$lib/me/SubPage.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { hapticSuccess } from '$lib/haptics.js';
	import { t } from '$lib/i18n';
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
		} catch {
			toasts.show(t('network_error'), 'error');
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
		try {
			const res = await api('/api/profile', {
				method: 'PUT',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					displayName: displayName.trim() || null,
					bloodType: bloodType || null,
					locationLat,
					locationLon,
					locationName: locationName || null
				})
			});
			if (!res.ok) {
				const err = await res.json().catch(() => ({}));
				toasts.show(err.message ?? t('profile_save_failed'), 'error');
			} else {
				hapticSuccess();
				toasts.show(t('profile_save_success'), 'success');
			}
		} catch {
			toasts.show(t('network_error'), 'error');
		} finally {
			saving = false;
		}
	}
</script>

<SubPage title={t('profile_title')}>
	<form class="form" onsubmit={(e) => { e.preventDefault(); save(); }}>
		<Card>
			<div class="fields">
				<div class="ui-field">
					<label class="ui-label" for="display-name">{t('profile_display_name')}</label>
					<input
						id="display-name"
						type="text"
						class="ui-input"
						placeholder={t('profile_display_name_placeholder')}
						bind:value={displayName}
						autocomplete="given-name"
						maxlength="120"
					/>
					<p class="ui-hint">{t('profile_display_name_hint')}</p>
				</div>

				<div class="ui-field">
					<span class="ui-label" id="blood-type-label">{t('profile_blood_type')}</span>
					<div class="blood" role="radiogroup" aria-labelledby="blood-type-label">
						{#each ['', ...bloodTypes] as bt (bt)}
							<button
								type="button"
								role="radio"
								aria-checked={bloodType === bt}
								class="blood-option"
								class:picked={bloodType === bt}
								onclick={() => (bloodType = bt)}
							>
								{bt || t('profile_blood_type_unset')}
							</button>
						{/each}
					</div>
				</div>
			</div>
		</Card>

		<Card>
			<div class="fields">
				<div class="ui-field">
					<label class="ui-label" for="city-search">{t('profile_location')}</label>
					<p class="ui-hint">{t('profile_location_hint')}</p>

					{#if locationName}
						<div class="location">
							<span class="pin" aria-hidden="true"><MapPin size={20} /></span>
							<span class="location-text">
								<span class="location-name">{locationName}</span>
								{#if locationLat !== null && locationLon !== null}
									<span class="coords">{locationLat.toFixed(3)}, {locationLon.toFixed(3)}</span>
								{/if}
							</span>
							<button type="button" class="clear" onclick={clearLocation} aria-label={t('profile_location_clear')}>
								<X size={18} />
							</button>
						</div>
					{:else}
						<div class="search">
							<input
								id="city-search"
								type="text"
								class="ui-input"
								placeholder={t('profile_location_search')}
								bind:value={citySearch}
								onkeydown={(e) => {
									if (e.key === 'Enter') {
										e.preventDefault();
										searchLocation();
									}
								}}
							/>
							<Button
								variant="secondary"
								loading={geoLoading}
								disabled={!citySearch.trim()}
								onclick={searchLocation}
								aria-label={t('profile_location_search_button')}
							>
								<Search size={18} />
							</Button>
						</div>
					{/if}

					{#if geoResults.length > 0}
						<ul class="results" aria-label={t('profile_location_suggestions')}>
							{#each geoResults as result (result.id)}
								<li>
									<button type="button" class="result" onclick={() => selectLocation(result)}>
										<span class="result-name">{result.name}</span>
										<span class="result-meta">{[result.admin1, result.country].filter(Boolean).join(', ')}</span>
									</button>
								</li>
							{/each}
						</ul>
					{/if}
				</div>
			</div>
		</Card>

		<Button type="submit" size="lg" block loading={saving}>{t('save')}</Button>
	</form>
</SubPage>

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.fields {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.blood {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));
		gap: var(--space-2);
	}

	.blood-option {
		min-height: 44px;
		border-radius: var(--radius-md);
		border: 1.5px solid var(--border);
		background: var(--bg-card);
		color: var(--text);
		font-weight: 700;
		cursor: pointer;
	}

	.blood-option:first-child {
		grid-column: 1 / -1;
	}

	.blood-option.picked {
		border-color: var(--danger);
		background: var(--danger-soft);
		color: var(--danger);
	}

	.location {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3);
		border-radius: var(--radius-md);
		background: var(--accent-soft);
	}

	.pin {
		color: var(--accent);
		display: grid;
	}

	.location-text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.location-name {
		font-weight: 700;
	}

	.coords {
		font-size: var(--text-xs);
		color: var(--text-muted);
		font-variant-numeric: tabular-nums;
	}

	.clear {
		width: 40px;
		height: 40px;
		display: grid;
		place-items: center;
		border: none;
		border-radius: 50%;
		background: var(--bg-card);
		color: var(--text-muted);
		cursor: pointer;
	}

	.search {
		display: flex;
		gap: var(--space-2);
	}

	.results {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.result {
		width: 100%;
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 2px;
		padding: var(--space-2) var(--space-3);
		border: none;
		border-radius: var(--radius-md);
		background: var(--bg-subtle);
		color: var(--text);
		text-align: start;
		cursor: pointer;
	}

	.result-name {
		font-weight: 700;
	}

	.result-meta {
		font-size: var(--text-sm);
		color: var(--text-muted);
	}
</style>

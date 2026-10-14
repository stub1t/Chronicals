<script lang="ts">
	import { api } from '$lib/api.js';
	import SubPage from '$lib/me/SubPage.svelte';
	import LocationSearch, { type Place } from '$lib/me/LocationSearch.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { hapticSuccess } from '$lib/haptics.js';
	import { t } from '$lib/i18n';
	import type { UserProfile } from '$lib/types';

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
	let place = $state<Place | null>(
		data.profile.locationLat != null && data.profile.locationLon != null
			? { lat: data.profile.locationLat, lon: data.profile.locationLon, name: data.profile.locationName ?? '' }
			: null
	);

	let saving = $state(false);

	const bloodTypes = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

	async function save() {
		saving = true;
		try {
			const res = await api('/api/profile', {
				method: 'PUT',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					displayName: displayName.trim() || null,
					bloodType: bloodType || null,
					locationLat: place?.lat ?? null,
					locationLon: place?.lon ?? null,
					locationName: place?.name || null
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

					<LocationSearch bind:value={place} id="city-search" />
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











</style>

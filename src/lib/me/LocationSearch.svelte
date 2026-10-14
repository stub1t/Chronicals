<script lang="ts">
	/** Find a place by name (Open-Meteo geocoding) and pick it; or show the picked one with a clear button. */
	import MapPin from '@lucide/svelte/icons/map-pin';
	import Search from '@lucide/svelte/icons/search';
	import X from '@lucide/svelte/icons/x';
	import { api } from '$lib/api.js';
	import Button from '$lib/ui/Button.svelte';
	import { toasts } from '$lib/ui/toast.svelte.js';
	import { t } from '$lib/i18n';

	export interface Place {
		lat: number;
		lon: number;
		name: string;
	}

	interface GeoResult {
		id: number;
		name: string;
		country: string;
		admin1?: string;
		latitude: number;
		longitude: number;
	}

	interface Props {
		value: Place | null;
		onchange?: (value: Place | null) => void;
		/** id of the search input, for a label elsewhere. */
		id?: string;
	}

	let { value = $bindable(), onchange, id = 'place-search' }: Props = $props();

	let query = $state('');
	let results = $state<GeoResult[]>([]);
	let loading = $state(false);

	async function search() {
		const q = query.trim();
		if (!q) return;
		loading = true;
		results = [];
		try {
			const res = await api(`/api/profile/location-search?q=${encodeURIComponent(q)}`);
			if (res.ok) results = (await res.json()).results ?? [];
		} catch {
			toasts.show(t('network_error'), 'error');
		} finally {
			loading = false;
		}
	}

	function pick(result: GeoResult) {
		value = {
			lat: result.latitude,
			lon: result.longitude,
			name: [result.name, result.admin1, result.country].filter(Boolean).join(', ')
		};
		results = [];
		query = '';
		onchange?.(value);
	}

	function clear() {
		value = null;
		onchange?.(null);
	}
</script>

{#if value}
	<div class="location">
		<span class="pin" aria-hidden="true"><MapPin size={20} /></span>
		<span class="text">
			<span class="name">{value.name}</span>
			<span class="coords">{value.lat.toFixed(3)}, {value.lon.toFixed(3)}</span>
		</span>
		<button type="button" class="clear" onclick={clear} aria-label={t('profile_location_clear')}>
			<X size={18} />
		</button>
	</div>
{:else}
	<div class="search">
		<input
			{id}
			type="text"
			class="ui-input"
			placeholder={t('profile_location_search')}
			bind:value={query}
			onkeydown={(e) => {
				if (e.key === 'Enter') {
					e.preventDefault();
					search();
				}
			}}
		/>
		<Button variant="secondary" loading={loading} disabled={!query.trim()} onclick={search} aria-label={t('profile_location_search_button')}>
			<Search size={18} />
		</Button>
	</div>
{/if}

{#if results.length > 0}
	<ul class="results" aria-label={t('profile_location_suggestions')}>
		{#each results as result (result.id)}
			<li>
				<button type="button" class="result" onclick={() => pick(result)}>
					<span class="result-name">{result.name}</span>
					<span class="result-meta">{[result.admin1, result.country].filter(Boolean).join(', ')}</span>
				</button>
			</li>
		{/each}
	</ul>
{/if}

<style>
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

	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.name {
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

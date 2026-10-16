package io.github.stub1t.chronicals.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.stub1t.chronicals.ChronicalsApp
import io.github.stub1t.chronicals.data.Times
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The phone's rough position from Android's own LocationManager (no Google Play Services), and the
 * optional daily "where am I" for weather while travelling. Port of LocationPlugin.java and mobile/location.ts.
 */
object PhoneLocation {
    /** Farther than this from home counts as away. */
    private const val AWAY_KM = 25.0
    private val PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, "fused", LocationManager.GPS_PROVIDER)

    fun allowed(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Rough coordinates: a known position up to an hour old, else a fresh one within 15 s. */
    suspend fun current(context: Context): Pair<Double, Double>? {
        if (!allowed(context)) return null
        val manager = context.getSystemService(LocationManager::class.java)
        val enabled = manager.getProviders(true)
        val last = enabled.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time <= 3_600_000) return last.latitude to last.longitude
        val provider = PROVIDERS.firstOrNull { it in enabled } ?: return last?.let { it.latitude to it.longitude }
        val fresh: Location? = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                } catch (_: SecurityException) { cont.resume(null) }
            }
        }
        return (fresh ?: last)?.let { it.latitude to it.longitude }
    }

    fun distanceKm(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        fun rad(d: Double) = d * Math.PI / 180
        val h = sin(rad(b.first - a.first) / 2).pow(2) + cos(rad(a.first)) * cos(rad(b.first)) * sin(rad(b.second - a.second) / 2).pow(2)
        return 2 * 6371 * asin(sqrt(h))
    }

    /** Away from home, today gets the current place (unless set by hand); back home clears an automatic place. */
    suspend fun updateTodaysPlace(context: Context) {
        val app = context.applicationContext as ChronicalsApp
        if (!app.settings.autoLocation.value) return
        val date = Times.today().toString()
        val profile = app.diary.profile()
        val entry = app.diary.entryByDate(date)
        if (!profile.weatherEnabled || entry?.locationSource == "manual") return
        val here = current(context) ?: return
        val home = if (profile.locationLat != null && profile.locationLon != null) profile.locationLat to profile.locationLon else null
        if (home == null || distanceKm(home, here) > AWAY_KM) {
            // Rounded to about a kilometre: that is all the weather needs.
            app.diary.setDayPlace(date, round(here.first * 100) / 100, round(here.second * 100) / 100, null, "auto")
        } else if (entry?.locationSource == "auto") app.diary.setDayPlace(date, null, null, null, null)
    }
}

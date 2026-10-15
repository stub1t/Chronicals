package io.github.stub1t.chronicals;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The phone's approximate position from Android's own LocationManager, so the
 * app works without Google Play Services. Only coarse location is asked for.
 * checkPermissions() and requestPermissions() come from the Plugin base class.
 */
@CapacitorPlugin(
    name = "Location",
    permissions = @Permission(alias = "coarseLocation", strings = { Manifest.permission.ACCESS_COARSE_LOCATION })
)
public class LocationPlugin extends Plugin {

    /** Providers to switch on for a fresh position, in order of preference. */
    private static final String[] PROVIDERS = { LocationManager.NETWORK_PROVIDER, "fused", LocationManager.GPS_PROVIDER };

    @PluginMethod
    public void getCurrentPosition(PluginCall call) {
        if (getPermissionState("coarseLocation") != PermissionState.GRANTED) {
            call.reject("Location permission not granted");
            return;
        }
        LocationManager manager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        long maximumAge = call.getLong("maximumAge", 3_600_000L);
        int timeout = call.getInt("timeout", 15_000);
        // Only the providers that are turned on and that this app may use.
        List<String> enabled = manager.getProviders(true);

        // A recent enough known position needs no provider switched on.
        Location last = null;
        for (String provider : enabled) {
            try {
                Location known = manager.getLastKnownLocation(provider);
                if (known != null && (last == null || known.getTime() > last.getTime())) last = known;
            } catch (SecurityException ignored) {
                // Not allowed for this provider.
            }
        }
        if (last != null && System.currentTimeMillis() - last.getTime() <= maximumAge) {
            call.resolve(toResult(last));
            return;
        }

        String provider = null;
        for (String candidate : PROVIDERS) {
            if (enabled.contains(candidate)) {
                provider = candidate;
                break;
            }
        }
        final Location fallback = last;
        if (provider == null) {
            if (fallback != null) call.resolve(toResult(fallback));
            else call.reject("Location is turned off");
            return;
        }

        AtomicBoolean done = new AtomicBoolean(false);
        CancellationSignal cancel = new CancellationSignal();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable onTimeout = () -> {
            if (!done.compareAndSet(false, true)) return;
            cancel.cancel();
            if (fallback != null) call.resolve(toResult(fallback));
            else call.reject("No position in time");
        };
        handler.postDelayed(onTimeout, timeout);
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(getContext()), (location) -> {
                if (!done.compareAndSet(false, true)) return;
                handler.removeCallbacks(onTimeout);
                if (location != null) call.resolve(toResult(location));
                else if (fallback != null) call.resolve(toResult(fallback));
                else call.reject("No position found");
            });
        } catch (SecurityException e) {
            if (done.compareAndSet(false, true)) {
                handler.removeCallbacks(onTimeout);
                call.reject("Location permission not granted");
            }
        }
    }

    private static JSObject toResult(Location location) {
        JSObject result = new JSObject();
        result.put("lat", location.getLatitude());
        result.put("lon", location.getLongitude());
        return result;
    }
}

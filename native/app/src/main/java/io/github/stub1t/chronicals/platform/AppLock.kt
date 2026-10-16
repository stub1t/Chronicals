package io.github.stub1t.chronicals.platform

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * Fingerprint, face or the phone's screen lock, with Android's own prompt (no extra library). Keeps someone
 * holding the unlocked phone out of the diary; it does not encrypt anything.
 */
object AppLock {
    /** Relock after this long in the background. */
    const val RELOCK_AFTER_MS = 60_000L

    /** Needs a screen lock on the phone. */
    fun available(context: Context) = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    /**
     * Shows the prompt. Before Android 9 there is no combined prompt, so [fallback] receives the system's
     * screen-lock confirmation to start for a result.
     */
    @Suppress("DEPRECATION")
    fun prompt(context: Context, title: String, onResult: (Boolean) -> Unit, fallback: (Intent) -> Unit) {
        if (Build.VERSION.SDK_INT < 28) {
            val intent = context.getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) onResult(true) else fallback(intent)
            return
        }
        val builder = BiometricPrompt.Builder(context).setTitle(title)
        if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        else builder.setDeviceCredentialAllowed(true)
        builder.build().authenticate(CancellationSignal(), ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        })
    }
}

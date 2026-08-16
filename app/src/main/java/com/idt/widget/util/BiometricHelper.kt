package com.idt.widget.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CompletableDeferred

class BiometricHelper(private val activity: FragmentActivity) {

    private val executor = ContextCompat.getMainExecutor(activity)

    fun authenticate(reason: String = "Autentique para acessar o IDT Status"): CompletableDeferred<Boolean> = CompletableDeferred()

    fun authenticateWithCallback(
        reason: String = "Autentique para acessar o IDT Status",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("IDT Status")
            .setSubtitle("Autenticação biométrica")
            .setDescription(reason)
            .setNegativeButtonText("Cancelar")
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Autenticação falhou")
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }

    companion object {
        fun isBiometricAvailable(context: Context): Boolean {
            val biometricManager = androidx.biometric.BiometricManager.from(context)
            val authResult = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                }
                else -> {
                    biometricManager.canAuthenticate(0)
                }
            }
            return authResult == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        }
    }
}
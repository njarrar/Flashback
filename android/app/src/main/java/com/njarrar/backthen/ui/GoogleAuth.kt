package com.njarrar.backthen.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.njarrar.backthen.AppViewModel
import com.njarrar.backthen.core.L
import com.njarrar.backthen.data.PICKER_SCOPE

/**
 * Returns a function that asks Google for access to the Photos Picker (a consent screen
 * the first time), then runs the picker. The app's Android OAuth client is matched by
 * package name and signing key in Google Cloud, so no client ID lives in the code.
 */
@Composable
fun rememberGoogleImport(vm: AppViewModel): () -> Unit {
    val context = LocalContext.current
    val client = Identity.getAuthorizationClient(context)
    val openPicker = { uri: android.net.Uri -> CustomTabsIntent.Builder().build().launchUrl(context, uri) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching { client.getAuthorizationResultFromIntent(result.data) }
            .onSuccess { auth -> auth.accessToken?.let { vm.importFromGoogle(it, openPicker) } }
            .onFailure { vm.say(L.s("toast_google_signin_failed")) }
    }

    return {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(PICKER_SCOPE))).build()
        client.authorize(request)
            .addOnSuccessListener { auth ->
                val pending = auth.pendingIntent
                if (auth.hasResolution() && pending != null) {
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    auth.accessToken?.let { vm.importFromGoogle(it, openPicker) }
                }
            }
            .addOnFailureListener { vm.say(L.s("toast_google_not_setup")) }
    }
}

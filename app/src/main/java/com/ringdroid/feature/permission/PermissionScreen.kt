package com.ringdroid.feature.permission

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ringdroid.BuildConfig
import com.ringdroid.R
import com.ringdroid.core.permissions.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onNext: () -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasStorage by remember { mutableStateOf(false) }
    var hasMediaAudio by remember { mutableStateOf(false) }
    var hasWriteSettings by remember { mutableStateOf(false) }
    var hasContacts by remember { mutableStateOf(false) }
    var hasMic by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        hasStorage = PermissionUtils.hasStoragePermission(context)
        hasMediaAudio = PermissionUtils.hasMediaAudioPermission(context)
        hasWriteSettings = PermissionUtils.hasWriteSettingsPermission(context)
        hasContacts = PermissionUtils.hasContactPermissions(context)
        hasMic = PermissionUtils.hasMicPermissions(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Initial load
    LaunchedEffect(Unit) { refreshPermissions() }

    val canContinue = hasStorage || hasMediaAudio

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ringdroid_app_name)) }) }
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.padding(16.dp).width(IntrinsicSize.Max)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRowWithSummary(
                        title = stringResource(R.string.media_audio_permission),
                        summary = stringResource(R.string.media_audio_summary),
                        checked = hasMediaAudio,
                        enabled = !hasMediaAudio,
                    ) {
                        PermissionUtils.requestMediaAudioPermission(activity)
                    }
                }

                if (BuildConfig.FLAVOR != "play") {
                    PermissionRowWithSummary(
                        title = stringResource(R.string.storage_permission),
                        summary = stringResource(R.string.storage_permission_summary),
                        checked = hasStorage,
                        enabled = !hasStorage,
                    ) {
                        PermissionUtils.requestStoragePermission(activity)
                    }
                }

                PermissionRowWithSummary(
                    title = stringResource(R.string.write_system_settings),
                    summary = stringResource(R.string.write_system_settings_summary),
                    checked = hasWriteSettings,
                    enabled = !hasWriteSettings,
                ) {
                    PermissionUtils.requestWriteSettingsPermission(context)
                }

                PermissionRowWithSummary(
                    title = stringResource(R.string.mic_permission),
                    summary = stringResource(R.string.mic_permission_summary),
                    checked = hasMic,
                    enabled = !hasMic,
                ) {
                    PermissionUtils.requestMicPermissions(activity)
                }

                PermissionRowWithSummary(
                    title = stringResource(R.string.contacts_permission),
                    summary = stringResource(R.string.contact_permission_summary),
                    checked = hasContacts,
                    enabled = !hasContacts,
                ) {
                    PermissionUtils.requestContactPermissions(activity)
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onNext,
                enabled = canContinue,
                modifier = Modifier.wrapContentWidth().heightIn(min = 44.dp),
            ) {
                Text(
                    text = stringResource(R.string.start),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

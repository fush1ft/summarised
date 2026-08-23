package io.github.fush1ft.summarised.feature.record

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.core.audio.RecorderStatus
import io.github.fush1ft.summarised.core.designsystem.component.Waveform
import io.github.fush1ft.summarised.core.designsystem.theme.TimerTextStyle
import io.github.fush1ft.summarised.core.designsystem.util.formatTimer

@Composable
fun RecordRoute(viewModel: RecordViewModel = hiltViewModel()) {
    val state by viewModel.recorderState.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.record_saved)
    val tooShortMessage = stringResource(R.string.record_too_short)

    LaunchedEffect(event) {
        when (event) {
            RecordEvent.SAVED -> snackbarHostState.showSnackbar(savedMessage)
            RecordEvent.TOO_SHORT -> snackbarHostState.showSnackbar(tooShortMessage)
            null -> return@LaunchedEffect
        }
        viewModel.consumeEvent()
    }

    // Tracks whether the user has been asked yet, so the "denied" explanation only
    // appears after an actual refusal rather than on a fresh install.
    var hasRequested by rememberSaveable { mutableStateOf(false) }
    var micGranted by rememberSaveable {
        mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasRequested = true
        micGranted = results[Manifest.permission.RECORD_AUDIO] ?: micGranted
        if (micGranted) viewModel.start()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                micGranted -> RecordContent(
                    status = state.status,
                    elapsedMs = state.elapsedMs,
                    amplitudes = state.amplitudes,
                    errorMessage = state.errorMessage,
                    onStart = viewModel::start,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStop = viewModel::stopAndSave,
                    onDiscard = viewModel::discard,
                )

                else -> PermissionPrompt(
                    deniedBefore = hasRequested,
                    onRequest = { permissionLauncher.launch(requiredPermissions()) },
                    onOpenSettings = { context.openAppSettings() },
                )
            }
        }
    }
}

@Composable
private fun RecordContent(
    status: RecorderStatus,
    elapsedMs: Long,
    amplitudes: List<Float>,
    errorMessage: String?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatTimer(elapsedMs),
            style = TimerTextStyle,
            color = if (status == RecorderStatus.IDLE) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        if (status == RecorderStatus.PAUSED) {
            Text(
                text = stringResource(R.string.record_paused_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        Waveform(
            amplitudes = amplitudes,
            color = if (status == RecorderStatus.PAUSED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )

        Spacer(Modifier.height(40.dp))

        RecordButton(
            status = status,
            onClick = { if (status == RecorderStatus.IDLE) onStart() else onStop() },
        )

        Spacer(Modifier.height(24.dp))

        if (status == RecorderStatus.IDLE) {
            Text(
                text = stringResource(R.string.record_idle_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = if (status == RecorderStatus.PAUSED) onResume else onPause,
                ) {
                    Icon(
                        imageVector = if (status == RecorderStatus.PAUSED) {
                            Icons.Filled.PlayArrow
                        } else {
                            Icons.Filled.Pause
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            if (status == RecorderStatus.PAUSED) {
                                R.string.record_resume
                            } else {
                                R.string.record_pause
                            },
                        ),
                    )
                }
                OutlinedButton(onClick = onDiscard) {
                    Text(stringResource(R.string.record_discard))
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecordButton(status: RecorderStatus, onClick: () -> Unit) {
    val recording = status == RecorderStatus.RECORDING

    // A slow pulse while recording, so the screen reads as live at a glance from
    // across a table. It stops on pause, which is the point.
    val transition = rememberInfiniteTransition(label = "record-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-pulse-scale",
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    if (status == RecorderStatus.IDLE) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
        )
        Button(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = if (status == RecorderStatus.IDLE) {
                    Icons.Filled.Mic
                } else {
                    Icons.Filled.Stop
                },
                contentDescription = stringResource(
                    if (status == RecorderStatus.IDLE) {
                        R.string.record_start
                    } else {
                        R.string.record_stop
                    },
                ),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(
    deniedBefore: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_needed_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (deniedBefore) R.string.permission_denied_body else R.string.permission_needed_body,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (deniedBefore) {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings))
            }
        } else {
            Button(onClick = onRequest) {
                Text(stringResource(R.string.permission_grant))
            }
        }
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    // The recording notification is the user's only persistent signal that the
    // microphone is live, so it is worth asking for alongside the microphone.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun android.content.Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun android.content.Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

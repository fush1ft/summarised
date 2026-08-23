package io.github.fush1ft.summarised.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.core.data.model.Recording
import io.github.fush1ft.summarised.core.database.entity.RecordingStatus
import io.github.fush1ft.summarised.core.designsystem.component.EmptyState
import io.github.fush1ft.summarised.core.designsystem.util.formatDuration
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryRoute(viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var renaming by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf<Recording?>(null) }

    if (state.recordings.isEmpty() && !state.loading) {
        EmptyState(
            icon = Icons.Outlined.LibraryMusic,
            title = stringResource(R.string.library_empty_title),
            description = stringResource(R.string.library_empty_description),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.recordings, key = { it.id }) { recording ->
                RecordingCard(
                    recording = recording,
                    isPlaying = state.playingId == recording.id,
                    onTogglePlayback = { viewModel.togglePlayback(recording) },
                    onRename = { renaming = recording },
                    onDelete = { deleting = recording },
                )
            }
        }
    }

    renaming?.let { recording ->
        RenameDialog(
            initialTitle = recording.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                viewModel.rename(recording.id, title)
                renaming = null
            },
        )
    }

    deleting?.let { recording ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.library_delete_confirm_title)) },
            text = { Text(stringResource(R.string.library_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(recording.id)
                        deleting = null
                    },
                ) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }
}

@Composable
private fun RecordingCard(
    recording: Recording,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onTogglePlayback) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.library_pause else R.string.library_play,
                    ),
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(formatDuration(recording.durationMs))
                        append(" · ")
                        append(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(recording.createdAt)),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recording.status == RecordingStatus.QUEUED) {
                    Text(
                        text = stringResource(R.string.library_status_queued),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.library_rename),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.library_delete),
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.library_rename_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) {
                Text(stringResource(R.string.library_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) }
        },
    )
}

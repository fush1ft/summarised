package io.github.fush1ft.summarised.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.fush1ft.summarised.R
import io.github.fush1ft.summarised.core.designsystem.component.EmptyState
import io.github.fush1ft.summarised.core.designsystem.theme.SummarisedTheme

/**
 * Stand-in for screens whose feature module has not landed yet. Each is replaced
 * wholesale by its own module in the milestone that implements it.
 */
@Composable
fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LibraryPlaceholderScreen() {
    EmptyState(
        icon = Icons.Outlined.LibraryMusic,
        title = stringResource(R.string.library_empty_title),
        description = stringResource(R.string.library_empty_description),
    )
}

@Preview(showBackground = true)
@Composable
private fun LibraryPlaceholderPreview() {
    SummarisedTheme {
        LibraryPlaceholderScreen()
    }
}

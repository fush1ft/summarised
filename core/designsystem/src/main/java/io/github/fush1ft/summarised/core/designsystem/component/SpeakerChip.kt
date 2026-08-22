package io.github.fush1ft.summarised.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.fush1ft.summarised.core.designsystem.theme.LocalSpeakerColors

/**
 * A speaker's name, colored by their index. Colors wrap if a recording somehow has
 * more speakers than the palette, which is better than crashing on an eight-person
 * meeting.
 */
@Composable
fun SpeakerChip(
    name: String,
    colorIndex: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSpeakerColors.current
    val (container, onContainer) = palette[colorIndex.mod(palette.size)]

    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = onContainer,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "Speaker: $name" },
    )
}

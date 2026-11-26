@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Tone for chips, hints and icons. */
enum class Tone { NEUTRAL, ACTIVE, DONE, WARN, ERROR }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        Tone.NEUTRAL -> c.surfaceContainerHighest to c.onSurfaceVariant
        Tone.ACTIVE -> c.primaryContainer to c.onPrimaryContainer
        Tone.DONE -> c.tertiaryContainer to c.onTertiaryContainer
        Tone.WARN -> c.secondaryContainer to c.onSecondaryContainer
        Tone.ERROR -> c.errorContainer to c.onErrorContainer
    }
}

/** Page header with optional back arrow. */
@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Big rounded icon badge. */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone = Tone.ACTIVE, size: Int = 48) {
    val (bg, fg) = toneColors(tone)
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size((size * 0.52f).dp))
    }
}

@Composable
fun Pill(text: String, tone: Tone) {
    val (bg, fg) = toneColors(tone)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

/** A titled card; [step] shows a numbered circle (1, 2, 3 …) for guided flows. */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    step: Int? = null,
    done: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step != null) {
                    val (bg, fg) = toneColors(if (done) Tone.DONE else Tone.ACTIVE)
                    Box(Modifier.size(32.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                        if (done) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = fg, modifier = Modifier.size(20.dp))
                        } else {
                            Text("$step", color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}

/** Contextual explanation or warning. */
@Composable
fun Hint(text: String, icon: ImageVector, tone: Tone = Tone.NEUTRAL) {
    val (bg, fg) = toneColors(tone)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Large, full-width primary action. */
@Composable
fun BigButton(text: String, icon: ImageVector?, enabled: Boolean = true, tonal: Boolean = false, onClick: () -> Unit) {
    val mod = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
    if (tonal) {
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = mod, shape = RoundedCornerShape(20.dp)) { content() }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = mod, shape = RoundedCornerShape(20.dp)) { content() }
    }
}

/** One line of a setup checklist with a live state and an optional fix-it action. */
@Composable
fun CheckRow(done: Boolean, title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (done) "hotovo" else "zbývá",
            tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null && !done) {
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Collapsed-by-default section for technical controls. */
@Composable
fun Collapsible(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open = !open }.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

/** Labelled horizontal bar, animated as values arrive. */
@Composable
fun MetricBar(label: String, value: String, fraction: Float, highlight: Boolean = false) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "bar")
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(c.surfaceContainerHighest)) {
            Box(
                Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(50))
                    .background(if (highlight) c.tertiary else c.primary),
            )
        }
    }
}

/** 1–5 stars. */
@Composable
fun StarRating(label: String, value: Int, onRate: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row {
            (1..5).forEach { i ->
                IconButton(onClick = { onRate(i) }) {
                    Icon(
                        if (i <= value) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "$i z 5",
                        tint = if (i <= value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

/** Breathing red dot shown while recording. */
@Composable
fun PulsingDot(size: Int = 18) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(0.75f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Box(Modifier.size(size.dp).scale(s).clip(CircleShape).background(MaterialTheme.colorScheme.error))
}

/** Live microphone level as a row of bars. */
@Composable
fun LevelMeter(level: Float) {
    val l by animateFloatAsState(level, tween(90), label = "level")
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        val bars = 24
        repeat(bars) { i ->
            val center = 1f - kotlin.math.abs(i - bars / 2f) / (bars / 2f)
            val h = (0.15f + l * (0.35f + 0.65f * center)).coerceIn(0.1f, 1f)
            Box(
                Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(50))
                    .background(if (l > 0.05f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** A horizontally scrolling single-choice row of chips. */
@Composable
fun <T> Choice(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
fun SmallLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, textAlign = TextAlign.Center) }
}

@Composable
fun ButtonPair(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

fun formatMb(mb: Int): String = if (mb >= 1000) "%.1f GB".format(mb / 1000.0).replace('.', ',') else "$mb MB"

package com.jacdrive.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.Freshness
import com.jacdrive.ui.R
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout

val CardShape = RoundedCornerShape(22.dp)

/**
 * Glass panel: translucent fill over the navy background, top-lit gradient border and an optional
 * coloured glow ([highlight]) for warning/active states. Base surface of every screen.
 */
@Composable
fun JacCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    highlight: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.2f
    val edge = highlight ?: if (dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val border = Brush.verticalGradient(
        listOf(
            edge.copy(alpha = if (highlight != null) 0.85f else if (dark) 0.38f else 0.12f),
            edge.copy(alpha = if (highlight != null) 0.30f else 0.06f),
        ),
    )
    val fill = if (dark) {
        Brush.verticalGradient(
            listOf(
                (highlight ?: Color(0xFF6FA8FF)).copy(alpha = if (highlight != null) 0.16f else 0.09f),
                Color(0xFF0A1428).copy(alpha = 0.72f),
            ),
        )
    } else {
        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainer))
    }
    Column(
        modifier
            .clip(CardShape)
            .background(fill)
            .border(BorderStroke(if (highlight != null) 1.5.dp else 1.dp, border), CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) { content() }
    }
}

/** Navy cockpit backdrop with a soft blue (or sport red) glow, used behind every screen in dark mode. */
fun Modifier.appBackground(dark: Boolean, glow: Color): Modifier =
    if (!dark) this else this.drawBehind {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1730), Color(0xFF040812), Color(0xFF02040A))))
        drawRect(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.22f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.12f),
                radius = size.maxDimension * 0.6f,
            ),
        )
    }

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        else Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
fun FreshnessDot(freshness: Freshness, modifier: Modifier = Modifier) {
    val s = JacTheme.status
    val color = when (freshness) {
        Freshness.LIVE -> s.ok
        Freshness.STALE -> s.warning
        Freshness.MISSING -> MaterialTheme.colorScheme.outline
    }
    Box(modifier.size(7.dp).clip(CircleShape).background(color))
}

/**
 * Persistent banner that makes the data provenance impossible to miss. Shown on every screen that
 * displays live values whenever data is simulated or there is no adapter.
 */
@Composable
fun DataModeBanner(mode: DataMode, modifier: Modifier = Modifier, onAction: (() -> Unit)? = null) {
    if (mode == DataMode.LIVE) return
    val s = JacTheme.status
    val (color, icon, title, body) = when (mode) {
        DataMode.DEMO -> Quad(s.demo, Icons.Rounded.Science, stringResource(R.string.demo_banner_title), stringResource(R.string.demo_banner_body))
        else -> Quad(MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.CloudOff, stringResource(R.string.offline_banner_title), stringResource(R.string.offline_banner_body))
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        onClick = onAction ?: {},
        enabled = onAction != null,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = color)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Label / large value / unit block used across non-cockpit screens. */
@Composable
fun MetricReadout(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    freshness: Freshness? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (freshness != null) {
                FreshnessDot(freshness)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
        ) {
          Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Readout.Medium, color = valueColor)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = Readout.Unit, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
          }
        }
    }
}

/** Top bar used by every secondary screen: large bold title, optional back and actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/** Small capability note used where standard OBD-II has hard limits the user should know about. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, color: Color = JacTheme.status.info, icon: ImageVector? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon ?: Icons.Rounded.Info, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Marks a section whose engine ships in a later development phase, so nothing looks functional when it is not. */
@Composable
fun InDevelopmentChip(modifier: Modifier = Modifier) {
    StatusPill(
        text = stringResource(R.string.in_development),
        color = JacTheme.status.warning,
        modifier = modifier,
    )
}

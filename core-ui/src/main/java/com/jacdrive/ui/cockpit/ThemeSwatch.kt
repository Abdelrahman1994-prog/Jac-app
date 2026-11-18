package com.jacdrive.ui.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jacdrive.ui.components.labelRes

/** Selectable miniature of a cockpit theme. */
@Composable
fun ThemeSwatch(style: CockpitStyle, selected: Boolean, modifier: Modifier = Modifier, width: Dp = 112.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.width(width).clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) style.accent else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).background(Brush.verticalGradient(listOf(style.backgroundTop, style.backgroundBottom))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .border(4.dp, Brush.sweepGradient(listOf(style.accentSecondary, style.accent, style.accentSecondary)), CircleShape),
            )
        }
        Text(
            stringResource(style.id.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            maxLines = 1,
        )
    }
}

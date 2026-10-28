package com.jjteo.phototodocs.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jjteo.phototodocs.R

@Composable
internal fun AppIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, description: String? = null, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), contentDescription = description, modifier = modifier.size(22.dp), tint = tint)
}

@Composable
internal fun ActionLabel(@DrawableRes icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(icon)
        Text(label)
    }
}

/** Keep long-form content readable, including on tablets and in landscape. */
@Composable
internal fun ContentFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 760.dp).fillMaxWidth(), content = content)
    }
}

@Composable
internal fun PreviewAiAction(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalWorkspaceAccent.current
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 74.dp).background(Brush.linearGradient(listOf(accent.start, accent.end)), MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White, disabledContainerColor = Color.Transparent)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_ai)
            Column(Modifier.weight(1f)) {
                Text("AI edit", style = MaterialTheme.typography.titleMedium)
                Text("Suggestions you review first.", style = MaterialTheme.typography.bodySmall)
            }
            AppIcon(R.drawable.ic_chevron_right)
        }
    }
}

package com.jjteo.phototodocs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jjteo.phototodocs.data.ThemeMode

@Composable
internal fun AppearanceCard(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("Choose how the app looks.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
                    val active = selected == mode
                    Surface(color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(MaterialTheme.shapes.small)
                                .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(mode) })
                                .testTag("theme-${mode.preferenceValue}").padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = active, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(when (mode) { ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.SYSTEM -> "System" }, style = MaterialTheme.typography.titleMedium)
                                Text(when (mode) {
                                    ThemeMode.LIGHT -> "Ocean, blue to teal"
                                    ThemeMode.DARK -> "Graphite, slate and teal"
                                    ThemeMode.SYSTEM -> "Match your phone's appearance"
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.jjteo.phototodocs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jjteo.phototodocs.AppViewModel
import com.jjteo.phototodocs.ai.AiProvider
import com.jjteo.phototodocs.chatgpt.ChatGptUiState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ChatGptSettings(vm: AppViewModel, provider: AiProvider, state: ChatGptUiState) {
    var accountMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI provider", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.selectableGroup()) {
                    AiProvider.entries.forEach { item ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .selectable(selected = provider == item, enabled = !state.working, role = Role.RadioButton, onClick = { vm.setAiProvider(item) })
                            .testTag("provider-${item.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = provider == item, onClick = null, enabled = !state.working)
                            Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                                Text(item.label, style = MaterialTheme.typography.titleSmall)
                                Text(if (item == AiProvider.LOCAL) "Gemma on this phone · works offline" else "Your ChatGPT plan · internet required",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(if (provider == AiProvider.CHATGPT) "ChatGPT sends submitted photos and document text to OpenAI. Basic text recognition, manual edits, previews, and exports stay on this phone."
                    else "Local AI keeps photos and edits on your phone. Connecting ChatGPT does not change your selected provider.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("ChatGPT connection", style = MaterialTheme.typography.titleMedium)
                Text("Use an eligible Plus or Pro account. Signing in does not confirm model access; Test connection checks it with a small request.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.accounts.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { accountMenu = true }, enabled = !state.working,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chatgpt-account")) {
                            Text(state.accounts.find { it.id == state.selectedId }?.label ?: "Choose an account")
                        }
                        DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                            state.accounts.forEach { account ->
                                DropdownMenuItem(text = { Text(account.label + if (account.connected) "" else " · signed out") },
                                    onClick = { accountMenu = false; vm.selectChatGptAccount(account.id) })
                            }
                        }
                    }
                }
                Text(state.status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("chatgpt-status"))
                if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.signingIn) {
                    OutlinedButton(onClick = vm::cancelChatGpt, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel sign-in") }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { vm.signInChatGpt() }, enabled = !state.working, modifier = Modifier.heightIn(min = 48.dp).testTag("chatgpt-sign-in")) {
                            Text(if (!state.connected) "Continue with ChatGPT" else if (!state.planEnabled) "Enable ChatGPT plan use" else "Reconnect")
                        }
                        if (state.accounts.isNotEmpty()) TextButton(onClick = { vm.signInChatGpt(addAccount = true) }, enabled = !state.working,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Add account") }
                    }
                }
                if (state.connected && state.planEnabled) {
                    Box {
                        OutlinedButton(onClick = { modelMenu = true }, enabled = !state.working && state.models.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chatgpt-model")) {
                            Text(state.models.find { it.slug == state.model }?.name ?: state.model ?: "Choose a model")
                        }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            state.models.forEach { model -> DropdownMenuItem(text = { Text(model.name) },
                                onClick = { modelMenu = false; vm.selectChatGptModel(model.slug) }) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = vm::testChatGpt, enabled = !state.working && state.ready,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("chatgpt-test")) { Text("Test connection") }
                        TextButton(onClick = vm::loadChatGptModels, enabled = !state.working, modifier = Modifier.heightIn(min = 48.dp)) { Text("Load models") }
                    }
                    Text("The test uses a small amount of your ChatGPT plan allowance.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.verifiedAt?.let { time ->
                        Text("Verified with ${state.models.find { it.slug == state.model }?.name ?: state.model} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("chatgpt-verified"))
                    }
                }
                if (state.accounts.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::manageChatGptUsage, modifier = Modifier.heightIn(min = 48.dp)) { Text("Manage usage") }
                    if (state.connected) TextButton(onClick = { disconnect = true }, enabled = !state.working, modifier = Modifier.heightIn(min = 48.dp)) { Text("Disconnect") }
                }
            }
        }
    }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect ChatGPT?") },
        text = { Text("Sign out this connection on your phone and request session revocation. Your notes and downloaded local model stay saved.") },
        confirmButton = { TextButton(onClick = { disconnect = false; vm.disconnectChatGpt() }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}

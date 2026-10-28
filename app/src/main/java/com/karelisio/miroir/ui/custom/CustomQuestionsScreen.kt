package com.karelisio.miroir.ui.custom

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.data.model.QuestionType
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.ShapeAvatar
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import kotlinx.coroutines.launch

private val typeLabels = listOf(
    QuestionType.TEXT to "Texte",
    QuestionType.CHOICE to "Choix",
    QuestionType.SCALE to "1 à 10",
    QuestionType.WHO to "Qui de nous",
)

private fun QuestionType.label() = typeLabels.first { it.first == this }.second

@Composable
fun CustomQuestionsScreen(vm: CustomQuestionsViewModel, contentPadding: PaddingValues, onMessage: (String) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                Column(Modifier.staggeredAppear(0, "c-title")) {
                    Text("Vos questions", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ajoutez vos propres questions : elles sont chiffrées, partagées avec l'autre et glissées dans le tirage quotidien.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (ui.items.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            MorphingBlob(Modifier.size(140.dp).graphicsLayer { alpha = 0.3f }, MaterialTheme.colorScheme.tertiary)
                            Text("✍️", style = MaterialTheme.typography.displaySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Aucune question perso pour l'instant.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }
            itemsIndexed(ui.items, key = { _, it -> it.id }) { index, item ->
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().animateItem().staggeredAppear(index + 1, item.id),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShapeAvatar(
                            item.authorName,
                            if (item.mine) MaterialShapes.Cookie9Sided else MaterialShapes.Flower,
                            if (item.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            if (item.mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.text, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${item.type.label()} · ${if (item.used) "déjà tirée" else "en attente de tirage"}${if (!item.synced) " · envoi en attente" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (item.mine) {
                            IconButton(onClick = { haptics.press(); vm.delete(item.id) }) { Icon(Icons.Rounded.DeleteOutline, "Supprimer") }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { haptics.press(); editing = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Nouvelle question") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp, end = 20.dp),
        )
    }

    if (editing) {
        QuestionEditorSheet(
            onDismiss = { editing = false },
            onSave = { text, type, options, min, max ->
                vm.add(text, type, options, min, max)
                editing = false
                onMessage("Question ajoutée au tirage ✍️")
            },
        )
    }
}

@Composable
private fun QuestionEditorSheet(
    onDismiss: () -> Unit,
    onSave: (String, QuestionType, List<String>, String?, String?) -> Unit,
) {
    val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var text by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(QuestionType.TEXT) }
    val options = remember { mutableStateListOf("", "") }
    var minLabel by rememberSaveable { mutableStateOf("") }
    var maxLabel by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Nouvelle question", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 160) text = it },
                label = { Text("Votre question") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                typeLabels.forEachIndexed { i, (t, label) ->
                    ToggleButton(
                        checked = type == t,
                        onCheckedChange = { haptics.tick(); type = t },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            typeLabels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            AnimatedContent(type, transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) }, label = "type") { t ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (t) {
                        QuestionType.CHOICE -> {
                            options.forEachIndexed { i, value ->
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { if (it.length <= 48) options[i] = it },
                                    label = { Text("Option ${i + 1}") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                )
                            }
                            AnimatedVisibility(options.size < 4) {
                                Button(onClick = { options.add("") }, shapes = ButtonDefaults.shapes()) { Text("Ajouter une option") }
                            }
                        }
                        QuestionType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(minLabel, { if (it.length <= 28) minLabel = it }, label = { Text("1 =") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large)
                            OutlinedTextField(maxLabel, { if (it.length <= 28) maxLabel = it }, label = { Text("10 =") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large)
                        }
                        QuestionType.WHO -> Text("Chacun répondra « moi » ou « toi ».", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        QuestionType.TEXT -> Text("Chacun répondra librement.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val filled = options.filter { it.isNotBlank() }
            val valid = text.trim().length >= 8 && (type != QuestionType.CHOICE || filled.size >= 2)
            Button(
                onClick = {
                    haptics.confirm()
                    scope.launch { sheet.hide() }.invokeOnCompletion {
                        onSave(text.trim(), type, filled.map { it.trim() }, minLabel.trim().ifBlank { null }, maxLabel.trim().ifBlank { null })
                    }
                },
                enabled = valid,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Ajouter au tirage") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

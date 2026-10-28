package com.karelisio.miroir.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.notifications.DailyReminder
import com.karelisio.miroir.permissions.AppPermission
import com.karelisio.miroir.ui.components.ShapeAvatar
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

private fun String?.pretty(): String? = this?.let { runCatching { LocalDate.parse(it).format(longDate) }.getOrNull() }

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    contentPadding: PaddingValues,
    onOpenCategories: () -> Unit,
    onOpenPermissions: () -> Unit,
    onShowInvite: () -> Unit,
    onUnpaired: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val p by vm.prefs.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsState()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val permissions = rememberPermissionStates()
    val request = rememberPermissionRequester()

    var editProfile by rememberSaveable { mutableStateOf(false) }
    var pickBirthday by rememberSaveable { mutableStateOf(false) }
    var pickAnniversary by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }

    val errorColor = MaterialTheme.colorScheme.error
    val enabledCategories = Category.drawable.count { it.key !in p.shared.disabledCategories }
    val missingPermissions = permissions.count { (perm, granted) -> !granted && !perm.optional }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "title") {
            Text("Réglages", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 4.dp).staggeredAppear(0, "s-title"))
        }
        item(key = "profile") { ProfileHeader(p, Modifier.staggeredAppear(1, "s-profile")) { editProfile = true } }

        item(key = "couple") {
            Group("Nous deux", 2) {
                link(Icons.Rounded.Cake, "Mon anniversaire", p.myBirthday.pretty() ?: "Pour un pack spécial ce jour-là") { pickBirthday = true }
                link(Icons.Rounded.Favorite, "Notre anniversaire", p.shared.anniversary.pretty() ?: "La date de votre rencontre") { pickAnniversary = true }
                if (p.member.key == "A" && !p.partnerJoined) link(Icons.Rounded.QrCode2, "Afficher le QR code", "Pour relier l'autre téléphone", onClick = onShowInvite)
            }
        }
        item(key = "questions") {
            Group("Questions", 3) {
                link(Icons.Rounded.Category, "Catégories", "$enabledCategories sur ${Category.drawable.size} actives", onClick = onOpenCategories)
                toggle(Icons.Rounded.Celebration, "Packs saisonniers", "Noël, Saint-Valentin, été, anniversaires…", p.shared.packsEnabled) {
                    haptics.toggle(it); vm.setPacks(it)
                }
            }
        }
        item(key = "notifications") {
            Group("Notifications", 4) {
                toggle(Icons.Rounded.Schedule, "Rappel quotidien", if (p.reminderEnabled) "Chaque jour à %02d:%02d".format(p.reminderHour, p.reminderMinute) else "Désactivé", p.reminderEnabled, onClick = { pickTime = true }) {
                    haptics.toggle(it); vm.setReminderEnabled(it)
                }
                toggle(Icons.Rounded.NotificationsActive, "Quand ${p.partnerDisplayName} répond", "Réponses, réactions, révélations", p.notifyPartner) {
                    haptics.toggle(it); vm.setNotifyPartner(it)
                }
                toggle(Icons.Rounded.Fullscreen, "Révélation en plein écran", "Sur l'écran verrouillé", p.fullScreenReveal) {
                    haptics.toggle(it); vm.setFullScreen(it)
                    if (it && permissions[AppPermission.FULL_SCREEN] == false) request(AppPermission.FULL_SCREEN)
                }
                toggle(Icons.Rounded.DoNotDisturbOn, "Traverser Ne pas déranger", "Pour la question du jour", p.bypassDnd) {
                    haptics.toggle(it); vm.setBypassDnd(it)
                    if (it && permissions[AppPermission.DO_NOT_DISTURB] == false) request(AppPermission.DO_NOT_DISTURB)
                }
                if (p.reminderEnabled && !DailyReminder.canScheduleExact(context)) {
                    link(Icons.Rounded.Schedule, "Rappel à l'heure pile", "Autoriser les alarmes exactes") { request(AppPermission.EXACT_ALARMS) }
                }
            }
        }
        item(key = "app") {
            Group("Application", 5) {
                link(Icons.Rounded.Shield, "Autorisations", if (missingPermissions == 0) "Tout est accordé ✓" else "$missingPermissions à revoir", onClick = onOpenPermissions)
                toggle(Icons.Rounded.Vibration, "Retours haptiques", "Vibrations subtiles pendant les animations", p.haptics) {
                    haptics.toggle(it); vm.setHaptics(it)
                }
                link(
                    Icons.Rounded.SystemUpdate,
                    "Rechercher une mise à jour",
                    "Version ${vm.version}" + (p.ignoredVersion?.let { " · $it ignorée" } ?: ""),
                    trailing = { if (checking) LoadingIndicator(Modifier.size(28.dp)) },
                ) {
                    haptics.press()
                    vm.checkUpdates { msg -> msg?.let(onMessage) }
                }
                if (p.ignoredVersion != null) link(Icons.Rounded.SystemUpdate, "Ne plus ignorer la version ${p.ignoredVersion}", null) { vm.resetIgnoredVersion() }
            }
        }
        item(key = "privacy") {
            Group("Confidentialité", 6) {
                link(Icons.Rounded.Lock, "Chiffrement de bout en bout", "Réponses et questions perso sont chiffrées (AES-256-GCM) sur vos téléphones. Le serveur ne voit que du chiffré.", onClick = null)
                link(Icons.Rounded.LinkOff, "Dissocier nos miroirs", "Efface le couple sur le serveur et sur ce téléphone", tint = errorColor) { confirmUnpair = true }
            }
        }
    }

    if (editProfile) ProfileDialog(p, onDismiss = { editProfile = false }) { name -> vm.updateProfile(name, p.myBirthday); editProfile = false }
    if (pickBirthday) DateDialog(p.myBirthday, onDismiss = { pickBirthday = false }) { vm.updateProfile(p.myName, it); pickBirthday = false }
    if (pickAnniversary) DateDialog(p.shared.anniversary, onDismiss = { pickAnniversary = false }) { vm.setAnniversary(it); pickAnniversary = false }
    if (pickTime) TimeDialog(p.reminderHour, p.reminderMinute, onDismiss = { pickTime = false }) { h, m ->
        vm.setReminderTime(h, m); vm.setReminderEnabled(true); pickTime = false
    }
    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            icon = { Icon(Icons.Rounded.LinkOff, null) },
            title = { Text("Dissocier vos miroirs ?") },
            text = { Text("L'historique, les réponses et les questions perso seront effacés du serveur et de ce téléphone. Cette action est définitive.") },
            confirmButton = {
                Button(
                    onClick = { confirmUnpair = false; vm.unpair(onUnpaired, onMessage) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Dissocier") }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ProfileHeader(p: AppPrefs, modifier: Modifier, onEdit: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, onClick = onEdit, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeAvatar(p.myName.ifBlank { "?" }, MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.myName.ifBlank { "Ton prénom" }, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (p.partnerJoined) "Miroir partagé avec ${p.partnerDisplayName} 💜" else "En attente de l'autre téléphone",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ShapeAvatar(p.partnerDisplayName, MaterialShapes.Flower, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, 40.dp)
        }
    }
}

private typealias Shape = androidx.compose.ui.graphics.Shape

/** Groupe "segmenté" façon Android 16 : coins larges aux extrémités, petits entre les éléments. */
@Composable
private fun Group(title: String, index: Int, content: GroupBuilder.() -> Unit) {
    val builder = GroupBuilder().apply(content)
    Column(Modifier.staggeredAppear(index, "group-$title"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        val n = builder.rows.size
        builder.rows.forEachIndexed { i, row ->
            val big = 24.dp
            val small = 6.dp
            val shape = RoundedCornerShape(
                topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
                bottomStart = if (i == n - 1) big else small, bottomEnd = if (i == n - 1) big else small,
            )
            row(shape)
        }
    }
}

private class GroupBuilder {
    val rows = mutableListOf<@Composable (Shape) -> Unit>()

    fun link(
        icon: ImageVector,
        title: String,
        subtitle: String?,
        tint: Color? = null,
        trailing: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)?,
    ) {
        rows += { shape -> SettingRow(shape, icon, title, subtitle, tint, onClick, trailing ?: { if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }) }
    }

    fun toggle(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onClick: (() -> Unit)? = null, onToggle: (Boolean) -> Unit) {
        rows += { shape ->
            SettingRow(shape, icon, title, subtitle, null, onClick ?: { onToggle(!checked) }) {
                Switch(checked = checked, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun SettingRow(
    shape: Shape,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tint: Color?,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        ListItem(
            supportingContent = subtitle?.let { { Text(it) } },
            leadingContent = { Icon(icon, null, tint = tint ?: colors.primary) },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) { Text(title, color = tint ?: Color.Unspecified) }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Surface(shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun ProfileDialog(p: AppPrefs, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(p.myName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ton prénom") },
        text = {
            OutlinedTextField(name, { if (it.length <= 30) name = it }, singleLine = true, shape = MaterialTheme.shapes.large, label = { Text("Prénom") })
        },
        confirmButton = { Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun DateDialog(initial: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val initialMillis = initial?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() })
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onPick(null) }) { Text("Effacer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    ) { DatePicker(state) }
}

@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Heure du rappel") },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { TimePicker(state) } },
        confirmButton = { Button(onClick = { onPick(state.hour, state.minute) }, shapes = ButtonDefaults.shapes()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

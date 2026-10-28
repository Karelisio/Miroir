package com.karelisio.miroir.ui.settings

import com.karelisio.miroir.ui.components.miroirTopBar
import com.karelisio.miroir.ui.components.miroirSafe
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.permissions.AppPermission
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear

/** Écran "Autorisations" : l'état de chacune, et un bouton pour la (ré)activer. */
@Composable
fun PermissionsScreen(@Suppress("UNUSED_PARAMETER") container: AppContainer, onBack: () -> Unit) {
    val states = rememberPermissionStates()
    val request = rememberPermissionRequester()
    val haptics = rememberHaptics()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val granted = states.count { it.value }

    Scaffold(
        contentWindowInsets = WindowInsets.miroirSafe,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                windowInsets = WindowInsets.miroirTopBar,
                title = { Text("Autorisations") },
                subtitle = { Text("$granted sur ${states.size} accordées") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(AppPermission.all, key = { _, p -> p.name }) { i, perm ->
                val ok = states[perm] == true
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().staggeredAppear(i, "perm-${perm.name}"),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialShapes.Cookie6Sided.toShape(), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                            Icon(perm.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(perm.title + if (perm.optional) " (optionnel)" else "", style = MaterialTheme.typography.titleMedium)
                            Text(perm.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(10.dp))
                        AnimatedContent(ok, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "perm") { isOk ->
                            if (isOk) {
                                AnimatedGrantedCheck(true, 36.dp)
                            } else {
                                FilledTonalButton(onClick = { haptics.press(); request(perm) }, shapes = ButtonDefaults.shapes()) { Text("Activer") }
                            }
                        }
                    }
                }
            }
        }
    }
}

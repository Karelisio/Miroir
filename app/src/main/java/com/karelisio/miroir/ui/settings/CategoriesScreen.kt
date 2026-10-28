package com.karelisio.miroir.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val p by vm.prefs.collectAsStateWithLifecycle()
    val counts by produceState(emptyMap<String, Int>()) {
        value = container.bank.all().filter { it.pack == null }.groupingBy { it.category }.eachCount()
    }
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Catégories") },
                subtitle = { Text("${counts.values.sum()} questions dans la banque") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(Category.drawable, key = { _, c -> c.key }) { i, c ->
                val enabled = c.key !in p.shared.disabledCategories
                val n = Category.drawable.size
                val big = 24.dp
                val small = 6.dp
                Surface(
                    onClick = {
                        if (vm.toggleCategory(c.key)) haptics.toggle(!enabled)
                        else { haptics.reject(); scope.launch { snackbar.showSnackbar("Garde au moins une catégorie active.") } }
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
                        bottomStart = if (i == n - 1) big else small, bottomEnd = if (i == n - 1) big else small,
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().staggeredAppear(i, "cat-${c.key}", baseDelayMs = 30),
                ) {
                    ListItem(
                        leadingContent = { Text(c.emoji, style = MaterialTheme.typography.headlineSmall) },
                        supportingContent = { Text("${counts[c.key] ?: 0} questions") },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = {
                                if (vm.toggleCategory(c.key)) haptics.toggle(it) else haptics.reject()
                            })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) { Text(c.label) }
                }
            }
        }
    }
}

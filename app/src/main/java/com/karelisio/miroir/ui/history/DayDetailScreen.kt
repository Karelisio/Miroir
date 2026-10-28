package com.karelisio.miroir.ui.history

import com.karelisio.miroir.ui.components.miroirTopBar
import com.karelisio.miroir.ui.components.miroirSafe
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Pack
import com.karelisio.miroir.ui.components.staggeredAppear
import com.karelisio.miroir.ui.navigation.sharedContainer
import com.karelisio.miroir.ui.navigation.sharedItem
import com.karelisio.miroir.ui.today.CategoryChip
import com.karelisio.miroir.ui.today.MirrorReveal
import com.karelisio.miroir.ui.today.ReactionsSection
import com.karelisio.miroir.ui.today.display
import com.karelisio.miroir.ui.today.myAnswerPayload
import com.karelisio.miroir.ui.today.myReactionPayload
import com.karelisio.miroir.ui.today.partnerAnswerPayload
import com.karelisio.miroir.ui.today.partnerReactionPayload
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

@Composable
fun DayDetailScreen(vm: HistoryViewModel, day: String, onBack: () -> Unit) {
    val flow = remember(day) { vm.day(day) }
    val ui by flow.collectAsState(initial = null)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets.miroirSafe,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                windowInsets = WindowInsets.miroirTopBar,
                title = { Text(LocalDate.parse(day).format(detailDate).replaceFirstChar { it.titlecase(Locale.FRENCH) }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val d = ui
        if (d == null) {
            LoadingIndicator(Modifier.padding(padding).padding(32.dp))
            return@Scaffold
        }
        val e = d.entity
        val p = d.prefs
        val mine = e.myAnswerPayload()
        val theirs = e.partnerAnswerPayload()
        val nameOf: (String) -> String = { if (it == p.member.key) p.myName.ifBlank { "Toi" } else p.partnerDisplayName }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().sharedContainer("day-$day"),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val category = Category.of(e.category)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryChip("${category.emoji} ${category.label}")
                        Pack.of(e.pack)?.let { CategoryChip("${it.emoji} ${it.label}", highlight = true) }
                    }
                    Text(e.questionText, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.sharedItem("q-$day"))
                }
            }
            when {
                mine != null && theirs != null -> {
                    MirrorReveal(d.question, mine, theirs, p.member, p.myName, p.partnerDisplayName, play = false, onPlayed = {}, modifier = Modifier.staggeredAppear(1, "d-reveal"))
                    ReactionsSection(e.myReactionPayload(), e.partnerReactionPayload(), p.partnerDisplayName) { emoji, comment ->
                        vm.react(day, emoji, comment)
                    }
                }
                mine != null -> Text(
                    "Ta réponse : ${mine.display(d.question, nameOf)}\n\n${p.partnerDisplayName} n'a pas répondu ce jour-là.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                e.partnerAnswered -> Text("${p.partnerDisplayName} a répondu, mais pas toi : sa réponse reste secrète.", style = MaterialTheme.typography.bodyLarge)
                else -> Text("Personne n'a répondu ce jour-là.", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

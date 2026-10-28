package com.karelisio.miroir.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.width
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.QuestionType
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.miroir.MainActivity
import com.karelisio.miroir.container
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Pack
import com.karelisio.miroir.ui.theme.MiroirDarkScheme
import com.karelisio.miroir.ui.theme.MiroirLightScheme
import com.karelisio.miroir.ui.today.streakOf

enum class WidgetStatus(val label: String) {
    NOT_PAIRED("Relie vos deux miroirs"),
    WAITING_PAIR("En attente de l'appairage"),
    YOUR_TURN("À toi !"),
    YOUR_TURN_PARTNER_DONE("À toi ! L'autre a répondu ✨"),
    WAITING_PARTNER("En attente de sa réponse"),
    REVEALED("Révélé 💜"),
}

/** Réponse rapide depuis le widget : (valeur, libellé) pour les « qui de nous deux » et les petits choix. */
data class QuickAnswer(val type: QuestionType, val options: List<Pair<String, String>>)

data class WidgetState(
    val question: String,
    val badge: String,
    val status: WidgetStatus,
    val streak: Int,
    val day: String? = null,
    val questionId: String? = null,
    val quick: QuickAnswer? = null,
) {
    val yourTurn: Boolean get() = status == WidgetStatus.YOUR_TURN || status == WidgetStatus.YOUR_TURN_PARTNER_DONE
}

/** État du jour, partagé par le widget et la tuile des réglages rapides. */
suspend fun loadWidgetState(context: Context): WidgetState {
    val c = context.applicationContext.container
    val prefs = c.prefs.current()
    if (!prefs.paired) return WidgetState("Une question par jour, pour vous deux.", "Miroir", WidgetStatus.NOT_PAIRED, 0)
    if (!prefs.partnerJoined) return WidgetState("Fais scanner ton QR code par l'autre téléphone.", "Miroir", WidgetStatus.WAITING_PAIR, 0)
    val today = c.repository.ensureToday()
    val days = c.repository.dao.days()
    val streak = streakOf(days, c.repository.today())
    if (today == null) return WidgetState("Ouvre Miroir pour découvrir la question du jour.", "Miroir", WidgetStatus.YOUR_TURN, streak)
    val badge = Pack.of(today.pack)?.let { "${it.emoji} ${it.label}" } ?: Category.of(today.category).let { "${it.emoji} ${it.label}" }
    val status = when {
        today.myAnswer == null && today.partnerAnswered -> WidgetStatus.YOUR_TURN_PARTNER_DONE
        today.myAnswer == null -> WidgetStatus.YOUR_TURN
        !today.partnerAnswered -> WidgetStatus.WAITING_PARTNER
        else -> WidgetStatus.REVEALED
    }
    val quick = if (today.myAnswer == null && today.questionText.isNotBlank()) {
        c.repository.resolveQuestion(today.questionId)?.let { q ->
            when {
                q.type == QuestionType.WHO -> QuickAnswer(q.type, listOf(prefs.member.key to prefs.myName.ifBlank { "Moi" }, prefs.member.other.key to prefs.partnerDisplayName))
                q.type == QuestionType.CHOICE && q.options.size in 2..4 -> QuickAnswer(q.type, q.options.mapIndexed { i, o -> "$i" to o })
                else -> null
            }
        }
    } else null
    return WidgetState(today.questionText, badge, status, streak, today.day, today.questionId, quick)
}

class MiroirWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadWidgetState(context)
        provideContent {
            // Réponse touchée (en attente d'envoi), valable seulement pour la question affichée.
            val selected = currentState(SELECTED)?.split('|')?.takeIf { it.size == 3 && it[0] == state.day && it[1] == state.questionId }?.get(2)
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= 31) GlanceTheme.colors else fallbackColors) {
                Content(state, selected)
            }
        }
    }

    @Composable
    private fun Content(state: WidgetState, selected: String?) {
        val size = LocalSize.current
        val compact = size.width < WIDE.width
        val tall = size.height >= LARGE.height
        val quick = state.quick?.takeIf { state.yourTurn && !compact && size.height >= MEDIUM.height && (tall || it.options.size <= 2) }
        val colors = GlanceTheme.colors
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (compact) "Miroir" else state.badge,
                    style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (state.streak > 0) {
                    Text("🔥 ${state.streak}", style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = state.question,
                style = TextStyle(color = colors.onSurface, fontSize = if (compact) 14.sp else 17.sp, fontWeight = FontWeight.Medium),
                maxLines = if (quick != null) 2 else if (tall) 6 else if (compact) 3 else 2,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.height(8.dp))
            if (quick != null) {
                QuickAnswers(state, quick, selected)
                return@Column
            }
            val (bg, fg) = when (state.status) {
                WidgetStatus.REVEALED -> colors.tertiaryContainer to colors.onTertiaryContainer
                WidgetStatus.YOUR_TURN, WidgetStatus.YOUR_TURN_PARTNER_DONE -> colors.primary to colors.onPrimary
                else -> colors.secondaryContainer to colors.onSecondaryContainer
            }
            Box(
                modifier = GlanceModifier.background(bg).cornerRadius(50.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.status.label, style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }

    /** Boutons de réponse (deux par ligne) puis « Envoyer » une fois un choix touché. */
    @Composable
    private fun QuickAnswers(state: WidgetState, quick: QuickAnswer, selected: String?) {
        val colors = GlanceTheme.colors
        quick.options.chunked(2).forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                row.forEachIndexed { i, (value, label) ->
                    val on = value == selected
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .background(if (on) colors.primaryContainer else colors.secondaryContainer)
                            .cornerRadius(16.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .clickable(actionRunCallback<PickAnswerAction>(actionParametersOf(VALUE to "${state.day}|${state.questionId}|$value"))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (on) "✓ $label" else label,
                            style = TextStyle(
                                color = if (on) colors.onPrimaryContainer else colors.onSecondaryContainer,
                                fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }
                    if (i < row.lastIndex) Spacer(GlanceModifier.width(6.dp))
                }
            }
            Spacer(GlanceModifier.height(6.dp))
        }
        if (selected != null) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(colors.primary)
                    .cornerRadius(50.dp)
                    .padding(vertical = 7.dp)
                    .clickable(actionRunCallback<SendAnswerAction>(actionParametersOf(VALUE to "${state.day}|${state.questionId}|$selected"))),
                contentAlignment = Alignment.Center,
            ) {
                Text("Envoyer ma réponse 🔒", style = TextStyle(color = colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        } else {
            Text(if (state.status == WidgetStatus.YOUR_TURN_PARTNER_DONE) "L'autre a répondu ✨ À toi !" else "Touche ta réponse", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }

    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 170.dp)
        private val LARGE = DpSize(250.dp, 220.dp)
        val SELECTED = stringPreferencesKey("selected")
        val VALUE = ActionParameters.Key<String>("value")
        private val fallbackColors = ColorProviders(light = MiroirLightScheme, dark = MiroirDarkScheme)

        suspend fun refresh(context: Context) {
            runCatching { MiroirWidget().updateAll(context) }
            MiroirTileService.requestUpdate(context)
        }

    }
}

/** Premier toucher : la réponse est surlignée, rien n'est encore envoyé. */
class PickAnswerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val value = parameters[MiroirWidget.VALUE] ?: return
        updateAppWidgetState(context, glanceId) { it[MiroirWidget.SELECTED] = value }
        MiroirWidget().update(context, glanceId)
    }
}

/** « Envoyer » : même chemin que depuis l'appli (chiffrement, file hors ligne, synchro). */
class SendAnswerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val (day, questionId, value) = parameters[MiroirWidget.VALUE]?.split('|')?.takeIf { it.size == 3 } ?: return
        updateAppWidgetState(context, glanceId) { it.remove(MiroirWidget.SELECTED) }
        val repo = context.applicationContext.container.repository
        val question = repo.dao.day(day)?.takeIf { it.questionId == questionId && it.myAnswer == null }?.let { repo.resolveQuestion(questionId) }
        if (question != null) {
            repo.submitAnswer(
                day,
                AnswerPayload(
                    questionId = questionId,
                    type = question.type,
                    choice = value.toIntOrNull().takeIf { question.type == QuestionType.CHOICE },
                    who = value.takeIf { question.type == QuestionType.WHO },
                    at = System.currentTimeMillis(),
                ),
            )
        }
        MiroirWidget().update(context, glanceId)
    }
}

class MiroirWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiroirWidget()
}

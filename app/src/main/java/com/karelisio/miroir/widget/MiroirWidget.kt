package com.karelisio.miroir.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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

data class WidgetState(val question: String, val badge: String, val status: WidgetStatus, val streak: Int)

class MiroirWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent {
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= 31) GlanceTheme.colors else fallbackColors) {
                Content(state)
            }
        }
    }

    private suspend fun load(context: Context): WidgetState {
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
        return WidgetState(today.questionText, badge, status, streak)
    }

    @Composable
    private fun Content(state: WidgetState) {
        val size = LocalSize.current
        val compact = size.width < WIDE.width
        val tall = size.height >= LARGE.height
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
                maxLines = if (tall) 6 else if (compact) 3 else 2,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.height(8.dp))
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

    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 220.dp)
        private val fallbackColors = ColorProviders(light = MiroirLightScheme, dark = MiroirDarkScheme)

        suspend fun refresh(context: Context) {
            runCatching { MiroirWidget().updateAll(context) }
        }

    }
}

class MiroirWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiroirWidget()
}

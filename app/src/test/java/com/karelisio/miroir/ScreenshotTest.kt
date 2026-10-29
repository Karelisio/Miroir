package com.karelisio.miroir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Member
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.model.QuestionType
import com.karelisio.miroir.data.model.ReactionPayload
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karelisio.miroir.data.model.Guess
import com.karelisio.miroir.ui.history.Agreement
import com.karelisio.miroir.ui.lock.LockScreen
import com.karelisio.miroir.ui.recap.Highlight
import com.karelisio.miroir.ui.recap.Recap
import com.karelisio.miroir.ui.recap.RecapContent
import java.time.YearMonth
import com.karelisio.miroir.ui.history.HistoryContent
import com.karelisio.miroir.ui.history.HistoryItem
import com.karelisio.miroir.ui.history.HistoryUi
import com.karelisio.miroir.ui.onboarding.OnboardingScreen
import com.karelisio.miroir.ui.pairing.PairingScreen
import com.karelisio.miroir.ui.pairing.QrCode
import com.karelisio.miroir.ui.settings.PermissionsScreen
import com.karelisio.miroir.ui.settings.SettingsScreen
import com.karelisio.miroir.ui.settings.SettingsViewModel
import com.karelisio.miroir.ui.theme.MiroirRoot
import com.karelisio.miroir.ui.theme.MiroirTheme
import com.karelisio.miroir.ui.today.MirrorReveal
import com.karelisio.miroir.ui.today.Phase
import com.karelisio.miroir.ui.today.TodayActions
import com.karelisio.miroir.ui.today.TodayContent
import com.karelisio.miroir.ui.today.TodayUi
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Captures des écrans clés (thème de repli rose/violet) dans app/screenshots/. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 28)
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun entity(q: Question, mine: AnswerPayload? = null, theirs: AnswerPayload? = null, partnerAnswered: Boolean = theirs != null) = DayEntity(
        day = day.toString(), questionId = q.id, tag = "t", questionText = q.text, category = q.category, pack = q.pack,
        myAnswer = mine?.let { "x" }, partnerAnswered = partnerAnswered, partnerAnswer = theirs?.let { "x" }, revealSeen = true,
    )

    private fun ui(q: Question, phase: Phase, mine: AnswerPayload? = null, theirs: AnswerPayload? = null, partnerAnswered: Boolean = theirs != null) = TodayUi(
        loading = false, paired = true, partnerJoined = true, day = day, entity = entity(q, mine, theirs, partnerAnswered), question = q,
        questionText = q.text, phase = phase, myAnswer = mine, partnerAnswer = theirs, partnerAnswered = partnerAnswered,
        canSkip = mine == null && !partnerAnswered, streak = 12, bestStreak = 21, me = Member.A, myName = "Léa", partnerName = "Sam",
        myReaction = if (phase == Phase.REVEALED) ReactionPayload("😂", null, 0) else null,
        partnerReaction = if (phase == Phase.REVEALED) ReactionPayload("💜", "Je le savais !", 0) else null,
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            MiroirRoot(darkTheme = dark, dynamicColor = false) { content() }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val choice = TestBank.questions.first { it.pack == null && it.type == QuestionType.CHOICE && it.category == "tu_preferes" }
    private val scale = TestBank.questions.first { it.pack == "anniversaire_couple" && it.type == QuestionType.SCALE }
    private val who = TestBank.questions.first { it.pack == null && it.type == QuestionType.WHO && it.category == "qui_de_nous" }
    private val text = TestBank.questions.first { it.pack == null && it.type == QuestionType.TEXT && it.category == "souvenirs" }

    @Test fun today_choice() = shot("01_today_choice") { TodayContent(ui(choice, Phase.ANSWER), false, false, padding, TodayActions()) }

    /** Réponse choisie : le pronostic « Et selon toi ? » apparaît. */
    @Test fun today_guess() {
        compose.setContent {
            MiroirRoot(dynamicColor = false) { TodayContent(ui(choice, Phase.ANSWER), false, false, padding, TodayActions()) }
        }
        compose.onNodeWithText(choice.options[0]).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodesWithText(choice.options[1])[1].performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("screenshots/01a_today_guess.png")
    }

    @Test fun today_together() = shot("01b_today_together") {
        TodayContent(
            ui(text, Phase.WAITING, mine = AnswerPayload(text.id, QuestionType.TEXT, text = "Le pique-nique sous l'orage.", at = 0))
                .copy(together = day.minusDays(1000), recapMonth = YearMonth.of(2026, 9)),
            false, false, padding, TodayActions(),
        )
    }

    @Test fun today_scale_dark() = shot("02_today_scale_dark", dark = true) {
        TodayContent(ui(scale, Phase.ANSWER, partnerAnswered = true), false, false, padding, TodayActions())
    }

    @Test fun today_who() = shot("03_today_who") { TodayContent(ui(who, Phase.ANSWER), false, false, padding, TodayActions()) }

    @Test fun today_waiting() = shot("04_today_waiting") {
        TodayContent(ui(text, Phase.WAITING, mine = AnswerPayload(text.id, QuestionType.TEXT, text = "Notre fou rire dans le train de nuit pour Venise.", at = 0)), false, false, padding, TodayActions())
    }

    @Test fun today_catch_up() = shot("04a_today_catch_up") {
        val yesterday = entity(who).copy(day = day.minusDays(1).toString(), questionText = who.text, partnerAnswered = true)
        TodayContent(ui(choice, Phase.ANSWER).copy(catchUp = yesterday), false, false, padding, TodayActions())
    }

    @Test fun today_nudge_sent() = shot("04b_today_nudge_sent", dark = true) {
        TodayContent(
            ui(text, Phase.WAITING, mine = AnswerPayload(text.id, QuestionType.TEXT, text = "Le pique-nique sous l'orage.", at = 0)).copy(nudgeSent = true),
            false, false, padding, TodayActions(),
        )
    }

    @Test fun today_ready() = shot("05_today_ready") {
        TodayContent(
            ui(who, Phase.READY_TO_REVEAL, AnswerPayload(who.id, QuestionType.WHO, who = "A", at = 0), AnswerPayload(who.id, QuestionType.WHO, who = "A", at = 0)),
            false, false, padding, TodayActions(),
        )
    }

    @Test fun today_revealed() = shot("06_today_revealed") {
        TodayContent(
            ui(
                scale, Phase.REVEALED,
                AnswerPayload(scale.id, QuestionType.SCALE, scale = 8, at = 0, guess = Guess(scale = 7)),
                AnswerPayload(scale.id, QuestionType.SCALE, scale = 7, at = 0, guess = Guess(scale = 5)),
            ),
            false, false, padding, TodayActions(),
        )
    }

    @Test fun reactions_status() = shot("06a_reactions_status") {
        androidx.compose.foundation.layout.Column(Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp)) {
            com.karelisio.miroir.ui.today.ReactionsSection(ReactionPayload(null, "Je tâcherais de m'habiller plus comme ça ❤️", 0), null, "Sam", synced = true) { _, _ -> }
            com.karelisio.miroir.ui.today.ReactionsSection(ReactionPayload("🔥", null, 0), null, "Sam", synced = false) { _, _ -> }
        }
    }

    /** Images clés de l'animation de révélation (horloge de test contrôlée). */
    @Test
    fun reveal_frames() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MiroirTheme(dynamicColor = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    MirrorReveal(
                        question = text,
                        myAnswer = AnswerPayload(text.id, QuestionType.TEXT, text = "La cabane sous la pluie, en Bretagne 🌧️", at = 0),
                        partnerAnswer = AnswerPayload(text.id, QuestionType.TEXT, text = "Le pique-nique raté qui a fini en fou rire.", at = 0),
                        me = Member.A, myName = "Léa", partnerName = "Sam", play = true, onPlayed = {},
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
        listOf(300L, 650L, 850L, 1100L, 1800L).forEachIndexed { i, t ->
            compose.mainClock.advanceTimeBy(if (i == 0) t else t - listOf(300L, 650L, 850L, 1100L, 1800L)[i - 1])
            compose.onRoot().captureRoboImage("screenshots/07_reveal_frame_${i + 1}.png")
        }
    }

    @Test fun history() = shot("08_history") {
        val items = TestBank.questions.filter { it.pack == null }.shuffled(kotlin.random.Random(3)).take(6).mapIndexed { i, q ->
            HistoryItem(
                day.minusDays(i.toLong()).toString(), day.minusDays(i.toLong()), q.text, Category.of(q.category),
                revealed = i != 1, status = if (i != 1) "Révélé 💜" else "En attente de Sam",
                reactions = if (i % 2 == 0) listOf("😂", "💜") else emptyList(), searchText = "", favorite = i == 2,
            )
        }
        val agreements = listOf(Agreement("Qui de nous deux", "👀", 72, 18), Agreement("Choix", "⚖️", 58, 12), Agreement("Échelles (à 1 près)", "📏", 81, 16))
        val guesses = listOf(Agreement("Léa devine Sam", "🔮", 70, 20), Agreement("Sam devine Léa", "🔮", 62, 21))
        HistoryContent(HistoryUi(items, together = 42, streak = 12, best = 21, agreements = agreements, guesses = guesses), padding, {}, {}, {})
    }

    @Test fun recap() = shot("08a_recap") {
        val recap = Recap(
            month = YearMonth.of(2026, 9), days = 29, together = 26, bestStreak = 14,
            reactions = listOf("😂" to 9, "💜" to 7, "🥰" to 4), comments = 11, agreement = 64, guess = 71,
            topCategory = com.karelisio.miroir.data.model.Category.SOUVENIRS,
            highlight = Highlight("2026-09-12", "Quel est le meilleur fou rire qu'on ait eu ensemble ?", "Léa : Le train de nuit pour Venise", "Sam : La crêpe tombée au plafond", favorite = true),
        )
        RecapContent(recap to listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), {}, {})
    }

    @Test fun lock() = shot("08b_lock", dark = true) { LockScreen {} }

    @Test fun qr() = shot("09_qr") {
        QrCode("miroir://pair?v=1&id=MB_ZOoxuPXw_DIApG3oAFg&s=" + "a".repeat(43) + "&k=" + "b".repeat(43) + "&u=https%3A%2F%2Fmiroir.exemple.workers.dev", Modifier.padding(40.dp))
    }

    @Test fun onboarding() {
        val c = ApplicationProvider.getApplicationContext<Context>().container
        shot("10_onboarding") { OnboardingScreen(c) {} }
    }

    @Test fun onboarding_dark() {
        val c = ApplicationProvider.getApplicationContext<Context>().container
        shot("10a_onboarding_dark", dark = true) { OnboardingScreen(c) {} }
    }

    @Test fun onboarding_permission() {
        val c = ApplicationProvider.getApplicationContext<Context>().container
        shot("10b_onboarding_permission") { OnboardingScreen(c, initialPage = 4) {} }
    }

    @Test fun pairing_choose() {
        val c = ApplicationProvider.getApplicationContext<Context>().container
        runBlocking { c.prefs.update { myProfile("Léa", null) } }
        shot("10c_pairing") { PairingScreen(c, showInvite = false, onPaired = {}, onBack = null, onMessage = {}) }
    }

    @Test fun settings() {
        val c = ApplicationProvider.getApplicationContext<Context>().container
        runBlocking {
            c.prefs.update {
                myProfile("Léa", "1996-03-14")
                partnerProfile("Sam", null)
                partnerJoined = true
            }
        }
        shot("11_settings") { SettingsScreen(SettingsViewModel(c), padding, {}, {}, {}, {}, {}) }
    }

    @Test fun permissions() {
        val c = ApplicationProvider.getApplicationContext<Context>().container
        shot("12_permissions") { PermissionsScreen(c) {} }
    }

    /** Pages du livre souvenir PDF, dessinées sur une image (même code que l'export). */
    @Test
    fun pdf() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val json = kotlinx.serialization.json.Json { encodeDefaults = false; explicitNulls = false }
        fun enc(a: AnswerPayload) = json.encodeToString(AnswerPayload.serializer(), a)
        val qs = listOf(text, choice, who, scale)
        val entries = (0 until 7).map { i ->
            val q = qs[i % qs.size]
            val (a, b) = when (q.type) {
                QuestionType.TEXT -> AnswerPayload(q.id, q.type, text = "Ton rire dans la file d'attente du cinéma, et ce pull jaune.", at = 0) to
                    AnswerPayload(q.id, q.type, text = "Tes yeux qui cherchaient la sortie : tu étais aussi timide que moi.", at = 0)
                QuestionType.CHOICE -> AnswerPayload(q.id, q.type, choice = 0, at = 0) to AnswerPayload(q.id, q.type, choice = 1, at = 0)
                QuestionType.WHO -> AnswerPayload(q.id, q.type, who = "A", at = 0) to AnswerPayload(q.id, q.type, who = "A", at = 0)
                QuestionType.SCALE -> AnswerPayload(q.id, q.type, scale = 8, at = 0) to AnswerPayload(q.id, q.type, scale = 6, at = 0)
            }
            com.karelisio.miroir.export.MemoriesPdf.Entry(
                DayEntity(
                    day = day.minusDays(7L - i).toString(), questionId = q.id, tag = "t", questionText = q.text, category = q.category,
                    myAnswer = enc(a), partnerAnswered = true, partnerAnswer = enc(b), favorite = i == 2,
                    partnerReaction = if (i % 2 == 0) json.encodeToString(ReactionPayload.serializer(), ReactionPayload("😂", "Je le savais !", 0)) else null,
                ),
                q,
            )
        }
        val prefs = com.karelisio.miroir.data.prefs.AppPrefs(myName = "Léa", partnerName = "Sam")
        val pages = com.karelisio.miroir.export.MemoriesPdf.pages(ctx, prefs, entries, day)
        val dir = File("build/outputs/roborazzi/screenshots").apply { mkdirs() }
        pages.take(2).forEachIndexed { i, draw ->
            val bmp = Bitmap.createBitmap(595 * 2, 842 * 2, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply { scale(2f, 2f); draw(this) }
            File(dir, "15_pdf_${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        org.junit.Assert.assertEquals(7, com.karelisio.miroir.export.MemoriesPdf.revealed(entries).size)
    }

    /** Icône adaptative (masque circulaire) et version monochrome. */
    @Test
    fun icons() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        fun render(name: String, layers: List<Int>, tint: Int? = null) {
            val size = 432
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            if (tint != null) canvas.drawColor(0xFF3B2F33.toInt())
            layers.forEach { id ->
                ctx.getDrawable(id)!!.apply {
                    setBounds(0, 0, size, size)
                    tint?.let { setTint(it) }
                    draw(canvas)
                }
            }
            val masked = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            Canvas(masked).apply {
                drawPath(Path().apply { addCircle(size / 2f, size / 2f, size / 3f, Path.Direction.CW) }, Paint(Paint.ANTI_ALIAS_FLAG))
                drawBitmap(bmp, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
            }
            val dir = File("build/outputs/roborazzi/screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { masked.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        render("13_icon", listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground))
        render("14_icon_monochrome", listOf(R.drawable.ic_launcher_monochrome), tint = 0xFFF2DDE2.toInt())
    }
}

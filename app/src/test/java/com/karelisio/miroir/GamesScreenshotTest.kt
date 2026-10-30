package com.karelisio.miroir

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.karelisio.miroir.games.data.GameAnswerEntity
import com.karelisio.miroir.games.data.GameSessionEntity
import com.karelisio.miroir.games.model.GameAnswer
import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameKind
import com.karelisio.miroir.games.model.GameValue
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.model.ItemKind
import com.karelisio.miroir.games.model.MatchKind
import com.karelisio.miroir.games.model.SessionPayload
import com.karelisio.miroir.games.ui.Badge
import com.karelisio.miroir.games.ui.CategoryScore
import com.karelisio.miroir.games.ui.DONE_ITEM
import com.karelisio.miroir.games.ui.DefiCard
import com.karelisio.miroir.games.ui.GameStats
import com.karelisio.miroir.games.ui.GamesUi
import com.karelisio.miroir.games.ui.SessionCard
import com.karelisio.miroir.games.ui.SessionStatus
import com.karelisio.miroir.games.ui.SessionUi
import com.karelisio.miroir.games.ui.SessionView
import com.karelisio.miroir.games.ui.canape.CanapeContent
import com.karelisio.miroir.games.ui.canape.CanapeStep
import com.karelisio.miroir.games.ui.canape.CanapeUi
import com.karelisio.miroir.games.ui.components.RevealPair
import com.karelisio.miroir.games.ui.envies.EnvieMatch
import com.karelisio.miroir.games.ui.envies.EnviesContent
import com.karelisio.miroir.games.ui.envies.EnviesUi
import com.karelisio.miroir.games.ui.extras.StatsContent
import com.karelisio.miroir.games.ui.home.GamesActions
import com.karelisio.miroir.games.ui.home.GamesHome
import com.karelisio.miroir.games.ui.home.GamesOnboarding
import com.karelisio.miroir.games.ui.questionKey
import com.karelisio.miroir.games.ui.session.SessionContent
import com.karelisio.miroir.games.ui.theme.GamesTheme
import com.karelisio.miroir.ui.theme.MiroirRoot
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captures de « De l'autre côté » (palette chaude, thème de repli) dans app/screenshots/. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GamesScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)
    private val json = Json

    private fun shot(name: String, advance: Long = 2500, content: @Composable () -> Unit) {
        compose.setContent { MiroirRoot(darkTheme = false, dynamicColor = false) { GamesTheme { content() } } }
        compose.mainClock.advanceTimeBy(advance)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val choice = GameItem(
        "fun-a-0001", GameCategory.FUN, ItemKind.DEVINE, 1, "Quel superpouvoir {autre} choisirait sans hésiter ?",
        ItemFormat.CHOICE, listOf("Voler", "Lire dans les pensées", "Se téléporter", "Arrêter le temps"),
    )
    private val scale = GameItem("rom-a-0001", GameCategory.ROMANTIQUE, ItemKind.DEVINE, 2, "À quel point {autre} aime les déclarations en public ?", ItemFormat.SCALE, min = "Pas du tout", max = "Adore")
    private val text = GameItem("sou-a-0001", GameCategory.SOUVENIRS, ItemKind.DEVINE, 1, "Le plat que {autre} commanderait au restaurant de votre premier rendez-vous ?", ItemFormat.TEXT)
    private val preferes = GameItem("ose-a-0901", GameCategory.OSE, ItemKind.PREFERES, 2, "Tu préfères…", ItemFormat.CHOICE, listOf("Un slow collé-serré", "Un regard qui en dit long"))

    private fun answer(sid: String, member: String, i: Int, a: GameAnswer) =
        GameAnswerEntity(sid, member, questionKey(i), json.encodeToString(GameAnswer.serializer(), a), 0)

    private fun session(kind: GameKind, items: List<GameItem>, answers: List<GameAnswerEntity>, answerer: String? = null) = SessionUi(
        view = SessionView(
            GameSessionEntity(
                id = "s1", kind = kind.key, reveal = kind.reveal, createdBy = "B",
                payload = json.encodeToString(SessionPayload.serializer(), SessionPayload(items = items.map { it.id }, answerer = answerer)),
                createdAt = 0, updatedAt = 0,
            ),
            answers, me = "A",
        ),
        items = items, itemsById = items.associateBy { it.id }, myName = "Léa", partnerName = "Sam",
        pins = emptySet(), spicy = false, lockSpicy = false,
    )

    private val stats = GameStats(
        connivence = 72, points = 148,
        byCategory = listOf(
            CategoryScore(GameCategory.SOUVENIRS, 81, 32), CategoryScore(GameCategory.FUN, 64, 25),
            CategoryScore(GameCategory.ROMANTIQUE, 77, 18), CategoryScore(GameCategory.OSE, 58, 12),
        ),
        agreementByCategory = listOf(CategoryScore(GameCategory.VALEURS, 85, 20), CategoryScore(GameCategory.FUTUR, 70, 14)),
        played = 23, streak = 5, bestStreak = 9, matches = 11,
        badges = listOf(
            Badge("first", "🪞", "Premier reflet", "Une première partie", true),
            Badge("duel", "⚔️", "Duellistes", "5 duels terminés", true),
            Badge("match", "💞", "Sur la même longueur", "10 envies communes", true),
            Badge("streak", "🔥", "Inséparables", "7 jours d'affilée", false),
        ),
    )

    private val gamesUi = GamesUi(
        loading = false, myName = "Léa", partnerName = "Sam",
        ongoing = listOf(
            SessionCard("d1", GameKind.DUEL, SessionStatus.YOUR_TURN, "Duel à distance", "En attente de toi · 7 questions", 0),
            SessionCard("d2", GameKind.DEVINE, SessionStatus.WAITING, "Devine-moi", "En attente de Sam", 0),
        ),
        defi = DefiCard("defi-week-2026-W44", "2026-W44", "Glissez un mot doux quelque part où l'autre le trouvera par hasard.", 1, false, null),
        history = listOf(SessionCard("h1", GameKind.PREFERES, SessionStatus.DONE, "Terminée", "8 dilemmes", 0)),
        matches = 11, newMatches = 2, stats = stats, intensity = 2, myIntensity = 2, partnerIntensity = 3, bankSize = 16000,
    )

    @Test fun games_home() = shot("20_games_home") { GamesHome(gamesUi, false, padding, GamesActions()) }

    @Test fun games_onboarding() = shot("21_games_onboarding") { GamesOnboarding(padding) { _, _ -> } }

    @Test fun canape_pass() = shot("22_canape_pass") {
        CanapeContent(CanapeUi(players = listOf("Léa", "Sam"), items = listOf(choice), step = CanapeStep.Pass(1, CanapeStep.Guess(0, 1))), padding, null)
    }

    @Test fun canape_reveal() = shot("23_canape_reveal") {
        CanapeContent(
            CanapeUi(
                players = listOf("Léa", "Sam"), items = listOf(choice, scale), rounds = 2, step = CanapeStep.Reveal(0),
                answers = mapOf(0 to GameValue(choice = 3)), guesses = mapOf(0 to GameValue(choice = 3)), results = mapOf(0 to true),
            ),
            padding, null,
        )
    }

    @Test fun reveal_pair() = shot("24_reveal_pair") {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            RevealPair("Sam a répondu", "Arrêter le temps", "Léa a deviné", "Arrêter le temps", match = true, play = false)
            RevealPair("Sam a répondu", "4 / 5", "Léa a deviné", "1 / 5", match = false, play = false)
        }
    }

    @Test fun session_devine() = shot("25_session_devine") {
        val items = listOf(choice, scale, text)
        SessionContent(
            session(
                GameKind.DEVINE, items,
                listOf(
                    answer("s1", "B", 0, GameAnswer(self = GameValue(choice = 1))), answer("s1", "A", 0, GameAnswer(guess = GameValue(choice = 1))),
                    answer("s1", "B", 1, GameAnswer(self = GameValue(scale = 2))), answer("s1", "A", 1, GameAnswer(guess = GameValue(scale = 5))),
                    answer("s1", "B", 2, GameAnswer(self = GameValue(text = "Des pâtes à la truffe"))), answer("s1", "A", 2, GameAnswer(guess = GameValue(text = "Un risotto"))),
                    GameAnswerEntity("s1", "A", DONE_ITEM, "{}", 0), GameAnswerEntity("s1", "B", DONE_ITEM, "{}", 0),
                ),
                answerer = "B",
            ),
            padding, null,
        )
    }

    @Test fun session_duel_input() = shot("26_session_duel") {
        SessionContent(session(GameKind.DUEL, listOf(choice, scale), emptyList()), padding, null)
    }

    @Test fun session_preferes_input() = shot("27_session_preferes") {
        SessionContent(session(GameKind.PREFERES, listOf(preferes), emptyList()), padding, null)
    }

    @Test fun envies_matches() {
        val items = listOf(
            GameItem("rom-b-0601", GameCategory.ROMANTIQUE, ItemKind.ENVIE, 1, "Un week-end sans téléphone, rien que nous deux"),
            GameItem("ose-b-0700", GameCategory.OSE, ItemKind.ENVIE, 2, "Un dîner aux chandelles avec un code vestimentaire choisi par l'autre"),
            GameItem("fut-b-0800", GameCategory.FUTUR, ItemKind.ENVIE, 1, "Apprendre une danse ensemble"),
        )
        val ui = EnviesUi(
            loading = false, partnerName = "Sam", all = items, total = 600,
            matches = listOf(EnvieMatch(items[0], MatchKind.MATCH, true), EnvieMatch(items[1], MatchKind.DISCUSS, false), EnvieMatch(items[2], MatchKind.MATCH, false)),
        )
        compose.setContent { MiroirRoot(darkTheme = false, dynamicColor = false) { GamesTheme { EnviesContent(ui, padding, null) } } }
        compose.onNodeWithText("Nos matchs · 1 nouveau").performClick()
        compose.mainClock.advanceTimeBy(2500)
        compose.onRoot().captureRoboImage("screenshots/28_envies_matches.png")
    }

    @Test fun games_stats() = shot("29_games_stats") { StatsContent(gamesUi, padding) {} }
}

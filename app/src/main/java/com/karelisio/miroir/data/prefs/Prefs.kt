package com.karelisio.miroir.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karelisio.miroir.data.crypto.KeystoreBox
import com.karelisio.miroir.data.model.Member
import com.karelisio.miroir.data.model.SharedSettings
import com.karelisio.miroir.games.model.GameSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("miroir")

/** Secrets d'appairage : stockés scellés par l'Android Keystore, jamais en clair. */
@Serializable
data class PairCredentials(
    val pairId: String,
    val secret: String,
    val key: String,
    val member: String,
    val serverUrl: String,
) {
    val memberEnum: Member get() = Member.of(member) ?: Member.A
}

data class AppPrefs(
    val onboardingDone: Boolean = false,
    val paired: Boolean = false,
    val member: Member = Member.A,
    val partnerJoined: Boolean = false,
    val myName: String = "",
    val myBirthday: String? = null,
    val partnerName: String? = null,
    val partnerBirthday: String? = null,
    val shared: SharedSettings = SharedSettings(),
    val sharedVersion: Int = 0,
    val sharedDirty: Boolean = false,
    val profileDirty: Boolean = false,
    val rev: Long = 0,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val notifyPartner: Boolean = true,
    val fullScreenReveal: Boolean = false,
    val bypassDnd: Boolean = false,
    val haptics: Boolean = true,
    val skippedPermissions: Set<String> = emptySet(),
    val permissionReminderDismissedAt: Long = 0,
    val ignoredVersion: String? = null,
    val lastUpdateCheck: Long = 0,
    /** Relance vers 22 h si la question du jour n'a pas de réponse. */
    val eveningReminder: Boolean = true,
    /** Verrouillage par empreinte ou code du téléphone. */
    val appLock: Boolean = false,
    /** Dernier récap mensuel vu ("2026-10"). */
    val lastRecapSeen: String? = null,
    // De l'autre côté (jeux)
    val gamesOnboardingDone: Boolean = false,
    val gamesSettings: GameSettings = GameSettings(),
    /** Réglages de l'autre (null tant qu'il n'a pas ouvert la section : intensité 1 par prudence). */
    val partnerGamesSettings: GameSettings? = null,
    val gamesSettingsDirty: Boolean = false,
    val gamesRev: Long = 0,
    val gamesWishesRev: Long = 0,
    /** Verrou biométrique pour Osé et Intime. */
    val gamesLockSpicy: Boolean = false,
    /** Chrono du mode canapé, en secondes (0 = sans). */
    val gamesTimer: Int = 0,
    val gamesBadges: Set<String> = emptySet(),
    /** Parties dont j'ai vu la révélation : elles passent dans l'historique. */
    val gamesRevealSeen: Set<String> = emptySet(),
) {
    /** Le jeu n'utilise jamais une intensité supérieure au minimum des deux réglages. */
    val gamesIntensity: Int get() = minOf(gamesSettings.intensity, partnerGamesSettings?.intensity ?: 1).coerceIn(1, 3)

    /** Union des catégories exclues par chacun. */
    val gamesExcluded: Set<String> get() = gamesSettings.excluded + (partnerGamesSettings?.excluded ?: emptySet())

    val partnerDisplayName: String get() = partnerName?.takeIf { it.isNotBlank() } ?: "l'autre"
}

class Prefs(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val creds = stringPreferencesKey("creds_sealed")
        val member = stringPreferencesKey("member")
        val partnerJoined = booleanPreferencesKey("partner_joined")
        val myName = stringPreferencesKey("my_name")
        val myBirthday = stringPreferencesKey("my_birthday")
        val partnerName = stringPreferencesKey("partner_name")
        val partnerBirthday = stringPreferencesKey("partner_birthday")
        val shared = stringPreferencesKey("shared_settings")
        val sharedVersion = intPreferencesKey("shared_version")
        val sharedDirty = booleanPreferencesKey("shared_dirty")
        val profileDirty = booleanPreferencesKey("profile_dirty")
        val rev = longPreferencesKey("rev")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val notifyPartner = booleanPreferencesKey("notify_partner")
        val fullScreen = booleanPreferencesKey("full_screen_reveal")
        val bypassDnd = booleanPreferencesKey("bypass_dnd")
        val haptics = booleanPreferencesKey("haptics")
        val skippedPerms = stringSetPreferencesKey("skipped_permissions")
        val permReminder = longPreferencesKey("permission_reminder_dismissed")
        val ignoredVersion = stringPreferencesKey("ignored_version")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val fcmToken = stringPreferencesKey("fcm_token")
        val fcmTokenSent = booleanPreferencesKey("fcm_token_sent")
        val eveningReminder = booleanPreferencesKey("evening_reminder")
        val appLock = booleanPreferencesKey("app_lock")
        val lastRecapSeen = stringPreferencesKey("last_recap_seen")
        val gamesOnboarding = booleanPreferencesKey("games_onboarding_done")
        val gamesSettings = stringPreferencesKey("games_settings")
        val partnerGamesSettings = stringPreferencesKey("games_partner_settings")
        val gamesSettingsDirty = booleanPreferencesKey("games_settings_dirty")
        val gamesRev = longPreferencesKey("games_rev")
        val gamesWishesRev = longPreferencesKey("games_wishes_rev")
        val gamesLockSpicy = booleanPreferencesKey("games_lock_spicy")
        val gamesTimer = intPreferencesKey("games_timer")
        val gamesBadges = stringSetPreferencesKey("games_badges")
        val gamesRevealSeen = stringSetPreferencesKey("games_reveal_seen")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val data: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            onboardingDone = p[K.onboarding] ?: false,
            paired = p[K.creds] != null,
            member = Member.of(p[K.member]) ?: Member.A,
            partnerJoined = p[K.partnerJoined] ?: false,
            myName = p[K.myName] ?: "",
            myBirthday = p[K.myBirthday],
            partnerName = p[K.partnerName],
            partnerBirthday = p[K.partnerBirthday],
            shared = p[K.shared]?.let { runCatching { json.decodeFromString<SharedSettings>(it) }.getOrNull() } ?: SharedSettings(),
            sharedVersion = p[K.sharedVersion] ?: 0,
            sharedDirty = p[K.sharedDirty] ?: false,
            profileDirty = p[K.profileDirty] ?: false,
            rev = p[K.rev] ?: 0,
            reminderEnabled = p[K.reminderEnabled] ?: true,
            reminderHour = p[K.reminderHour] ?: 20,
            reminderMinute = p[K.reminderMinute] ?: 0,
            notifyPartner = p[K.notifyPartner] ?: true,
            fullScreenReveal = p[K.fullScreen] ?: false,
            bypassDnd = p[K.bypassDnd] ?: false,
            haptics = p[K.haptics] ?: true,
            skippedPermissions = p[K.skippedPerms] ?: emptySet(),
            permissionReminderDismissedAt = p[K.permReminder] ?: 0,
            ignoredVersion = p[K.ignoredVersion],
            lastUpdateCheck = p[K.lastUpdateCheck] ?: 0,
            eveningReminder = p[K.eveningReminder] ?: true,
            appLock = p[K.appLock] ?: false,
            lastRecapSeen = p[K.lastRecapSeen],
            gamesOnboardingDone = p[K.gamesOnboarding] ?: false,
            gamesSettings = p[K.gamesSettings]?.let { runCatching { json.decodeFromString<GameSettings>(it) }.getOrNull() } ?: GameSettings(),
            partnerGamesSettings = p[K.partnerGamesSettings]?.let { runCatching { json.decodeFromString<GameSettings>(it) }.getOrNull() },
            gamesSettingsDirty = p[K.gamesSettingsDirty] ?: false,
            gamesRev = p[K.gamesRev] ?: 0,
            gamesWishesRev = p[K.gamesWishesRev] ?: 0,
            gamesLockSpicy = p[K.gamesLockSpicy] ?: false,
            gamesTimer = p[K.gamesTimer] ?: 0,
            gamesBadges = p[K.gamesBadges] ?: emptySet(),
            gamesRevealSeen = p[K.gamesRevealSeen] ?: emptySet(),
        )
    }

    suspend fun current(): AppPrefs = data.first()

    suspend fun credentials(): PairCredentials? {
        val sealed = context.dataStore.data.first()[K.creds] ?: return null
        val plain = KeystoreBox.open(sealed) ?: return null
        return runCatching { json.decodeFromString<PairCredentials>(plain) }.getOrNull()
    }

    suspend fun saveCredentials(c: PairCredentials) = context.dataStore.edit {
        it[K.creds] = KeystoreBox.seal(json.encodeToString(PairCredentials.serializer(), c))
        it[K.member] = c.member
        it[K.rev] = 0
        it[K.fcmTokenSent] = false
    }

    /** Dissociation : on oublie tout ce qui concerne le couple, on garde les préférences locales. */
    suspend fun clearPairing() = context.dataStore.edit {
        listOf(
            K.creds, K.member, K.partnerJoined, K.partnerName, K.partnerBirthday, K.shared, K.sharedVersion, K.sharedDirty, K.rev, K.fcmTokenSent,
            K.partnerGamesSettings, K.gamesRev, K.gamesWishesRev, K.gamesBadges, K.gamesRevealSeen,
        )
            .forEach { k -> it.remove(k) }
    }

    suspend fun update(block: Editor.() -> Unit) = context.dataStore.edit { Editor(it).block() }

    suspend fun fcmToken(): Pair<String?, Boolean> = context.dataStore.data.first().let { it[K.fcmToken] to (it[K.fcmTokenSent] ?: false) }

    inner class Editor(private val p: MutablePreferences) {
        var onboardingDone: Boolean by bool(K.onboarding)
        var partnerJoined: Boolean by bool(K.partnerJoined)
        var reminderEnabled: Boolean by bool(K.reminderEnabled, true)
        var notifyPartner: Boolean by bool(K.notifyPartner, true)
        var fullScreenReveal: Boolean by bool(K.fullScreen)
        var bypassDnd: Boolean by bool(K.bypassDnd)
        var haptics: Boolean by bool(K.haptics, true)
        var sharedDirty: Boolean by bool(K.sharedDirty)
        var profileDirty: Boolean by bool(K.profileDirty)
        var fcmTokenSent: Boolean by bool(K.fcmTokenSent)
        var eveningReminder: Boolean by bool(K.eveningReminder, true)
        var appLock: Boolean by bool(K.appLock)
        var gamesOnboardingDone: Boolean by bool(K.gamesOnboarding)
        var gamesSettingsDirty: Boolean by bool(K.gamesSettingsDirty)
        var gamesLockSpicy: Boolean by bool(K.gamesLockSpicy)
        val gamesSettings: GameSettings
            get() = p[K.gamesSettings]?.let { runCatching { json.decodeFromString<GameSettings>(it) }.getOrNull() } ?: GameSettings()

        // Lectures dans la même transaction que l'écriture (pas de mise à jour perdue).
        val myName: String get() = p[K.myName] ?: ""
        val myBirthday: String? get() = p[K.myBirthday]
        val sharedVersion: Int get() = p[K.sharedVersion] ?: 0
        val shared: SharedSettings
            get() = p[K.shared]?.let { runCatching { json.decodeFromString<SharedSettings>(it) }.getOrNull() } ?: SharedSettings()

        fun myProfile(name: String, birthday: String?) {
            p[K.myName] = name
            if (birthday != null) p[K.myBirthday] = birthday else p.remove(K.myBirthday)
        }

        fun partnerProfile(name: String?, birthday: String?) {
            if (name != null) p[K.partnerName] = name else p.remove(K.partnerName)
            if (birthday != null) p[K.partnerBirthday] = birthday else p.remove(K.partnerBirthday)
        }

        fun shared(settings: SharedSettings, version: Int) {
            p[K.shared] = json.encodeToString(SharedSettings.serializer(), settings)
            p[K.sharedVersion] = version
        }

        fun rev(rev: Long) { p[K.rev] = rev }
        fun reminder(hour: Int, minute: Int) { p[K.reminderHour] = hour; p[K.reminderMinute] = minute }
        fun skippedPermissions(set: Set<String>) { p[K.skippedPerms] = set }
        fun permissionReminderDismissed(at: Long) { p[K.permReminder] = at }
        fun ignoredVersion(v: String?) { if (v != null) p[K.ignoredVersion] = v else p.remove(K.ignoredVersion) }
        fun lastUpdateCheck(at: Long) { p[K.lastUpdateCheck] = at }
        fun fcmToken(token: String) { p[K.fcmToken] = token }
        fun lastRecapSeen(month: String) { p[K.lastRecapSeen] = month }
        fun gamesSettings(s: GameSettings) { p[K.gamesSettings] = json.encodeToString(GameSettings.serializer(), s) }
        fun partnerGamesSettings(s: GameSettings?) {
            if (s != null) p[K.partnerGamesSettings] = json.encodeToString(GameSettings.serializer(), s) else p.remove(K.partnerGamesSettings)
        }
        fun gamesRev(rev: Long) { p[K.gamesRev] = rev }
        fun gamesWishesRev(rev: Long) { p[K.gamesWishesRev] = rev }
        fun gamesTimer(seconds: Int) { p[K.gamesTimer] = seconds }
        fun gamesBadges(ids: Set<String>) { p[K.gamesBadges] = ids }
        fun gamesRevealSeen(id: String) { p[K.gamesRevealSeen] = (p[K.gamesRevealSeen] ?: emptySet()) + id }

        private fun bool(key: Preferences.Key<Boolean>, default: Boolean = false) =
            object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
                override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = p[key] ?: default
                override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) { p[key] = value }
            }
    }
}

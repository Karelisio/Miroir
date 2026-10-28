package com.karelisio.miroir.permissions

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Toutes les autorisations de Miroir. Les "spéciales" passent par un écran des réglages système :
 * on surveille l'état pendant que l'utilisateur y est, et on referme l'écran dès que c'est accordé.
 */
enum class AppPermission(
    val title: String,
    val why: String,
    val icon: ImageVector,
    val special: Boolean,
    val optional: Boolean = false,
) {
    NOTIFICATIONS(
        "Notifications",
        "Pour la question du jour, savoir quand l'autre a répondu et ne rater aucune révélation.",
        Icons.Rounded.NotificationsActive,
        special = false,
    ),
    CAMERA(
        "Caméra",
        "Pour scanner le QR code de l'autre téléphone et relier vos deux miroirs. Rien n'est enregistré.",
        Icons.Rounded.QrCodeScanner,
        special = false,
    ),
    EXACT_ALARMS(
        "Alarmes exactes",
        "Pour que le rappel quotidien arrive pile à l'heure choisie, pas une demi-heure plus tard.",
        Icons.Rounded.Alarm,
        special = true,
    ),
    FULL_SCREEN(
        "Plein écran",
        "Pour afficher la révélation en grand sur l'écran verrouillé, si tu actives l'option.",
        Icons.Rounded.Fullscreen,
        special = true,
        optional = true,
    ),
    DO_NOT_DISTURB(
        "Ne pas déranger",
        "Pour laisser passer la question du jour même en mode Ne pas déranger, si tu le souhaites.",
        Icons.Rounded.DoNotDisturbOn,
        special = true,
        optional = true,
    ),
    BATTERY(
        "Optimisation de la batterie",
        "Pour que tes réponses partent même hors ligne puis au retour du réseau, sans être endormies.",
        Icons.Rounded.BatteryChargingFull,
        special = true,
    ),
    INSTALL_APPS(
        "Installation des mises à jour",
        "Pour installer les nouvelles versions de Miroir directement depuis l'appli.",
        Icons.Rounded.InstallMobile,
        special = true,
    ),
    ;

    /** Certaines autorisations n'existent pas (ou sont implicites) sur les anciennes versions. */
    val applicable: Boolean
        get() = when (this) {
            NOTIFICATIONS -> Build.VERSION.SDK_INT >= 33
            EXACT_ALARMS -> Build.VERSION.SDK_INT >= 31
            FULL_SCREEN -> Build.VERSION.SDK_INT >= 34
            else -> true
        }

    val runtimePermission: String?
        get() = when (this) {
            NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
            CAMERA -> Manifest.permission.CAMERA
            else -> null
        }

    fun isGranted(context: Context): Boolean {
        if (!applicable) return true
        return when (this) {
            NOTIFICATIONS, CAMERA ->
                ContextCompat.checkSelfPermission(context, runtimePermission!!) == PackageManager.PERMISSION_GRANTED
            EXACT_ALARMS -> Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            DO_NOT_DISTURB -> context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
            BATTERY -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
            INSTALL_APPS -> context.packageManager.canRequestPackageInstalls()
        }
    }

    fun settingsIntent(context: Context): Intent {
        val pkg = "package:${context.packageName}".toUri()
        return when (this) {
            EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else appDetails(context)
            DO_NOT_DISTURB -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            @Suppress("BatteryLife")
            BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            INSTALL_APPS -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg)
            NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            CAMERA -> appDetails(context)
        }
    }

    companion object {
        val all: List<AppPermission> get() = entries.filter { it.applicable }

        fun appDetails(context: Context) =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    }
}

/**
 * Ouvre l'écran système d'une autorisation spéciale et le referme automatiquement dès qu'elle
 * est accordée (sondage léger pendant 2 minutes max). Au retour, l'écran vérifie l'état (ON_RESUME).
 */
object SpecialPermissionLauncher {
    private const val REQUEST_CODE = 4242
    private var watcher: Job? = null

    @Suppress("DEPRECATION")
    fun launch(activity: Activity, permission: AppPermission) {
        val intent = permission.settingsIntent(activity)
        val started = runCatching { activity.startActivityForResult(intent, REQUEST_CODE) }.isSuccess ||
            runCatching { activity.startActivityForResult(AppPermission.appDetails(activity), REQUEST_CODE) }.isSuccess
        if (!started) return
        val ref = WeakReference(activity)
        watcher?.cancel()
        watcher = CoroutineScope(Dispatchers.Main).launch {
            repeat(240) {
                delay(500)
                val a = ref.get() ?: return@launch
                if (permission.isGranted(a)) {
                    a.finishActivity(REQUEST_CODE)
                    return@launch
                }
            }
        }
    }

    fun stopWatching() {
        watcher?.cancel()
        watcher = null
    }
}

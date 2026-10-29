package com.karelisio.miroir.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karelisio.miroir.BuildConfig
import com.karelisio.miroir.container
import com.karelisio.miroir.data.prefs.Prefs
import com.karelisio.miroir.diagnostics.Diagnostics
import com.karelisio.miroir.notifications.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

data class UpdateInfo(
    val version: String,
    val title: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long,
    val htmlUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val info: UpdateInfo, val apk: File) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Mise à jour in-app depuis les GitHub Releases : comparaison de version, téléchargement avec
 * progression puis installation par PackageInstaller (même keystore obligatoire).
 */
class UpdateManager(private val context: Context, private val http: OkHttpClient, private val prefs: Prefs) {

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore("-")

    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Miroir/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(request).execute().use { res ->
            if (res.code == 404) return@withContext null
            if (!res.isSuccessful) throw IOException("GitHub ${res.code}")
            val release = json.decodeFromString(Release.serializer(), res.body.string())
            if (release.draft || release.prerelease) return@withContext null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
            val version = release.tagName.removePrefix("v")
            if (compareVersions(version, currentVersion) <= 0) return@withContext null
            UpdateInfo(version, release.name ?: "Miroir $version", release.body.orEmpty().trim(), apk.url, apk.size, release.htmlUrl)
        }
    }

    /** [manual] : depuis les réglages, on ignore "ignorer cette version" et on affiche "à jour". */
    suspend fun check(manual: Boolean = false): UpdateInfo? {
        if (_state.value is UpdateState.Downloading) return null
        _state.value = UpdateState.Checking
        return try {
            val info = fetchLatest()
            prefs.update { lastUpdateCheck(System.currentTimeMillis()) }
            val ignored = prefs.current().ignoredVersion
            when {
                info == null -> { _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle; null }
                !manual && info.version == ignored -> { _state.value = UpdateState.Idle; null }
                else -> { _state.value = UpdateState.Available(info); info }
            }
        } catch (e: Exception) {
            Diagnostics.record("maj", "vérification impossible", e)
            _state.value = if (manual) UpdateState.Failed("Impossible de joindre GitHub. Vérifie ta connexion.") else UpdateState.Idle
            null
        }
    }

    suspend fun ignore(info: UpdateInfo) {
        prefs.update { ignoredVersion(info.version) }
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    suspend fun downloadAndInstall(info: UpdateInfo) {
        val apk = try {
            download(info)
        } catch (e: Exception) {
            Diagnostics.record("maj", "téléchargement de ${info.version} échoué", e)
            _state.value = UpdateState.Failed("Le téléchargement a échoué.", info)
            return
        }
        installOrAskPermission(info, apk)
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    suspend fun installOrAskPermission(info: UpdateInfo, apk: File) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(info, apk)
            return
        }
        _state.value = UpdateState.Installing(info)
        try {
            install(apk)
        } catch (e: Exception) {
            Diagnostics.record("maj", "installation de ${info.version} impossible", e)
            _state.value = UpdateState.Failed("L'installation n'a pas pu démarrer.", info)
        }
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "miroir-${info.version}.apk")
        _state.value = UpdateState.Downloading(info, 0f)
        val request = Request.Builder().url(info.apkUrl).header("User-Agent", "Miroir/${BuildConfig.VERSION_NAME}").build()
        http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastEmit = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        val progress = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
                        if (progress - lastEmit >= 0.01f || progress == 1f) {
                            lastEmit = progress
                            _state.value = UpdateState.Downloading(info, progress)
                        }
                    }
                }
            }
        }
        target
    }

    private suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("miroir.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed("Signature différente : réinstalle Miroir depuis la release GitHub.", info)
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Espace de stockage insuffisant.", info)
            else -> UpdateState.Failed(message ?: "L'installation a échoué.", info)
        }
    }

    companion object {
        /** Compare deux versions sémantiques "X.Y.Z" (suffixes ignorés). */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
            val pa = parts(a)
            val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            } ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Au premier plan, l'écran de confirmation s'ouvre directement ; sinon (lancement
            // d'activité bloqué en arrière-plan) une notification permet de le rouvrir.
            val started = runCatching { context.startActivity(confirm) }.isSuccess
            if (!started || !Notifier.isAppInForeground()) Notifier.showInstallConfirmation(context, confirm)
            return
        }
        context.container.updates.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}

/** Vérification périodique (6 h) : notifie si une nouvelle version non ignorée existe. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updates = applicationContext.container.updates
        val info = updates.check(manual = false) ?: return Result.success()
        Notifier.showUpdate(applicationContext, info.version)
        return Result.success()
    }
}

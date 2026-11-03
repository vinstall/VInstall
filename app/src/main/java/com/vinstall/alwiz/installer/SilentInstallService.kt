package com.vinstall.alwiz.installer

import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vinstall.alwiz.R
import com.vinstall.alwiz.history.InstallHistoryManager
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.InstallHistoryEntry
import com.vinstall.alwiz.model.PackageFormat
import com.vinstall.alwiz.root.RootHelper
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.MetadataReader
import com.vinstall.alwiz.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class SilentInstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installLock = Mutex()
    private val pending = AtomicInteger(0)
    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        scope.launch { sweepStaleStaging(this@SilentInstallService) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        enterForeground()

        val uris = intent?.let { urisFrom(it) }.orEmpty()
        if (uris.isEmpty()) {
            if (pending.get() == 0) stopSelf(startId)
            return START_NOT_STICKY
        }

        val source = intent?.getStringExtra(EXTRA_SOURCE)
        pending.incrementAndGet()
        scope.launch {
            var staged: StagedRequest? = null
            try {
                staged = stage(uris)
                installLock.withLock { install(staged!!, source) }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Request failed before install: ${e.message}")
                NotificationHelper.postInstallFailure(
                    this@SilentInstallService,
                    "",
                    getString(R.string.silent_install_read_failed, e.message ?: "")
                )
            } finally {
                staged?.dir?.deleteRecursively()
                if (pending.decrementAndGet() == 0) stopSelf(lastStartId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground() {
        val notification = NotificationHelper.buildSilentProgress(
            this, "", getString(R.string.silent_install_preparing)
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationHelper.NOTIF_ID_SILENT, notification, type)
    }

    private class StagedRequest(val dir: File, val files: List<File>)

    private fun stage(uris: List<Uri>): StagedRequest {
        val dir = File(stagingRoot(this), UUID.randomUUID().toString()).also { it.mkdirs() }
        try {
            val files = uris.map { uri ->
                val name = sanitizeName(FileUtil.getFileName(this, uri))
                var target = File(dir, name)
                var n = 1
                while (target.exists()) target = File(dir, "${n++}_$name")
                val input = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open $uri")
                input.use { src ->
                    target.outputStream().buffered(FileUtil.BUFFER_SIZE).use { dst ->
                        src.copyTo(dst, FileUtil.BUFFER_SIZE)
                    }
                }
                target
            }
            return StagedRequest(dir, files)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    private suspend fun install(request: StagedRequest, source: String?) {
        val startedAt = System.currentTimeMillis()
        val files = orderForInstall(request.files)
        val isSingle = files.size == 1
        val format = if (isSingle) detectFormat(files[0]) else PackageFormat.APK
        val meta = readMeta(files, format)
        val label = meta.appLabel.ifBlank { meta.packageName }
        val totalBytes = files.sumOf { it.length() }

        var lastNotify = 0L
        var lastStep = getString(R.string.silent_install_preparing)
        val onStep: (String) -> Unit = { step ->
            lastStep = step
            NotificationHelper.updateSilentProgress(this, label, step)
        }
        val onProgress: (Float) -> Unit = { progress ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotify >= PROGRESS_NOTIFY_INTERVAL_MS) {
                lastNotify = now
                NotificationHelper.updateSilentProgress(this, label, lastStep, progress)
            }
        }
        onStep(getString(R.string.silent_install_preparing))

        InstallHelper.reset()
        val installed: Result<Unit> = withContext(SilentOnly()) {
            val started = if (isSingle) {
                installSingle(Uri.fromFile(files[0]), format, onStep, onProgress)
            } else {
                SplitInstaller.installSplits(this@SilentInstallService, files, null, onProgress)
            }
            if (started.isFailure) {
                started
            } else {
                when (val r = InstallHelper.awaitResult(timeoutMs = INSTALL_TIMEOUT_MS)) {
                    null -> Result.failure<Unit>(Exception("Installation timed out"))
                    is InstallHelper.Result.Success -> Result.success(Unit)
                    is InstallHelper.Result.Failure -> Result.failure<Unit>(Exception(r.message ?: "Install failed"))
                }
            }
        }

        val failure = installed.exceptionOrNull()
        val message = failure?.message ?: ""
        val status = when {
            failure == null -> HistoryStatus.SUCCESS
            message.contains("cancelled", true) || message.contains("aborted", true) -> HistoryStatus.CANCELLED
            else -> HistoryStatus.FAILED
        }
        when (status) {
            HistoryStatus.SUCCESS ->
                NotificationHelper.postInstallSuccess(this, meta.packageName, meta.appLabel)
            HistoryStatus.CANCELLED ->
                NotificationHelper.postInstallCancelled(this, label, message)
            HistoryStatus.FAILED ->
                NotificationHelper.postInstallFailure(this, label, message.ifBlank { "Install failed" })
            HistoryStatus.BLOCKED -> Unit
        }
        DebugLog.i(TAG, "Silent install of ${meta.packageName.ifBlank { files[0].name }} -> $status $message")

        try {
            InstallHistoryManager.add(
                this,
                InstallHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    appLabel = meta.appLabel,
                    packageName = meta.packageName,
                    versionName = meta.versionName,
                    format = if (isSingle) format.label else "APK (${files.size} splits)",
                    fileSize = totalBytes,
                    status = status,
                    detail = if (failure == null) "Installed silently" else message,
                    installMode = AppSettings.getInstallMode(this).name,
                    durationMs = System.currentTimeMillis() - startedAt,
                    sourcePackage = source
                )
            )
        } catch (e: Exception) {
            DebugLog.e(TAG, "Could not record history: ${e.message}")
        }
        if (AppSettings.isAutoClearCache(this)) FileUtil.clearCache(this)
    }

    private suspend fun installSingle(
        uri: Uri,
        format: PackageFormat,
        onStep: (String) -> Unit,
        onProgress: (Float) -> Unit
    ): Result<Unit> = when (format) {
        PackageFormat.APK -> ApkInstaller.install(this, uri, onStep = onStep, onProgress = onProgress)
        PackageFormat.XAPK -> XapkInstaller.install(this, uri, onStep = onStep, selectedSplits = null, onProgress = onProgress)
        PackageFormat.APKS -> ApksInstaller.install(this, uri, onStep = onStep, selectedSplits = null, onProgress = onProgress)
        PackageFormat.APKM -> ApkmInstaller.install(this, uri, onStep = onStep, selectedSplits = null, onProgress = onProgress)
        PackageFormat.APKV -> ApkvInstaller.install(this, uri, null, onStep = onStep, onProgress = onProgress)
        PackageFormat.ZIP -> ZipApkInstaller.install(this, uri, onStep = onStep, selectedSplits = null, onProgress = onProgress)
        else -> Result.failure<Unit>(Exception("Unsupported package format"))
    }

    private fun detectFormat(file: File): PackageFormat {
        val byName = PackageFormat.fromFileName(file.name)
        return if (byName == PackageFormat.UNKNOWN) PackageFormat.APK else byName
    }

    private fun orderForInstall(files: List<File>): List<File> =
        if (files.size <= 1) files
        else files.sortedBy { if (it.name.contains("base", ignoreCase = true)) 0 else 1 }

    private fun readMeta(files: List<File>, format: PackageFormat): MetadataReader.AppMeta {
        return try {
            if (files.size > 1) {
                files.map { readArchiveMeta(it) }.firstOrNull { it.packageName.isNotEmpty() }
                    ?: MetadataReader.AppMeta()
            } else {
                val file = files[0]
                val uri = Uri.fromFile(file)
                when (format) {
                    PackageFormat.APK -> MetadataReader.readFromApk(this, uri, file.name)
                    PackageFormat.XAPK -> MetadataReader.readFromXapk(this, uri)
                    PackageFormat.APKM -> MetadataReader.readFromApkm(this, uri)
                    PackageFormat.APKV -> MetadataReader.readFromApkv(this, uri)
                    PackageFormat.APKS, PackageFormat.ZIP -> MetadataReader.readFromApks(this, uri)
                    else -> MetadataReader.AppMeta()
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "readMeta failed: ${e.message}")
            MetadataReader.AppMeta()
        }
    }

    @Suppress("DEPRECATION")
    private fun readArchiveMeta(file: File): MetadataReader.AppMeta {
        return try {
            val pm = packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return MetadataReader.AppMeta()
            val label = info.applicationInfo?.let { ai ->
                ai.sourceDir = file.absolutePath
                ai.publicSourceDir = file.absolutePath
                pm.getApplicationLabel(ai).toString()
            }.orEmpty()
            MetadataReader.AppMeta(
                packageName = info.packageName ?: "",
                versionName = info.versionName ?: "",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong(),
                appLabel = label
            )
        } catch (_: Exception) {
            MetadataReader.AppMeta()
        }
    }

    companion object {
        private const val TAG = "SilentInstall"
        private const val EXTRA_SOURCE = "com.vinstall.alwiz.extra.SOURCE_PACKAGE"
        private const val STAGING_DIR = "silent_staging"
        private const val INSTALL_TIMEOUT_MS = 120_000L
        private const val PROGRESS_NOTIFY_INTERVAL_MS = 500L
        private const val STALE_STAGING_MS = 60 * 60 * 1000L

        fun urisFrom(intent: Intent): List<Uri> {
            val result = LinkedHashSet<Uri>()
            intent.data?.let { result.add(it) }
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { result.add(it) }
            }
            return result.toList()
        }

        fun isSilentCapable(context: Context): Boolean = when (AppSettings.getInstallMode(context)) {
            InstallMode.SHIZUKU ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    ShizukuHelper.isAvailable() &&
                    ShizukuHelper.isNewProcessAvailable() &&
                    (ShizukuHelper.isGranted() || AppSettings.isShizukuPermissionGranted(context))
            InstallMode.ROOT -> RootHelper.isRooted()
            InstallMode.NORMAL -> false
        }

        fun start(context: Context, uris: List<Uri>, sourcePackage: String? = null) {
            require(uris.isNotEmpty())
            val clip = ClipData.newRawUri("packages", uris[0])
            for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
            val intent = Intent(context, SilentInstallService::class.java).apply {
                clipData = clip
                if (sourcePackage != null) putExtra(EXTRA_SOURCE, sourcePackage)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        private fun stagingRoot(context: Context): File = File(context.filesDir, STAGING_DIR)

        private fun sanitizeName(raw: String): String {
            val base = raw.substringAfterLast('/').replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            return base.ifEmpty { "package.apk" }
        }

        private fun sweepStaleStaging(context: Context) {
            val cutoff = System.currentTimeMillis() - STALE_STAGING_MS
            stagingRoot(context).listFiles()?.forEach {
                if (it.lastModified() < cutoff) it.deleteRecursively()
            }
        }
    }
}

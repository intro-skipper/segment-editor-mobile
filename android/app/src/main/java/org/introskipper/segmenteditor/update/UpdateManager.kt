package org.introskipper.segmenteditor.update

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.BuildConfig
import org.introskipper.segmenteditor.MainActivity
import org.introskipper.segmenteditor.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlin.random.Random

class UpdateManager internal constructor(activity: MainActivity) {
    private val browserActivity = activity
    private var updateListener: UpdateListener? = null
    private var isUpdateAvailable = false
    private var updateUrl: String? = null

    init {
        with (activity.applicationContext.packageManager.packageInstaller) {
            mySessions.forEach {
                try {
                    abandonSession(it.sessionId)
                } catch (_: Exception) { }
            }
        }
        activity.externalCacheDir?.listFiles {
                _: File?, name: String -> name.lowercase().endsWith(".apk")
        }?.forEach { if (!it.isDirectory) it.delete() }
        configureGit()
    }

    private fun configureGit() {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            JSONExecutor(
                browserActivity,  "https://api.github.com/repos/intro-skipper/segment-editor-mobile/releases/"
            ).setResultListener(object : JSONExecutor.ResultListener {
                override fun onResults(result: String?) {
                    result?.let { parseUpdateJSON(it) }
                }
                override fun onException(e: Exception) {

                }
            })
        }
    }

    fun installDownload(apkUrl: String?) {
        if (apkUrl.isNullOrEmpty()) return
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val apk = File(browserActivity.externalCacheDir, apkUrl.substringAfterLast(File.separator))
            try {
                URL(apkUrl).openStream().use { stream ->
                    FileOutputStream(apk).use { stream.copyTo(it) }
                }
            } catch (fnf: FileNotFoundException) {
                configureGit()
                return@launch
            }
            if (!apk.name.lowercase().endsWith(".apk")) apk.delete()
            try {
                browserActivity.run {
                    val apkUri = FileProvider.getUriForFile(
                        browserActivity.applicationContext,
                        browserActivity.packageName + ".provider",
                        apk
                    )

                    applicationContext.contentResolver.openInputStream(apkUri).use { apkStream ->
                        val session = with (applicationContext.packageManager.packageInstaller) {
                            val params = PackageInstaller.SessionParams(
                                PackageInstaller.SessionParams.MODE_FULL_INSTALL
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                params.setRequireUserAction(
                                    PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                                )
                            }
                            openSession(createSession(params))
                        }
                        val document = DocumentFile.fromSingleUri(applicationContext, apkUri)
                            ?: throw IOException(browserActivity.getString(R.string.install_broken))
                        session.openWrite("NAME", 0, document.length()).use { sessionStream ->
                            apkStream?.copyTo(sessionStream)
                            session.fsync(sessionStream)
                        }
                        val pi = PendingIntent.getBroadcast(
                            applicationContext, Random.nextInt(),
                            Intent(applicationContext, UpdateReceiver::class.java),
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                            else
                                PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        session.commit(pi.intentSender)
                    }
                }
            } catch (ex: SecurityException) {

            } catch (ex: IOException) {

            }
        }
    }

    private fun requestDownload(apkUrl: String) {

        if (browserActivity.packageManager.canRequestPackageInstalls()) {
            installDownload(apkUrl)
        } else {
            browserActivity.onRequestInstall.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = String.format("package:%s", browserActivity.packageName).toUri()
                }
            )
        }
    }

    private fun parseUpdateJSON(result: String) {
        try {
            val jsonObject = JSONTokener(result).nextValue() as JSONObject
            val lastCommit = (jsonObject["name"] as String).substring(
                browserActivity.getString(R.string.app_name).length + 1
            ).also { if (it.length > 7) it.substring(0, 7) }
            val assets = jsonObject["assets"] as JSONArray
            val asset = assets[0] as JSONObject
            isUpdateAvailable = BuildConfig.COMMIT != lastCommit
            if (isUpdateAvailable) {
                updateUrl = asset["browser_download_url"] as String
                updateListener?.onUpdateFound()
            }
        } catch (e: JSONException) {

        }
    }

    fun onUpdateRequested() {
        updateUrl?.let { requestDownload(it) }
    }

    fun setUpdateListener(listener: UpdateListener?) {
        updateListener = listener
    }

    interface UpdateListener {
        fun onUpdateFound()
    }
}
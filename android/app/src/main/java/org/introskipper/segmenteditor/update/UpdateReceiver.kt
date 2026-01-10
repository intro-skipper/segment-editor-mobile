package org.introskipper.segmenteditor.update

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.core.content.IntentSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.R
import java.net.URISyntaxException

inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? {
    return IntentCompat.getParcelableExtra(this, name, T::class.java)
}

class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        intent.setPackage(context.packageName)
        intent.flags = 0
        intent.data = null
        if (Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            CoroutineScope(Dispatchers.IO).launch {
                if (context.isAppRunning) {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(-1)
                }
            }
        } else {
            when (intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            )) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)?.let { intent ->
                        try {
                            startLauncherActivity(
                                context, Intent.parseUri(
                                    intent.toUri(0),
                                    Intent.URI_ALLOW_UNSAFE or Intent.URI_INTENT_SCHEME
                                )
                            )
                        } catch (_: URISyntaxException) {
                            context.toast(R.string.install_broken)
                        }
                    } ?: context.toast(R.string.install_rejected)
                }
                PackageInstaller.STATUS_FAILURE_BLOCKED -> { context.toast(R.string.install_blocked) }
                PackageInstaller.STATUS_FAILURE_STORAGE -> { context.toast(R.string.install_storage) }
                PackageInstaller.STATUS_FAILURE_CONFLICT -> { context.toast(R.string.install_conflict) }
                PackageInstaller.STATUS_FAILURE_ABORTED -> { context.toast(R.string.install_aborted) }
                PackageInstaller.STATUS_SUCCESS -> { }
                else -> {
                    val error = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    if (error?.contains("Session was abandoned") != true)
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startLauncherActivity(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun startSanitized(context: Context, intent: Intent) {
        context.startActivity(IntentSanitizer.Builder().apply {
            intent.action?.let { allowAction(it) }
            allowExtra(PackageInstaller.EXTRA_SESSION_ID) { true }
            allowAnyComponent()
            allowPackage { true }
        }.build().sanitizeByFiltering(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun Context.toast( res: Int) {
        MainScope().launch {
            Toast.makeText(
                this@toast, getString(res), Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val Context.isAppRunning: Boolean get() {
        with (getSystemService(ACTIVITY_SERVICE) as ActivityManager) {
            return runningAppProcesses.any { packageName.equals(it.processName) }
        }
    }
}

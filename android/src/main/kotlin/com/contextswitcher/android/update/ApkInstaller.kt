package com.contextswitcher.android.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File

/// Installs a downloaded APK over the running app through a package-installer
/// session. The first time, Android asks to allow ContextSwitcher to install
/// apps and to confirm; once it has installed itself, Android 12 and later let
/// later updates through without a confirmation.
// [impl->dsn~android-update-hint~3]
object ApkInstaller {

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Mutable: the installer adds the status extras to this intent.
            val result = PendingIntent.getBroadcast(context, sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(result.intentSender)
        }
    }
}

/// The installer's answer: its confirmation screen when the user has to
/// agree, a message when the install failed. Success needs nothing — Android
/// restarts the app on the new version.
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Toast.makeText(context,
                "Update not installed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}",
                Toast.LENGTH_LONG).show()
        }
    }
}

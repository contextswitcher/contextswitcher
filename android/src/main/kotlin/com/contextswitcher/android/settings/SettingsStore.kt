package com.contextswitcher.android.settings

import android.content.Context

private const val PREFS_NAME = "contextswitcher_settings"
private const val KEY_URL = "repo_url"
private const val KEY_USERNAME = "username"
private const val KEY_TOKEN = "token"
private const val KEY_UPDATE_TOKEN = "update_token"
private const val KEY_SSH_PRIVATE_KEY = "ssh_private_key"
private const val KEY_SKIP_PERMISSIONS = "skip_permissions"

/// The task backup repo's clone URL plus HTTPS credentials
/// (`UsernamePasswordCredentialsProvider`; any value works for username with
/// a GitHub PAT as the token). [updateToken] reads the ContextSwitcher
/// repository for the update hint; blank falls back to [token] — a
/// fine-grained PAT has a single owner, so a task repo under another owner
/// needs a second one.
data class RepoSettings(val url: String, val username: String, val token: String, val updateToken: String = "") {
    val effectiveUpdateToken: String get() = updateToken.ifBlank { token }
}

/// App-private `SharedPreferences` for the repo settings.
// ponytail: the token sits in plain SharedPreferences (app-private storage,
// but unencrypted — readable on a rooted device or from a backup); known
// ceiling, move to Android Keystore-backed EncryptedSharedPreferences before
// a wider release.
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /// The saved settings, or `null` when no repo URL is configured yet —
    /// the signal the app uses to show the settings screen first.
    fun load(): RepoSettings? {
        val url = prefs.getString(KEY_URL, null)
        if (url.isNullOrBlank()) {
            return null
        }
        return RepoSettings(
            url,
            prefs.getString(KEY_USERNAME, "") ?: "",
            prefs.getString(KEY_TOKEN, "") ?: "",
            prefs.getString(KEY_UPDATE_TOKEN, "") ?: "",
        )
    }

    fun save(settings: RepoSettings) {
        prefs.edit()
            .putString(KEY_URL, settings.url)
            .putString(KEY_USERNAME, settings.username)
            .putString(KEY_TOKEN, settings.token)
            .putString(KEY_UPDATE_TOKEN, settings.updateToken)
            .apply()
    }

    /// The pasted OpenSSH private key for [com.contextswitcher.android.ssh.SshjCommandRunner]
    /// (MADR 0027), or `""` when none is set yet — same app-private,
    /// unencrypted storage and Keystore-later ceiling as the repo token
    /// above. No passphrase support (ceiling: a passphrase-protected key
    /// fails auth).
    fun loadSshPrivateKey(): String = prefs.getString(KEY_SSH_PRIVATE_KEY, "") ?: ""

    fun saveSshPrivateKey(pem: String) {
        prefs.edit().putString(KEY_SSH_PRIVATE_KEY, pem).apply()
    }

    /// The last choice of *Skip permission prompts* when starting Claude from
    /// the phone — the desktop's `claudeAuto` setting, remembered per device.
    fun loadSkipPermissions(): Boolean = prefs.getBoolean(KEY_SKIP_PERMISSIONS, false)

    fun saveSkipPermissions(skip: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_PERMISSIONS, skip).apply()
    }
}

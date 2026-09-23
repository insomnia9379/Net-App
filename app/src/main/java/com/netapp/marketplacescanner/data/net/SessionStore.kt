package com.netapp.marketplacescanner.data.net

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

/**
 * Persists the Facebook session captured via the in-app WebView login plus a
 * few scan settings. The cookie string is what authenticates background scans;
 * we deliberately keep it on-device only and never transmit it anywhere but
 * facebook.com.
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val COOKIE = stringPreferencesKey("fb_cookie")
        val USER_AGENT = stringPreferencesKey("user_agent")
        val LOGGED_IN = booleanPreferencesKey("logged_in")
        val CAPTURED_AT = longPreferencesKey("captured_at")
        val INTERVAL_MIN = longPreferencesKey("interval_min")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOGGED_IN] ?: false }

    val intervalMinutes: Flow<Long> =
        context.dataStore.data.map { it[Keys.INTERVAL_MIN] ?: DEFAULT_INTERVAL_MIN }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.WIFI_ONLY] ?: false }

    val capturedAt: Flow<Long> = context.dataStore.data.map { it[Keys.CAPTURED_AT] ?: 0L }

    suspend fun cookie(): String? = context.dataStore.data.first()[Keys.COOKIE]

    suspend fun userAgent(): String =
        context.dataStore.data.first()[Keys.USER_AGENT] ?: DEFAULT_USER_AGENT

    suspend fun saveSession(cookie: String, userAgent: String) {
        context.dataStore.edit {
            it[Keys.COOKIE] = cookie
            it[Keys.USER_AGENT] = userAgent
            it[Keys.LOGGED_IN] = cookie.contains("c_user")
            it[Keys.CAPTURED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.COOKIE)
            it[Keys.LOGGED_IN] = false
            it[Keys.CAPTURED_AT] = 0L
        }
    }

    suspend fun setInterval(minutes: Long) {
        context.dataStore.edit { it[Keys.INTERVAL_MIN] = minutes }
    }

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = value }
    }

    companion object {
        const val DEFAULT_INTERVAL_MIN = 30L
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

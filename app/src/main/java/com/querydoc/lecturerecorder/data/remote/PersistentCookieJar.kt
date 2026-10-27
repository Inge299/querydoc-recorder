package com.querydoc.lecturerecorder.data.remote

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native clients must keep the platform session cookie. Storing it in private
 * app preferences is deliberately simple; the value is a bearer credential,
 * so the app disables Android backups.
 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this) {
            val entries = readEntries()
            for (cookie in cookies) {
                entries.removeAll { it.first == url.host && it.second.name == cookie.name }
                if (cookie.expiresAt > System.currentTimeMillis()) entries.add(url.host to cookie)
            }
            writeEntries(entries)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(this) {
            val now = System.currentTimeMillis()
            val entries = readEntries()
            // Only drop expired cookies. Filtering by the current host here
            // would erase the QueryDoc session cookie when OkHttp uploads to
            // the S3 host, because S3 cookies and API cookies share this jar.
            val unexpired = entries.filter { (_, cookie) -> cookie.expiresAt > now }
            if (unexpired.size != entries.size) writeEntries(unexpired)
            return unexpired
                .filter { (host, cookie) -> host == url.host || cookie.matches(url) }
                .map { it.second }
        }
    }

    fun hasSessionCookie(): Boolean = synchronized(this) {
        readEntries().any { it.second.name == "token" && it.second.expiresAt > System.currentTimeMillis() }
    }

    fun cookieValue(name: String): String? = synchronized(this) {
        val now = System.currentTimeMillis()
        readEntries().firstOrNull { it.second.name == name && it.second.expiresAt > now }?.second?.value
    }

    fun clear() = synchronized(this) { prefs.edit().remove(KEY).apply() }

    private fun readEntries(): MutableList<Pair<String, Cookie>> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val host = item.optString("host")
                val cookie = Cookie.parse(host.asHttpUrl() ?: return@mapNotNull null, item.optString("cookie"))
                    ?: return@mapNotNull null
                host to cookie
            }.toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun writeEntries(entries: List<Pair<String, Cookie>>) {
        val array = JSONArray()
        entries.forEach { (host, cookie) ->
            array.put(JSONObject().put("host", host).put("cookie", cookie.toString()))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun String.asHttpUrl(): HttpUrl? = toHttpUrlOrNull() ?: "https://$this".toHttpUrlOrNull()

    private companion object {
        const val PREFS = "querydoc_session"
        const val KEY = "cookies_v1"
    }
}

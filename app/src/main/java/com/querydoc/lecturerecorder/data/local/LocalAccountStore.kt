package com.querydoc.lecturerecorder.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The account that owns local recordings and cached conspects.
 *
 * Local data is intentionally kept across logout, but it must never be shown
 * or uploaded under another account. Entries written before this store was
 * introduced have no owner and are treated as hidden/foreign.
 */
object LocalAccountStore {
    private const val PREFS = "querydoc_local_account"
    private const val KEY_USER_ID = "user_id"

    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId.asStateFlow()

    fun current(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, "")
            .orEmpty()
        if (_userId.value != stored) _userId.value = stored
        return stored
    }

    fun set(context: Context, userId: String) {
        val value = userId.trim()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, value)
            .apply()
        _userId.value = value
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USER_ID)
            .apply()
        _userId.value = ""
    }
}

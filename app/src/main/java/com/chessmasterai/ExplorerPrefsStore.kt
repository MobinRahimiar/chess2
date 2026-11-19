package com.chessmasterai

import android.content.Context

/** SharedPreferences-backed cache store. Holds only public aggregate statistics. */
class PrefsExplorerStore(context: Context) : ExplorerCacheStore {
    private val prefs = context.applicationContext.getSharedPreferences("chess_master_explorer_cache", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString("entries", null)
    override fun write(text: String) { prefs.edit().putString("entries", text).apply() }
}

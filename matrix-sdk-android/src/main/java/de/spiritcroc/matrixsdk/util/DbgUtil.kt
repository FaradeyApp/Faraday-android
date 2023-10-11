package de.spiritcroc.matrixsdk.util

import android.content.Context
import androidx.preference.PreferenceManager
import org.matrix.android.sdk.BuildConfig
import timber.log.Timber

object DbgUtil {
    const val DBG_READ_MARKER = "DBG_READ_MARKER"
    const val DBG_SHOW_READ_TRACKING = "DBG_SHOW_READ_TRACKING"
    const val DBG_READ_RECEIPTS = "DBG_READ_RECEIPTS"
    const val DBG_TIMELINE_CHUNKS = "DBG_TIMELINE_CHUNKS"
    const val DBG_SHOW_DISPLAY_INDEX = "DBG_SHOW_DISPLAY_INDEX"
    const val DBG_VIEW_PAGER = "DBG_VIEW_PAGER"
    const val DBG_VIEW_PAGER_VISUALS = "DBG_VIEW_PAGER_VISUALS"
    const val DBG_SHOW_DUPLICATE_READ_RECEIPTS = "DBG_SHOW_DUPLICATE_READ_RECEIPTS"

    private val prefs = HashMap<String, Boolean>()

    val ALL_PREFS = arrayOf(
            DBG_READ_MARKER,
            DBG_SHOW_READ_TRACKING,
            DBG_READ_RECEIPTS,
            DBG_TIMELINE_CHUNKS,
            DBG_SHOW_DISPLAY_INDEX,
            DBG_VIEW_PAGER,
            DBG_VIEW_PAGER_VISUALS,
            DBG_SHOW_DUPLICATE_READ_RECEIPTS,
    )

    fun load(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        ALL_PREFS.forEach {
            prefs[it] = sp.getBoolean(it, false)
        }
    }

    fun onPreferenceChanged(context: Context, key: String, value: Boolean) {
        if (key !in ALL_PREFS) {
            Timber.e("Trying to set unsupported preference $key to $value")
            return
        }
        prefs[key] = value
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
    }

    fun isDbgEnabled(key: String): Boolean {
        return prefs[key] ?: BuildConfig.DEBUG
        //return prefs[key] == true
    }
}

package im.vector.app.features.settings

import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import de.spiritcroc.matrixsdk.util.DbgUtil
import im.vector.app.R
import im.vector.app.core.preference.VectorSwitchPreference
import javax.inject.Inject

class VectorSettingsScDebuggingFragment @Inject constructor(
        //private val vectorPreferences: VectorPreferences
) : VectorSettingsBaseFragment() {

    override var titleRes = R.string.settings_sc_debugging
    override val preferenceXmlRes = R.xml.vector_settings_sc_debugging

    companion object {
        const val SC_DEBUGGING_CATEGORY_LOGS = "SC_DEBUGGING_CATEGORY_LOGS"
        const val SC_DEBUGGING_CATEGORY_VISUALS = "SC_DEBUGGING_CATEGORY_VISUALS"
    }

    data class DbgPref(val key: String, @StringRes val stringRes: Int)
    private val dbgLoggingPrefs = arrayOf(
            DbgPref(DbgUtil.DBG_TIMELINE_CHUNKS, R.string.settings_sc_dbg_timeline_chunks),
            DbgPref(DbgUtil.DBG_READ_MARKER, R.string.settings_sc_dbg_read_marker),
            DbgPref(DbgUtil.DBG_READ_RECEIPTS, R.string.settings_sc_dbg_read_receipts),
            DbgPref(DbgUtil.DBG_VIEW_PAGER, R.string.settings_sc_dbg_view_pager),
    )
    private val dbgVisualsPrefs = arrayOf(
            DbgPref(DbgUtil.DBG_SHOW_DISPLAY_INDEX, R.string.settings_sc_dbg_show_display_index),
            DbgPref(DbgUtil.DBG_SHOW_READ_TRACKING, R.string.settings_sc_dbg_show_read_tracking),
            DbgPref(DbgUtil.DBG_VIEW_PAGER_VISUALS, R.string.settings_sc_dbg_view_pager_visuals),
            DbgPref(DbgUtil.DBG_SHOW_DUPLICATE_READ_RECEIPTS, R.string.settings_sc_dbg_show_duplicate_read_receipts),
    )
    val dbgPrefs = dbgLoggingPrefs + dbgVisualsPrefs

    private fun addScDbgPrefs(prefs: Array<DbgPref>, group: PreferenceGroup) {
        prefs.forEach { dbgPref ->
            var pref: VectorSwitchPreference? = findPreference(dbgPref.key)
            if (pref == null) {
                pref = VectorSwitchPreference(requireContext())
                pref.key = dbgPref.key
                pref.title = getString(dbgPref.stringRes)
                group.addPreference(pref)
            }
            pref.isChecked = DbgUtil.isDbgEnabled(pref.key)
            pref.onPreferenceChangeListener = preferenceChangeListener
        }
    }

    override fun bindPref() {
        findPreference<PreferenceGroup>(SC_DEBUGGING_CATEGORY_LOGS)?.let { addScDbgPrefs(dbgLoggingPrefs, it) }
        findPreference<PreferenceGroup>(SC_DEBUGGING_CATEGORY_VISUALS)?.let { addScDbgPrefs(dbgVisualsPrefs, it) }
    }

    private val preferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
        if (newValue is Boolean && dbgPrefs.any { preference.key == it.key }) {
            DbgUtil.onPreferenceChanged(requireContext(), preference.key as String, newValue)
        }
        true
    }
}

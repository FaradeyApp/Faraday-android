package de.spiritcroc.menu

import android.content.res.Resources
import android.view.Menu
import android.view.MenuItem
import android.view.View

object ArrayOptionsMenuHelper {
    private data class OptionsMenuMetadata(
            val menuId: Int,
            val entriesId: Int,
            val valuesId: Int,
            val values: HashMap<Int, String> = HashMap(),
            var action: (String) -> Boolean = { false }
    )

    private val menuLookup = HashMap<Int, OptionsMenuMetadata>()

    fun createSubmenu(resources: Resources,
                      menuItem: MenuItem,
                      groupId: Int,
                      entriesId: Int,
                      valuesId: Int,
                      initialValue: String,
                      sortFunction: (List<Pair<String, String>>) -> List<Pair<String, String>> = { it },
                      action: (String) -> Boolean) {
        val menuId = menuItem.itemId
        var metadata = menuLookup[menuId]
        if (metadata == null || metadata.entriesId != entriesId || metadata.valuesId != valuesId) {
            metadata = OptionsMenuMetadata(menuId, entriesId, valuesId)
            menuLookup[menuId] = metadata
        }
        metadata.action = action

        menuItem.subMenu?.apply {
            clear()
            val themeEntries = resources.getStringArray(entriesId)
            val themeValues = resources.getStringArray(valuesId)
            sortFunction(themeEntries.zip(themeValues)).forEach { theme ->
                val itemId = View.generateViewId()
                val item = add(groupId, itemId, Menu.NONE, theme.first).apply {
                    isCheckable = true
                }
                metadata.values[itemId] = theme.second
                if (initialValue == theme.second) {
                    item.isChecked = true
                }
            }
        }
    }

    fun handleSubmenu(item: MenuItem, vararg menuIds: Int): Boolean {
        menuIds.forEach { menuId ->
            val metadata = menuLookup[menuId] ?: return@forEach
            val value = metadata.values[item.itemId] ?: return@forEach
            if (metadata.action(value)) {
                return true
            }
        }
        return false
    }
}

fun MenuItem.toggleExec(withNewVal: (Boolean) -> Boolean): Boolean {
    val shouldCheck = !isChecked
    return if (withNewVal(shouldCheck)) {
        isChecked = shouldCheck
        true
    } else {
        false
    }
}

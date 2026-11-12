package app.orchard.tv.topshelf

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

/** Local presentation choices only; no writes or broadcasts to the content owner's history. */
class ShelfPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("top_shelf_choices", Context.MODE_PRIVATE)
    var disabledApps by mutableStateOf(prefs.getStringSet("disabled", emptySet())!!.toSet()); private set
    var hiddenItems by mutableStateOf(prefs.getStringSet("hidden", emptySet())!!.toSet()); private set

    fun accepts(program: ShelfProgram) = program.packageName !in disabledApps && key(program) !in hiddenItems
    fun hide(program: ShelfProgram) {
        hiddenItems = hiddenItems + key(program)
        prefs.edit().putStringSet("hidden", hiddenItems).apply()
    }
    fun disable(packageName: String) {
        disabledApps = disabledApps + packageName
        prefs.edit().putStringSet("disabled", disabledApps).apply()
    }
    fun restore() {
        disabledApps = emptySet(); hiddenItems = emptySet()
        prefs.edit().remove("disabled").remove("hidden").apply()
    }
    private fun key(program: ShelfProgram) = MessageDigest.getInstance("SHA-256")
        .digest(program.stableKey.toByteArray()).joinToString("") { "%02x".format(it) }
}

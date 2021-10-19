package cc.warlock.warlock3.app.views.settings

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.preferencesFile
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.hocon.toHocon

class MacroRegistry(
    private val config: Config
) {
    private val _macros = mutableStateMapOf<String, String>()
    val macros: Map<String, String> = _macros

    init {
        _macros.putAll(config[ClientSpec.macros])
    }

    fun saveMacro(name: String, value: String) {
        _macros += (name to value)
        config[ClientSpec.macros] = _macros
        config.toHocon.toFile(preferencesFile)
    }

    fun deleteMacro(name: String) {
        _macros -= name
        config[ClientSpec.macros] = _macros
        config.toHocon.toFile(preferencesFile)
    }
}
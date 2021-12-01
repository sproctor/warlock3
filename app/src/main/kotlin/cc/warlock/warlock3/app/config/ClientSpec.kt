package cc.warlock.warlock3.app.config

import cc.warlock.warlock3.app.model.Account
import cc.warlock.warlock3.app.model.GameCharacter
import cc.warlock.warlock3.core.highlights.Highlight
import cc.warlock.warlock3.core.text.StyleDefinition
import com.uchuhimo.konf.ConfigSpec

object ClientSpec : ConfigSpec("client") {
    val width by optional(640)
    val height by optional(480)

    val accounts by optional<List<Account>>(emptyList())
    val characters by optional<List<GameCharacter>>(emptyList())

    val variables by optional<Map<String, Map<String, String>>>(emptyMap())

    val scriptDirectory by optional(System.getProperty("user.home") + "/.warlock3/scripts")

    val maxTypeAhead by optional(1)

    val globalMacros by optional<Map<String, String>>(emptyMap())
    val characterMacros by optional<Map<String, Map<String, String>>>(emptyMap())

    val globalHighlights by optional<List<Highlight>>(emptyList())
    val characterHighlights by optional<Map<String, List<Highlight>>>(emptyMap())

    val styles by optional<Map<String, Map<String, StyleDefinition>>>(emptyMap())
}
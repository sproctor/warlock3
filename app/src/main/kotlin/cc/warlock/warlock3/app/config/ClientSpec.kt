package cc.warlock.warlock3.app.config

import cc.warlock.warlock3.app.model.Account
import cc.warlock.warlock3.app.model.GameCharacter
import com.uchuhimo.konf.ConfigSpec

object ClientSpec : ConfigSpec("client") {
    val width by optional(640)
    val height by optional(480)

    val accounts by optional<List<Account>>(emptyList())
    val characters by optional<List<GameCharacter>>(emptyList())

    val variables by optional<Map<String, Map<String, String>>>(emptyMap())

    val maxTypeAhead by optional(1)

    val globalMacros by optional<Map<String, String>>(emptyMap())
    val characterMacros by optional<Map<String, Map<String, String>>>(emptyMap())
}
package cc.warlock.warlock3.app.config

import com.uchuhimo.konf.ConfigSpec

object ClientSpec : ConfigSpec("client") {
    val width by optional(640)
    val height by optional(480)

    val variables by optional<Map<String, String>>(emptyMap())

    val maxTypeAhead by optional(1)

    val macros by optional<Map<String, String>>(emptyMap())
}
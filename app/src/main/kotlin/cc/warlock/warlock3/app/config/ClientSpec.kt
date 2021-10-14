package cc.warlock.warlock3.app.config

import com.uchuhimo.konf.ConfigSpec

object ClientSpec : ConfigSpec("client") {
    val width by optional(640)
    val height by optional(480)
}
package warlockfe.warlock3.scripting.lua

import kotlinx.coroutines.flow.MutableStateFlow
import warlockfe.warlock3.core.client.MudScriptOffer
import warlockfe.warlock3.core.client.ScriptableClient
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.scripting.wsl.FakeWarlockClient
import kotlin.time.Duration

/** A [FakeWarlockClient] a script may drive, the way the telnet client can be. */
class FakeScriptableClient :
    FakeWarlockClient(),
    ScriptableClient {
    override val mudScript = MutableStateFlow<MudScriptOffer?>(null)
    override val handsShown = MutableStateFlow(true)

    /** The vitals as last set: id to (percent, text). */
    val vitals = LinkedHashMap<String, Pair<Int, String?>>()

    override fun setLeftHand(item: String?) {
        leftHand.value = item
    }

    override fun setRightHand(item: String?) {
        rightHand.value = item
    }

    override fun setSpellHand(spell: String?) {
        spellHand.value = spell
    }

    override fun showHands(shown: Boolean) {
        handsShown.value = shown
    }

    override suspend fun setVital(
        id: String,
        percent: Int,
        text: String?,
    ) {
        vitals[id] = percent to text
    }

    override suspend fun clearVitals() {
        vitals.clear()
    }

    val sentGmcp = mutableListOf<Pair<String, String>>()
    val flashes = mutableListOf<List<Any>>()

    override fun flashBackground(
        window: String,
        color: WarlockColor,
        total: Duration,
        fadeIn: Duration,
        fadeOut: Duration,
    ) {
        flashes += listOf(window, color, total, fadeIn, fadeOut)
    }

    override fun setRoundTime(endSeconds: Long?) {
        roundTimeEnd.value = endSeconds
    }

    override fun setCastTime(endSeconds: Long?) {
        castTimeEnd.value = endSeconds
    }

    override suspend fun sendGmcp(
        name: String,
        data: String,
    ) {
        sentGmcp += name to data
    }
}

package warlockfe.warlock3.compose.util

import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.core.text.FontConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressBarSkinFontTest {
    private val shared =
        mapOf(
            "progressBar" to SkinObject(fontFamily = "Shared", fontSize = 9f, fontWeight = 400),
        )

    @Test
    fun aSkinThatSaysNothingContributesNoFont() {
        assertNull(progressBarSkinFont(skinObject = null, skin = emptyMap()))
        assertNull(progressBarSkinFont(skinObject = SkinObject(), skin = emptyMap()))
    }

    // A skin can size every bar in one place.
    @Test
    fun theSharedEntryAppliesToABarThatSaysNothingItself() {
        assertEquals(
            FontConfig(family = "Shared", size = 9f, weight = 400),
            progressBarSkinFont(skinObject = SkinObject(), skin = shared),
        )
    }

    // Skins and the server disagree about case and always have - the game asks for `healthBar`
    // while the skin calls it `HealthBar`, and the real client skins it anyway - so the shared entry
    // is found whatever case it is written in.
    @Test
    fun theSharedEntryIsFoundWhateverCaseItIsWrittenIn() {
        assertEquals(
            FontConfig(family = "Shared", size = 9f, weight = 400),
            progressBarSkinFont(
                skinObject = SkinObject(),
                skin = mapOf("PrOgReSsBaR" to SkinObject(fontFamily = "Shared", fontSize = 9f, fontWeight = 400)),
            ),
        )
    }

    // And still say something different about one of them.
    @Test
    fun theBarsOwnEntryWinsOverTheSharedOne() {
        assertEquals(
            FontConfig(family = "Own", size = 11f, weight = 700),
            progressBarSkinFont(
                skinObject = SkinObject(fontFamily = "Own", fontSize = 11f, fontWeight = 700),
                skin = shared,
            ),
        )
    }

    // Field by field, matching how withFont merges: a bar that only resizes keeps the rest.
    @Test
    fun aBarThatSetsOnlyASizeKeepsTheSharedFamilyAndWeight() {
        assertEquals(
            FontConfig(family = "Shared", size = 11f, weight = 400),
            progressBarSkinFont(skinObject = SkinObject(fontSize = 11f), skin = shared),
        )
    }

    @Test
    fun aBarsEntryAloneIsEnoughWithNoSharedEntry() {
        assertEquals(
            FontConfig(size = 11f),
            progressBarSkinFont(skinObject = SkinObject(fontSize = 11f), skin = emptyMap()),
        )
    }
}

package warlockfe.warlock3.app.updater

import com.seanproctor.potassium.updater.UpdateFile
import com.seanproctor.potassium.updater.UpdateInfo
import com.seanproctor.potassium.updater.UpdateLevel
import com.seanproctor.potassium.updater.UpdateResult
import com.seanproctor.potassium.updater.exception.NetworkException
import warlockfe.warlock3.core.prefs.ReleaseChannelSetting
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelUpdaterTest {
    @Test
    fun `stable only checks stable`() {
        assertEquals(
            listOf(STABLE_CHANNEL),
            ReleaseChannelSetting.STABLE.channelsToCheck("3.1.0-beta.4"),
        )
    }

    @Test
    fun `beta checks beta and stable`() {
        assertEquals(
            listOf(BETA_CHANNEL, STABLE_CHANNEL),
            ReleaseChannelSetting.BETA.channelsToCheck("3.1.0"),
        )
    }

    @Test
    fun `alpha checks every channel`() {
        assertEquals(
            listOf(ALPHA_CHANNEL, BETA_CHANNEL, STABLE_CHANNEL),
            ReleaseChannelSetting.ALPHA.channelsToCheck("3.1.0"),
        )
    }

    @Test
    fun `current follows the running version's channel`() {
        val setting = ReleaseChannelSetting.CURRENT
        assertEquals(listOf(STABLE_CHANNEL), setting.channelsToCheck("3.1.0"))
        assertEquals(listOf(BETA_CHANNEL, STABLE_CHANNEL), setting.channelsToCheck("3.1.0-beta.21"))
        assertEquals(
            listOf(ALPHA_CHANNEL, BETA_CHANNEL, STABLE_CHANNEL),
            setting.channelsToCheck("3.1.0-alpha.2"),
        )
    }

    @Test
    fun `build metadata does not hide the prerelease identifier`() {
        val setting = ReleaseChannelSetting.CURRENT
        assertEquals(listOf(BETA_CHANNEL, STABLE_CHANNEL), setting.channelsToCheck("3.1.0-beta+build.7"))
        assertEquals(
            listOf(ALPHA_CHANNEL, BETA_CHANNEL, STABLE_CHANNEL),
            setting.channelsToCheck("3.1.0-alpha.2+sha.9f3c1d0"),
        )
        assertEquals(listOf(STABLE_CHANNEL), setting.channelsToCheck("3.1.0+build.7"))
    }

    @Test
    fun `an unrecognized prerelease is treated as stable`() {
        // Only alpha and beta are published; anything else shouldn't widen the search.
        assertEquals(listOf(STABLE_CHANNEL), ReleaseChannelSetting.CURRENT.channelsToCheck("3.1.0-rc.1"))
        assertEquals(listOf(STABLE_CHANNEL), ReleaseChannelSetting.CURRENT.channelsToCheck("0.0.0-dev"))
    }

    @Test
    fun `the highest offered version wins`() {
        val result =
            bestUpdate(
                listOf(
                    available("3.2.0-beta.1"),
                    available("3.2.0"),
                    available("3.3.0-alpha.1"),
                ),
            )
        assertEquals("3.3.0-alpha.1", (result as UpdateResult.Available).info.version)
    }

    @Test
    fun `a stable release outranks a prerelease of the same version`() {
        val result = bestUpdate(listOf(available("3.2.0-beta.9"), available("3.2.0")))
        assertEquals("3.2.0", (result as UpdateResult.Available).info.version)
    }

    @Test
    fun `a channel with no releases does not mask an answer from another`() {
        // Asking for alphas on a repo that has never published one fails that lookup.
        val noAlphas = UpdateResult.Error(NetworkException("no alpha tag"))
        assertEquals(UpdateResult.NotAvailable, bestUpdate(listOf(noAlphas, UpdateResult.NotAvailable)))
        val result = bestUpdate(listOf(noAlphas, available("3.2.0")))
        assertEquals("3.2.0", (result as UpdateResult.Available).info.version)
    }

    @Test
    fun `an error survives when no channel could answer`() {
        val failure = UpdateResult.Error(NetworkException("offline"))
        assertEquals(failure, bestUpdate(listOf(failure, UpdateResult.Error(NetworkException("offline")))))
    }

    private fun available(version: String): UpdateResult.Available {
        val file = UpdateFile(url = "warlock-$version.deb", sha512 = "", size = 0, fileName = "warlock-$version.deb")
        return UpdateResult.Available(
            info = UpdateInfo(version = version, releaseDate = "", files = listOf(file), currentFile = file),
            level = UpdateLevel.MINOR,
        )
    }
}

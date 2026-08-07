package warlockfe.warlock3.app.updater

import com.seanproctor.potassium.updater.DownloadProgress
import com.seanproctor.potassium.updater.PotassiumUpdater
import com.seanproctor.potassium.updater.UpdateInfo
import com.seanproctor.potassium.updater.UpdateResult
import com.seanproctor.potassium.updater.Version
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.ReleaseChannelSetting
import java.io.File

/** Potassium/electron-updater channel names. Stable is spelled "latest" in the manifest files. */
internal const val ALPHA_CHANNEL = "alpha"
internal const val BETA_CHANNEL = "beta"
internal const val STABLE_CHANNEL = "latest"

/**
 * The channel a version string belongs to: its first semver pre-release identifier, which is how
 * both potassium and electron-updater classify a tag (`3.1.0-beta.8` -> `beta`). Anything we don't
 * publish under counts as stable.
 */
internal fun channelOfVersion(version: String): String =
    when (version.substringAfter('-', missingDelimiterValue = "").substringBefore('.')) {
        ALPHA_CHANNEL -> ALPHA_CHANNEL
        BETA_CHANNEL -> BETA_CHANNEL
        else -> STABLE_CHANNEL
    }

/**
 * The channels an update check covers. Each channel includes everything more stable than itself:
 * alpha takes the highest version published anywhere, beta takes the higher of the newest beta and
 * the newest stable, and stable only ever sees stable releases. Without that, a beta build would
 * sit on the beta line forever, never picking up the stable release that supersedes it.
 */
internal fun ReleaseChannelSetting.channelsToCheck(currentVersion: String): List<String> =
    when (this) {
        ReleaseChannelSetting.CURRENT -> channelsUpFrom(channelOfVersion(currentVersion))
        ReleaseChannelSetting.ALPHA -> channelsUpFrom(ALPHA_CHANNEL)
        ReleaseChannelSetting.BETA -> channelsUpFrom(BETA_CHANNEL)
        ReleaseChannelSetting.STABLE -> channelsUpFrom(STABLE_CHANNEL)
    }

private fun channelsUpFrom(channel: String): List<String> =
    when (channel) {
        ALPHA_CHANNEL -> listOf(ALPHA_CHANNEL, BETA_CHANNEL, STABLE_CHANNEL)
        BETA_CHANNEL -> listOf(BETA_CHANNEL, STABLE_CHANNEL)
        else -> listOf(STABLE_CHANNEL)
    }

/**
 * Offers the highest version across several release channels.
 *
 * A [PotassiumUpdater] resolves exactly one channel: `beta` finds the newest beta tag in the GitHub
 * releases feed and never looks at stable releases, and `latest` follows GitHub's latest-release
 * redirect, which skips prereleases. Our channel setting is cumulative instead (see
 * [channelsToCheck]), so this runs one updater per channel and keeps whichever offers the greatest
 * version.
 */
internal class ChannelUpdater(
    private val updaters: List<PotassiumUpdater>,
) {
    /**
     * Downloading and installing run against a single updater. Neither depends on the channel - the
     * artifact URLs come from the [UpdateInfo] the check produced - and `installAndRestart` records
     * the version that the same instance downloaded, so both have to be the same updater.
     */
    private val installer = updaters.first()

    val currentVersion: String get() = installer.currentVersion

    fun isUpdateSupported(): Boolean = installer.isUpdateSupported()

    suspend fun checkForUpdates(): UpdateResult = bestUpdate(updaters.map { it.checkForUpdates() })

    fun downloadUpdate(info: UpdateInfo): Flow<DownloadProgress> = installer.downloadUpdate(info)

    fun installAndRestart(installerFile: File) = installer.installAndRestart(installerFile)
}

/**
 * The highest version offered across a set of per-channel results.
 *
 * A channel that has never been published fails its lookup - the releases feed holds no matching
 * tag - which is routine for alpha on a repo that only ships betas. So an error is only reported
 * when no channel managed to answer at all; otherwise it is a channel that simply isn't there.
 */
internal fun bestUpdate(results: List<UpdateResult>): UpdateResult =
    results
        .filterIsInstance<UpdateResult.Available>()
        .maxByOrNull { Version.fromString(it.info.version) }
        ?: results.firstOrNull { it is UpdateResult.NotAvailable }
        ?: results.firstOrNull { it is UpdateResult.Error }
        ?: UpdateResult.NotAvailable

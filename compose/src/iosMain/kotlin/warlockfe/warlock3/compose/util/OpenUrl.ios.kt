package warlockfe.warlock3.compose.util

import com.eygraber.uri.Uri
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: Uri) {
    val nsUrl = NSURL.URLWithString(url.toString()) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

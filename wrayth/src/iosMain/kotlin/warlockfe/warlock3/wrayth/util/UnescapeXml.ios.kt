package warlockfe.warlock3.wrayth.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSAttributedString
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.UIKit.NSDocumentTypeDocumentAttribute
import platform.UIKit.NSHTMLTextDocumentType
import platform.UIKit.create

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
actual fun unescapeXml(text: String): String {
    val nsString = NSString.create(text)
    val data = nsString.dataUsingEncoding(NSUTF8StringEncoding)
    val options = mapOf<Any?, Any>(NSDocumentTypeDocumentAttribute to NSHTMLTextDocumentType!!)
    val attributedString = NSAttributedString.create(data = data!!, options = options, documentAttributes = null, error = null)
    return attributedString!!.string()
}

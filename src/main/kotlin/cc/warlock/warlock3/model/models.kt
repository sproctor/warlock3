package cc.warlock.warlock3.model

import cc.warlock.warlock3.stormfront.SgeConnection
import tornadofx.*

data class Document(var title : String = "", var text: String = "")

class DocumentViewModel(var document: Document = Document()) : ItemViewModel<Document>() {
    val title = bind { document.observable(Document::title) }
    val text = bind { document.observable(Document::text) }

}

class SgeConnectionModel : ItemViewModel<SgeConnection>() {

}
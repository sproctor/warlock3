package cc.warlock.warlock3.app.model

import javafx.beans.property.StringProperty
import tornadofx.*

data class Document(var title : String = "", var text: String = "")

class DocumentViewModel(var document: Document = Document()) : ItemViewModel<Document>() {
    val title = bind { document.observable(Document::title) }
    val text = bind { document.observable(Document::text) }

}

class AccountModel : ItemViewModel<Account>(Account()) {
    val name: StringProperty = bind { item?.nameProperty() }
    val password: StringProperty = bind { item?.passwordProperty() }
}